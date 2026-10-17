# feedback_<slug>.md

> Template for `feedback_*` memories — behavior rules the dev
> corrected/validated.

---

# <Short imperative title>

**When:** <exact trigger — e.g., "when touching an HTML template">
**Do:** <mandatory action — e.g., "run `npm run build:css` before
committing">
**Don't:** <forbidden action or anti-pattern>

## Why

<1–3 lines explaining the reason. Include the rationale: incident that
happened, rework it avoids, risk it covers.>

## How to verify

<Objective check command or criterion. If it became a hook, mention it.>

---

## Filled example

# Always rebuild CSS before committing

**When:** Touching any file under `templates/`.
**Do:** Run `npm run build:css` and add `static/css/app.css` to the
commit.
**Don't:** Commit a template without rebuild — CI fails with
"app.css out of date".

## Why

The runtime container has no Node. CSS is compiled and versioned. The
pipeline has an explicit gate that fails when CSS hash doesn't match
the classes in templates.

## How to verify

`.aidev/scripts/pre_pr_check.py` validates automatically.
