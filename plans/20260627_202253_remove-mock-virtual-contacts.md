# Remove mock "Virtual Contact Lists" dialog from composer

**Status:** completed

## What this is

The SMS composer's contact-picker button (the `PersonAdd` icon) opens a
**"Select Recipient"** dialog that lists 8 hardcoded fake contacts
("Mom", "Dad", "HDFC Banking Alerts", etc.) plus a "Search System Contacts App"
button that launches the real Android contact picker.

The fake contacts are mock/demo data baked into the Compose code, intended for
emulator testing. The user wants them removed.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## The issue

- The "Virtual Contact Lists" entries at lines ~6187–6196 are static mock data
  (`listOf(Triple(...))`) presented to the user as if they were real contacts.
- They serve no purpose in a real install and are confusing.

## Plan for the fix

1. **Remove the dialog state.** Delete `var showContactListDialog by remember { mutableStateOf(false) }`
   (line ~6068).

2. **Remove the entire dialog block** (the `if (showContactListDialog) { Dialog(...) { ... } }`,
   lines ~6136–6290), which contains the mock contact list and the
   "Search System Contacts App" button.

3. **Rewire the picker button** (`IconButton` at line ~6508) so that instead of
   `onClick = { showContactListDialog = true }`, it directly performs the real
   contact-picker flow that the dialog's "Search System Contacts App" button used:
   - check `READ_CONTACTS` permission;
   - if granted, launch `contactPickerLauncher`;
   - otherwise launch `permissionsLauncher` to request the permission.

   This keeps `contactPickerLauncher` and `permissionsLauncher` (lines ~6070–6134)
   in use, so no other code is orphaned.

## Result / behavior change

- The mock "Select Recipient" dialog is gone.
- Tapping the contact-picker button now goes **straight to the real system contact
  picker** (after a permission prompt if needed).
- Users can still type a sender manually, and existing inbox-derived
  `suggestedSenders` are unaffected.

## Not changed

- No data layer, manifest, or test changes anticipated. (Will check whether any test
  references `composer_contacts_picker_button`'s dialog behavior; the test tag itself
  stays on the button.)
