# Plan: Add a Plan-Status convention to the workflow rules

**Status:** completed

## Issue

The workflow rules in both the global `~/.claude/CLAUDE.md` and this project's `CLAUDE.md`
require writing a plan and a change log, but they say nothing about tracking the lifecycle
state of a plan. There is no convention for marking whether a plan is a draft, awaiting
approval, being implemented, finished, dropped, or only partially done. This makes it hard
to see at a glance where any given plan stands.

## Desired convention

Every plan file must carry a `**Status:**` line near the top (immediately under the title),
set to one of these values, and it must be updated as the plan moves through its lifecycle:

- `draft` — plan is being drafted, not yet presented for approval.
- `approval_pending` — plan presented to the user; waiting for explicit approval.
- `in_progress` — approved; implementation underway.
- `completed` — implementation finished and the change log written.
- `dropped` — plan abandoned / will not be implemented.
- `partial_completion` — some of the plan was implemented, the rest was not.

Lifecycle: `draft` → `approval_pending` → `in_progress` → `completed`.
At any point a plan may instead become `dropped`; a finished-but-incomplete plan becomes
`partial_completion`.

## Files to change

1. `D:\Users\sreerajp\.claude\CLAUDE.md` (global rules) — extend Workflow rule 1 with the
   status convention and the list of allowed status values + lifecycle.
2. `l:\Android\sms-sentry\CLAUDE.md` (this project) — mirror the same addition so the
   project rules stay in sync.

## Plan for the change

- In **rule 1 ("Plan before changing")** of both files, after the existing bullet list of
  required plan contents, add:
  - a new required field: a `**Status:**` line at the top of every plan;
  - the enumerated list of the six allowed status values with their meanings (as above);
  - the lifecycle ordering and when to transition (set `draft` on creation, flip to
    `approval_pending` when presenting, `in_progress` on approval, `completed` when the
    change log is written; use `dropped` / `partial_completion` as terminal states).
- Keep wording identical between the two files (global is the source of truth; project
  mirrors it), consistent with how the two rule sets are already kept in sync.
- No code or behavior changes; documentation-only edits to the two CLAUDE.md files.

## Out of scope

- Back-filling a `Status:` line into the 46 existing historical plans (they are already
  done). Can be done later if desired, but not part of this change.

## After approval

- Implement the two edits.
- Update this plan's `Status:` to `in_progress`, then `completed`.
- Write a change log to `change_log/` referencing this plan.
