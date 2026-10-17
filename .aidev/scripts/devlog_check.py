#!/usr/bin/env python3
"""Stop hook — garante que o devlog do dia seja escrito.

Por quê: dois desenvolvedores tocam este projeto via IA, e a memória da IA
é privada por conta. O contexto compartilhado vive em `.aidev/devlog/`
(um arquivo por dev, por dia). Depender de o agente lembrar a regra no fim
da sessão provou-se frágil — este hook torna isso determinístico.

Quando dispara: houve commit **hoje** do dev atual e ainda não existe o
devlog do dia. Aí bloqueia o encerramento do turno e pede a entrada.

Segurança:
- **Sem loop:** só avisa uma vez por sessão/dia (marcador dentro de .git/).
  Se o agente não obedecer, o turno seguinte encerra normalmente.
- **Fail-open:** qualquer erro inesperado → exit 0 (nunca travar o dev).

Contrato do Stop hook (Claude Code):
- stdin: JSON com `session_id`, `cwd`, ...
- exit 0 + stdout `{"decision": "block", "reason": ...}` → mantém o turno
  vivo para o agente agir. (Não usar exit 2 junto com JSON.)
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import unicodedata
from datetime import date
from pathlib import Path

GIT_TIMEOUT_S = 10


def git(cwd: Path, *args: str) -> str:
    """Roda um comando git. Retorna stdout sem espaços, ou '' se falhar."""
    try:
        proc = subprocess.run(
            ["git", *args],
            cwd=cwd,
            capture_output=True,
            text=True,
            timeout=GIT_TIMEOUT_S,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return ""
    return proc.stdout.strip() if proc.returncode == 0 else ""


def slugify(value: str) -> str:
    """Normaliza para [a-z0-9] (remove acentos e separadores)."""
    ascii_value = (
        unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    )
    return re.sub(r"[^a-z0-9]", "", ascii_value.lower())


def dev_slug(root: Path) -> str:
    """Identificador do dev no nome do arquivo.

    `git config devlog.slug` tem prioridade (permite 'ryan' em vez de
    'ryanpsouza'); senão usa o primeiro nome de `user.name`.
    """
    override = git(root, "config", "devlog.slug")
    if override:
        return slugify(override)
    name = git(root, "config", "user.name")
    first_name = name.split()[0] if name else ""
    return slugify(first_name) or "unknown"


def committed_today(root: Path) -> bool:
    """True se o dev atual tem commit hoje em qualquer branch local."""
    email = git(root, "config", "user.email")
    if not email:
        return False
    log = git(
        root,
        "log",
        "--branches",
        f"--author={email}",
        "--since=midnight",
        "--pretty=%h",
    )
    return bool(log)


def nudge_marker(root: Path, session_id: str, today: str) -> Path | None:
    """Marcador de 'já avisei' — dentro de .git/, fora do versionamento."""
    git_dir = git(root, "rev-parse", "--git-dir")
    if not git_dir:
        return None
    base = Path(git_dir)
    if not base.is_absolute():
        base = root / base
    return base / "devlog-nudge" / f"{slugify(session_id) or 'nosession'}-{today}"


def block_reason(entry: Path, root: Path) -> str:
    rel = entry.relative_to(root)
    return (
        f"Houve commit hoje, mas falta o devlog: `{rel}` não existe.\n\n"
        "Antes de encerrar, crie esse arquivo seguindo o template de "
        "`.aidev/devlog/README.md` (Feito / Estado atual / Próximo e "
        "pendências / Decisões & gotchas / Bloqueios).\n\n"
        "Curto e factual — aponte o PR/arquivos em vez de repetir o "
        "docs. Isso é o que o outro dev vai ler para saber onde você "
        "parou."
    )


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        payload = {}

    cwd = Path(payload.get("cwd") or Path.cwd())
    toplevel = git(cwd, "rev-parse", "--show-toplevel")
    if not toplevel:
        return 0  # fora de um repo git: nada a cobrar
    root = Path(toplevel)

    if not committed_today(root):
        return 0  # nenhum trabalho commitado hoje: nada a registrar

    today = date.today().isoformat()
    entry = root / ".aidev" / "devlog" / f"{today}-{dev_slug(root)}.md"
    if entry.exists():
        return 0  # devlog do dia já existe

    marker = nudge_marker(root, str(payload.get("session_id", "")), today)
    if marker is None or marker.exists():
        return 0  # já avisei nesta sessão hoje — nunca insistir (anti-loop)
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.touch()

    print(json.dumps({"decision": "block", "reason": block_reason(entry, root)}))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:  # noqa: BLE001 — fail-open: hook nunca trava a sessão
        sys.exit(0)
