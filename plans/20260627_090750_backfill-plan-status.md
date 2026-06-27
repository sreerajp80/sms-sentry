# Plan: Back-fill the `Status:` line into the 45 existing historical plans

**Status:** completed

## Issue

The new Plan-Status convention (see
[plans/20260627_090509_plan-status-convention.md](20260627_090509_plan-status-convention.md))
requires every plan to carry a `**Status:**` line near the top. The 45 plans written before
the convention have no such line. This back-fill adds an accurate `Status:` line to each.

## Approach

For each historical plan, insert `**Status:** <value>` as a new line immediately under the
H1 title (followed by a blank line). The value is determined by cross-referencing each plan
against `change_log/` and the current code:

- A plan with a matching change log (and/or whose changes are present in the code) →
  `completed`.
- A plan superseded / not implemented → `dropped`.
- A plan only partly implemented → `partial_completion`.

## Status assignments

**`completed` (43 plans)** — each has a matching change log (or, for `sender-actions-to-thread`,
verified present in code via `thread_menu_button` / `reportSpamSender`):

- 20260613_094208_gradle-wrapper-9.3.1
- 20260613_095029_rename-namespace
- 20260613_100221_agp-downgrade
- 20260613_100722_enable-androidx
- 20260613_104310_signing-keystore-fix
- 20260613_111819_add-kotlin-android-plugin
- 20260613_113500_align-jvm-target-17
- 20260613_123806_dashboard-theme-palette
- 20260613_214530_dashboard-layout-rebuild
- 20260614_111725_inbox-detail-redesign
- 20260614_121226_read-unread-status
- 20260614_122704_compact-card-unread-border
- 20260614_132308_category-color-cards
- 20260614_204042_fix-sync-button-wrap
- 20260614_215447_settings-redesign-and-about
- 20260614_223309_default-sms-app
- 20260616_073352_fix-respond-via-message-constant
- 20260616_074132_sender-actions-to-thread  *(no own change log; verified in code)*
- 20260616_075000_thread-multiselect-and-actions
- 20260617_112513_default-sms-app-status-detection
- 20260617_214643_thread-status-bar-inset
- 20260617_215753_blocked-card-swipe-bleed-through
- 20260617_221034_inbox-longpress-select-contextual-menu
- 20260618_082616_not-spam-action
- 20260618_083357_move-conversation-to-category
- 20260618_083830_scheduled-sms-delivery
- 20260618_093742_accounts-and-services-categories
- 20260618_100208_four-category-consolidation
- 20260618_105150_conversation-date-grouping-and-thread-scroll
- 20260618_192143_finance-calc-duplicates-and-amount
- 20260618_200211_inbox-search
- 20260620_203703_finance-contribution-drilldown
- 20260620_204909_dashboard-balance-mismatch
- 20260620_211440_remove-demo-seeding
- 20260620_212835_search-result-longpress-select
- 20260621_002230_coupon-and-payment-misclassification
- 20260621_002823_dashboard-balance-relock
- 20260621_003157_all-pill-colour
- 20260621_003529_notification-tap-open-message
- 20260621_004254_contact-name-display
- 20260627_082017_notification-open-back-to-inbox
- 20260627_083628_read-state-controls-and-menu-dedup
- 20260627_085313_remove-firebase

**`partial_completion` (1 plan):**

- 20260627_082654_move-sandbox-and-split-settings-pages — change log records parts B–E done,
  parts A & F (move sandbox into a Testing sub-page, delete `SimulateSmsDialog`) not done; the
  top-bar `simulate_sms_bar_button` still exists in code.

**`dropped` (1 plan):**

- 20260627_081453_notification-actions-not-working — its core change (remove `ACTION_OPEN_APP`,
  make "Open" a direct `getActivity`) was never applied (`ACTION_OPEN_APP` still present in the
  receiver, `SmsNotificationHelper`, and manifest); superseded by
  `20260627_082017_notification-open-back-to-inbox`.

Total: 43 + 1 + 1 = **45 plans** back-filled. (This back-fill plan and the convention plan
already have their own `Status:` lines and are not modified.)

## Files to change

The 45 historical plan files listed above (insert one `**Status:**` line each). No code,
docs, or change-log content is modified.

## After approval

- Insert the `Status:` lines.
- Flip this plan's `Status:` to `in_progress`, then `completed`.
- Write a change log to `change_log/` referencing this plan.
