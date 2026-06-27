# Change log: Back-fill the `Status:` line into historical plans

Implements plan [plans/20260627_090750_backfill-plan-status.md](../plans/20260627_090750_backfill-plan-status.md).

## What changed

Inserted a `**Status:**` line (immediately under the H1 title) into the 45 plan files that
predated the Plan-Status convention
([plans/20260627_090509_plan-status-convention.md](../plans/20260627_090509_plan-status-convention.md)).

Status was assigned by cross-referencing each plan against `change_log/` and the current code:

- **`completed` — 43 plans.** Each has a matching change log, or (for
  `20260616_074132_sender-actions-to-thread`, which has no own change log) was verified present
  in code via `thread_menu_button` / `reportSpamSender`.
- **`partial_completion` — 1 plan.** `20260627_082654_move-sandbox-and-split-settings-pages`:
  its change log records parts B–E done; parts A & F (move the sandbox into a Testing sub-page,
  delete `SimulateSmsDialog`) are not done, and the top-bar `simulate_sms_bar_button` still
  exists in code.
- **`dropped` — 1 plan.** `20260627_081453_notification-actions-not-working`: its core change
  (remove `ACTION_OPEN_APP`, make "Open" a direct `getActivity`) was never applied
  (`ACTION_OPEN_APP` still present in the receiver, `SmsNotificationHelper`, and manifest);
  superseded by `20260627_082017_notification-open-back-to-inbox`.

Plans-only change — no code, docs, or change-log content was modified.

## Verification

`grep` confirms all 47 plan files now carry exactly one `**Status:**` line: 1 `dropped`,
1 `partial_completion`, and the remainder `completed` (plus this back-fill plan, set to
`completed` alongside this log).
