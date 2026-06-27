# Change log: Add a Plan-Status convention to the workflow rules

Implements plan [plans/20260627_090509_plan-status-convention.md](../plans/20260627_090509_plan-status-convention.md).

## What changed

Added a Plan-Status convention to Workflow rule 1 ("Plan before changing") in both rule
sources, kept word-for-word identical:

- `D:\Users\sreerajp\.claude\CLAUDE.md` (global rules)
- `l:\Android\sms-sentry\CLAUDE.md` (this project)

The change adds:
- a new required plan field: a `**Status:**` line near the top of every plan;
- the six allowed status values and their meanings: `draft`, `approval_pending`,
  `in_progress`, `completed`, `dropped`, `partial_completion`;
- the normal lifecycle (`draft` → `approval_pending` → `in_progress` → `completed`) and
  when to transition, with `dropped` / `partial_completion` as terminal states.

Documentation-only; no code or behavior changes.

## Not done (out of scope)

- The 46 existing historical plans were not back-filled with a `Status:` line.

## Verification of the convention on this plan

This plan exercised the new convention end-to-end: created as `approval_pending`, moved to
`in_progress` on approval, and set to `completed` alongside this change log.
