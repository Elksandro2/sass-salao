# Devlog: continuidade entre sessões

Registro curto do que foi feito e do que falta, para a próxima sessão (de IA ou de pessoa) saber onde parou. Não substitui o git log nem os ADRs: aqui mora o **estado e a intenção**.

## Convenção

- Um arquivo por dev por dia: `AAAA-MM-DD-<dev>.md` (o `<dev>` vem do primeiro nome de `git config user.name`; para fixar outro: `git config devlog.slug elksandro`).
- Curto e factual; aponte commits/arquivos em vez de copiar conteúdo.

## Uso pela IA

- **Início da sessão**: ler os 2–3 mais recentes.
- **Fim da sessão** que mexeu em código: escrever o do dia.
- Rede de segurança: hook `Stop` (`.aidev/scripts/devlog_check.py`) cobra uma vez por dia se houver commit sem devlog. Não faz loop e falha em silêncio.

## Template

```markdown
# AAAA-MM-DD — <dev>

**Feito:** (com hashes/arquivos)
**Estado atual:** 
**Próximo / pendências:** 
**Decisões & gotchas:** 
**Bloqueios:** 
```
