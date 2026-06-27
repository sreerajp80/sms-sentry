# Explicit permission requests + a Permissions page in Settings

**Status:** in_progress

## What the user asked for

1. Ensure the app **explicitly requests at runtime** every permission it actually needs
   (the "dangerous" / runtime-prompt permissions). Implicit (normal / auto-granted)
   permissions need no prompt — that's fine.
2. Create a **Permissions page** that shows *all* permissions the app uses — both the
   explicit (runtime) ones and the implicit (auto-granted) ones — with a plain-English
   reason for each and live grant status.
3. Make that page reachable from the **Settings** section.

## Current state (findings)

- Startup runtime request in [SmsOrganizerUi.kt:148-160](app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L148-L160)
  asks for: `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`, `READ_CONTACTS`, `READ_PHONE_STATE`,
  and `POST_NOTIFICATIONS` (API 33+).
- The manifest ([AndroidManifest.xml](app/src/main/AndroidManifest.xml)) also declares two
  **dangerous** SMS-group permissions that are **never requested explicitly**:
  `RECEIVE_MMS` and `RECEIVE_WAP_PUSH`. (They're usually auto-granted with the SMS group,
  but the user wants them requested explicitly.)
- Special access permission `SCHEDULE_EXACT_ALARM` is handled separately via a system
  settings intent (`buildExactAlarmSettingsIntent()` / `canScheduleExactAlarms()` in the
  ViewModel) — correct; it cannot use the normal runtime prompt.
- Normal / auto-granted (implicit) permissions: `INTERNET`, `ACCESS_NETWORK_STATE`,
  `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `RECEIVE_BOOT_COMPLETED`.
- Settings already has a sub-page navigation pattern
  ([SettingsScreen, line 4339](app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L4339))
  and reusable helpers: `SettingsNavRow`, `SubPageHeader`, `IntegrationStatusRow`,
  `goodColor()`/`goodSoftColor()`.

## The issue

- Two dangerous permissions (`RECEIVE_MMS`, `RECEIVE_WAP_PUSH`) are declared but never
  explicitly requested, so the app is relying on implicit group-grant behavior.
- There is no single place that shows the user the full permission inventory and why each
  is needed; permission status is scattered (System integration card shows only SMS +
  notifications).

## Plan

### Files to change
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (only file)

No manifest change is required — every permission discussed is already declared. No
ViewModel change is required — `canScheduleExactAlarms()` / `buildExactAlarmSettingsIntent()`
already exist.

### Change 1 — request all dangerous permissions explicitly at startup
In the startup `LaunchedEffect` permission list ([line 149-158](app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L149-L158)),
add `RECEIVE_MMS` and `RECEIVE_WAP_PUSH` to `permissionsToRequest` so the runtime prompt
covers the complete set of dangerous permissions the app declares.

### Change 2 — add a "Permissions" sub-page to Settings
1. Add a nav row in `SettingsMainPage` (icon `Icons.Default.Lock` or `Shield`,
   title "Permissions", subtitle "What SMS Sentry can access & why") wired to a new
   `onOpenPermissions` callback.
2. In `SettingsScreen`, add a `"permissions"` branch that renders a new
   `PermissionsSettingsPage(viewModel, onBack = …)` composable (BackHandler already covered
   by the existing `subPage != null` guard).
3. Implement `PermissionsSettingsPage` as a `LazyColumn` with `SubPageHeader("Permissions", onBack)`
   and three grouped sections, each driven by a small static list of
   `(permission/label, plain-English reason)`:

   - **Requested at runtime (explicit):** `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`,
     `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `READ_CONTACTS`, `READ_PHONE_STATE`,
     `POST_NOTIFICATIONS` (POST_NOTIFICATIONS row only shown on API 33+; on older it is
     auto-granted). Each row shows a live granted/denied pill (via
     `ContextCompat.checkSelfPermission`). A "Grant permissions" button launches a
     `RequestMultiplePermissions` for any not-yet-granted ones, and an "Open app info"
     button (reusing the `ACTION_APPLICATION_DETAILS_SETTINGS` intent already used in
     `SystemIntegrationCard`) for ones the user previously denied permanently.
   - **Special access:** `SCHEDULE_EXACT_ALARM` — status from
     `viewModel.canScheduleExactAlarms()`, with an "Open settings" button using
     `viewModel.buildExactAlarmSettingsIntent()` (API 31+; auto-granted below 31).
   - **Granted automatically (implicit):** `INTERNET`, `ACCESS_NETWORK_STATE`,
     `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `RECEIVE_BOOT_COMPLETED` — listed with their
     reason and a static "Auto" pill (no button; cannot be revoked individually).

4. Reuse existing styling helpers (`Card`, `IntegrationStatusRow`-style rows,
   `goodColor()`/`goodSoftColor()`, `RoundedCornerShape`) so the page matches the rest of
   Settings. Recompute grant state on `ON_RESUME` (same `DisposableEffect` pattern as
   `DefaultSmsAppCard`) so returning from system settings refreshes the pills.

### Reasons text (plain English) to show per permission
- RECEIVE_SMS — "Receive incoming text messages as they arrive."
- READ_SMS — "Read existing messages on your phone to organize them."
- SEND_SMS — "Send replies and scheduled messages."
- RECEIVE_MMS — "Receive picture/group (MMS) messages."
- RECEIVE_WAP_PUSH — "Detect incoming MMS notifications from the carrier."
- READ_CONTACTS — "Show sender names instead of raw numbers."
- READ_PHONE_STATE — "Detect active SIMs for correct dual-SIM sending."
- POST_NOTIFICATIONS — "Show new-message, OTP and reminder notifications."
- SCHEDULE_EXACT_ALARM — "Deliver scheduled SMS and reminders at the exact time."
- INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / CHANGE_WIFI_STATE — "Local
  peer-to-peer sync over Wi-Fi (no data leaves your network)."
- RECEIVE_BOOT_COMPLETED — "Re-arm scheduled messages and reminders after a reboot."

## Out of scope / notes
- No new manifest permissions are added; the app's access surface does not grow.
- Signature-level permissions enforced on receivers (`BROADCAST_SMS`,
  `BROADCAST_WAP_PUSH`, `SEND_RESPOND_VIA_MESSAGE`) are *enforced on callers*, not held by
  this app, so they are intentionally not listed as app permissions.

## Verification
- Build the app (`docs/build-and-test.md` — no Gradle wrapper).
- Manually: Settings ▸ Permissions shows all three sections; pills reflect real status;
  Grant button prompts for the runtime set including MMS/WAP push; exact-alarm button opens
  system settings; returning refreshes pills.
