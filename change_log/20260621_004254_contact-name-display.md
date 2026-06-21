# Change log: Show contact names instead of phone numbers

Implements [plans/20260621_004254_contact-name-display.md](../plans/20260621_004254_contact-name-display.md).

## What changed

SMS senders saved in the device address book now display as their saved contact name
everywhere a sender is shown, falling back to the raw phone number when there is no match
or no permission. Names are resolved at display time (nothing persisted), so the offline
data model / Room schema is unchanged.

### New file
- **`app/src/main/java/in/sreerajp/sms_sentry/util/ContactNameResolver.kt`**
  - `object ContactNameResolver` resolving a sender number → display name via
    `ContactsContract.PhoneLookup`, with an in-memory `ConcurrentHashMap` cache (a miss caches
    the original sender so it is never re-queried).
  - Skips alphanumeric / short-code sender IDs (`looksLikePhoneNumber` guard) and returns the
    sender unchanged when `READ_CONTACTS` is not granted.
  - `clearCache()` to force re-resolution after a permission grant.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- Added `contactNames: StateFlow<Map<String, String>>` (sender → name; only matches included).
- Added `hasReadContactsPermission()` and a private `resolveContactNames()` that resolves
  distinct senders on `Dispatchers.IO`.
- `init` now collects `allMessages` and resolves names as messages arrive.
- Added `refreshContactNames()` (clears the resolver cache and re-resolves) for use after the
  READ_CONTACTS grant.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Added `LocalContactNames` `CompositionLocal` and a `displayNameFor(sender)` helper.
- `SmsOrganizerApp` collects `viewModel.contactNames` and wraps its content in
  `CompositionLocalProvider(LocalContactNames provides …)`.
- Added `READ_CONTACTS` to the startup permission request, and the launcher callback now calls
  `viewModel.refreshContactNames()` when that grant arrives.
- Updated the 5 sender display points to use the resolved name for both the name text and the
  avatar initial: inbox compact card, `ConversationCard`, `ThreadScreen` header, message card,
  and `MessageDetailScreen` header.

## Verification
- `./gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL (only pre-existing deprecation
  warnings; no new errors/warnings from these changes).
