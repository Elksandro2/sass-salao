# feedback_dep_<lib>_v<major>.md

> Template for memories that capture **new syntax / breaking-change
> patterns** introduced by a major version of an external dependency.
> Special case of `feedback_*` — created whenever we adopt a major
> version of a lib.

---

# `<lib>` v<major> — new idiom

**Adopted on:** YYYY-MM-DD
**Migration guide:** <URL>
**Project's old version:** <X.Y.Z>
**Project's new version:** <X+1.0.0>

## Why this memory exists

When working on this codebase, the AI must use the **new** v<major>
idiom from day one. Old patterns may still appear in older code we
haven't migrated yet, but **never write new code in the old style**.

## Old → New syntax mapping

> Concrete patterns from this project. One row per pattern.

| Concept | Old (v<old>) | New (v<new>) |
|---|---|---|
| <thing> | `old_call(x)` | `new_call(x)` |
| <thing> | `Foo.method()` | `Foo.new_method()` |

## Common pitfalls

- <Pitfall 1: subtle behavioral change that the migration guide warns
  about. Cite the section.>
- <Pitfall 2: type signature change that breaks at runtime, not
  compile time.>
- <Pitfall 3: default value changed.>

## Files still on old syntax

> Track migration progress. Remove rows as files are updated.

- [ ] `src/foo.py` — uses `old_call`
- [ ] `src/bar.py` — uses `Foo.method`
- [x] `src/baz.py` — migrated

## Lint/SAST rules to forbid old syntax

> If we have a Semgrep / ESLint / etc. rule that catches the old
> pattern, link it here. Otherwise, write one.

- `.semgrep/forbid-old-<lib>.yml` — catches `old_call` usage
- ...

## Revisit trigger

When this memory should be deleted: when **all files** are migrated
**and** lint rules forbidding old syntax are in place. At that point
the codebase enforces the new idiom; the memory is redundant.
