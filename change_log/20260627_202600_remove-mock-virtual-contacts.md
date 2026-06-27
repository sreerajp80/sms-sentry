# Change log: Remove mock "Virtual Contact Lists" dialog

Implements [plans/20260627_202253_remove-mock-virtual-contacts.md](../plans/20260627_202253_remove-mock-virtual-contacts.md).

## What changed

File: `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

1. **Removed** the `showContactListDialog` state variable.
2. **Removed** the entire "Virtual Preset Contacts Dialog for Sandboxed Simulator"
   block — the `if (showContactListDialog) { Dialog(...) { ... } }` (~155 lines).
   This contained the 8 hardcoded mock contacts ("Mom", "Dad", "Alex Smith",
   "Office Workspace", "HDFC Banking Alerts", "SBI Mobile Finance",
   "Amazon Post Services", "Google verification") and the embedded
   "Search System Contacts App" button.
3. **Rewired** the composer's contact-picker `IconButton` (`PersonAdd`,
   `testTag = "composer_contacts_picker_button"`). Its `onClick` no longer opens
   the mock dialog; it now performs the real contact-picker flow directly:
   - checks `READ_CONTACTS` permission;
   - if granted, launches `contactPickerLauncher` (the system contact picker);
   - otherwise launches `permissionsLauncher` to request the permission.

## Behavior change

- The mock "Select Recipient" dialog is gone.
- Tapping the contact-picker button now goes straight to the real Android system
  contact picker (with a permission prompt if needed).
- Manual sender entry and inbox-derived `suggestedSenders` are unchanged.

## Verification

- `contactPickerLauncher` and `permissionsLauncher` remain in use; no orphaned code.
- No tests referenced the dialog, the mock contacts, or the picker button's old behavior.
- `./gradlew :app:compileDebugKotlin` completes with no Kotlin errors.
