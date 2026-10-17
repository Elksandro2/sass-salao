# debt_<slug>.md

> Template for cataloged technical debts. Subset of `project_*`.

---

# Debt: <short title>

**Severity:** <low | medium | high>
**Estimated cost to pay:** <hours/days>
**Cost of not paying:** <impact if ignored>

## The problem

<2–4 lines: what is wrong/incomplete/temporary today. Include relevant
file paths.>

## Why it hasn't been done

<Objective reason: external blocker, cost-benefit imbalance, conscious
trade-off, lack of real usage to justify it yet.>

## Planned solution

<Outline of the solution. Doesn't need to be detailed, but enough for
whoever resumes it to understand the direction.>

## Revisit trigger

<Objective condition that triggers paying the debt:
- "When company reaches 200 users" (only matters at scale)
- "When lib X relaxes pin on Y"
- "After release 2.0"
- "Date >= 2026-05-01" (deterministic deadline)
>

## Current workaround

<What is being done today to live with the debt. Important so the AI
doesn't confuse the workaround with the definitive solution.>
