# Plan: Show contact names instead of phone numbers

## Issue

When an SMS sender's phone number is saved in the device address book, the app still shows
the raw phone number everywhere (inbox cards, conversation list, thread header, message
detail). It should resolve the number against `ContactsContract` and show the saved contact
name (falling back to the number when there is no match or no permission).

`READ_CONTACTS` is already declared in the manifest and is already requested ad-hoc inside
the composer, but it is **not** requested at app startup, and there is no number→name
resolution anywhere in the display path.

## Approach

Resolve numbers to names in a small util, cache the results, expose them reactively from the
ViewModel as a `sender → displayName` map, and surface that map to the UI through a
`CompositionLocal` so every existing display point can look up a name with a one-line change.

Non-phone-like senders (alphanumeric sender IDs such as `HDFCBK`, `VM-AXISBK`, short codes)
are skipped — `PhoneLookup` would never match them, so we avoid the wasted query and just
show them as-is.

## Files to change

1. **`app/src/main/java/in/sreerajp/sms_sentry/util/ContactNameResolver.kt`** (new)
   - `object ContactNameResolver` with an in-memory `ConcurrentHashMap<String, String>` cache
     (sender → resolved name; we cache the original sender as the value for "no match" so we
     never re-query a miss).
   - `fun lookup(context, sender): String` — returns the contact display name, or the sender
     unchanged when: permission is missing, the sender isn't phone-number-like, or there is no
     matching contact. Queries `ContactsContract.PhoneLookup.CONTENT_FILTER_URI`.
   - Small private `looksLikePhoneNumber(sender)` guard (digits / `+` / spaces / dashes only).
   - `fun clearCache()` so a later contacts edit / permission grant can force re-resolution.

2. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**
   - Add `private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())` and a
     public `val contactNames: StateFlow<Map<String, String>>`.
   - Add `hasReadContactsPermission()` helper (mirrors existing `hasReadSmsPermission()`).
   - In `init`, collect `allMessages`; for each distinct sender resolve a name on
     `Dispatchers.IO` (cheap after the first pass thanks to the cache) and publish only the
     entries whose resolved name differs from the raw sender into `_contactNames`.
   - Add `fun refreshContactNames()` that clears the resolver cache and re-resolves the current
     senders — called when the READ_CONTACTS grant arrives.

3. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   - Define `val LocalContactNames = compositionLocalOf { emptyMap<String, String>() }`.
   - In `SmsOrganizerApp`: collect `viewModel.contactNames` and wrap the existing content in
     `CompositionLocalProvider(LocalContactNames provides names) { ... }`.
   - Add `android.Manifest.permission.READ_CONTACTS` to the startup
     `permissionsToRequest` list, and in the launcher callback call
     `viewModel.refreshContactNames()` when that grant is `true`.
   - Add a tiny helper `displayNameFor(sender)` reading `LocalContactNames.current` (returns
     name or sender), and update the 5 display points to use it for **both** the name `Text`
     and the `AvatarTile` initial:
     - inbox compact card — text ~`L1583`, avatar ~`L1578`
     - `ConversationCard` — text ~`L1733`, avatar ~`L1719`
     - `ThreadScreen` header — text ~`L1937`, avatar ~`L1928`
     - message card — text ~`L2569`, avatar ~`L2555`
     - `MessageDetailScreen` header — text ~`L2691`, avatar ~`L2682`

No data-model / Room / DAO changes — names are resolved at display time, not stored.

## Notes / trade-offs

- Display-time resolution (not persisted) keeps the offline-classification data model
  untouched and automatically reflects later address-book edits after a `refreshContactNames()`.
- If READ_CONTACTS is denied, behavior is unchanged (numbers shown) — no regression.
- Sent messages key on the recipient number, so contact names will also show for outgoing
  threads, which is the desired behavior.
