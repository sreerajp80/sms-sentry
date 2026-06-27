# Change log: Contacts & senders — photo avatars, add-to-contacts, sender info screen

Implements [plans/20260627_144811_contacts-and-senders.md](../plans/20260627_144811_contacts-and-senders.md).

## What changed

### 1. Contact photos in avatars
- **`util/ContactNameResolver.kt`** — reworked around a cached `ContactInfo(name, photoUri)`. A
  single `PhoneLookup` query now projects both `DISPLAY_NAME` and `PHOTO_URI`. New public API:
  `resolve()`, `photoUri()` (alongside the existing `lookup()`), and the formerly-private
  `looksLikePhoneNumber` is exposed as `isPhoneNumberLike()`.
- **`SmsOrganizerViewModel.kt`** — added `contactPhotos: StateFlow<Map<String, Uri>>` populated by
  `resolveContactNames()` (now calls `ContactNameResolver.resolve()` once and splits the result
  into the names and photos maps). Same refresh/permission-grant path as names.
- **`SmsOrganizerUi.kt`** — new `LocalContactPhotos` composition local + `photoUriFor()` helper,
  provided at the app root next to `LocalContactNames`. `AvatarTile` gained an optional
  `photoUri` param: when present it renders the contact photo via Coil `AsyncImage` (cropped to the
  tile shape), otherwise the existing category-colored monogram (unchanged, per the approved
  decision to keep category coloring). All four call sites (conversation card, message card, thread
  header, message-detail header) now pass the resolved photo.
- **`app/build.gradle.kts`** — enabled the already-cataloged `libs.coil.compose` dependency
  (was commented out).

### 2. Add unknown sender to contacts
- **`SmsOrganizerViewModel.kt`** — `isUnknownContact(sender)` (phone-number-like with no resolved
  contact) and `addToContacts(sender)`, which fires `ACTION_INSERT_OR_EDIT` to the system Contacts
  app pre-filled with the number. No `WRITE_CONTACTS`; falls back to a toast if no contacts app.
- **`SmsOrganizerUi.kt`** — thread overflow menu gains an **"Add to contacts"** item, shown only
  when `isUnknownContact(sender)`.

### 3. Per-sender info / reputation screen
- **`SmsOrganizerViewModel.kt`** — `openedSenderInfo` nav state + `openSenderInfo()` /
  `closeSenderInfo()` (mirrors the message-detail overlay pattern).
- **`SmsOrganizerUi.kt`** — new `SenderInfoScreen` (plus `SenderStatusChip` / `SenderInfoRow` /
  `SenderActionButton` helpers): large avatar, name/number, Muted/Blocked/Spam status chips, a
  stats card (total / received / sent / first-seen / last-seen), a per-category breakdown, and
  quick actions (Add to contacts, Mute toggle, Report spam, Block). All stats are derived from
  `allMessages` filtered by sender — no new Room entity. Reached via a new **"Sender info"** thread
  overflow item; routed as a full-screen overlay in the top-level screen switch with a `BackHandler`.

## Verification
- `./gradlew :app:assembleDebug` — success (`app-debug.apk` produced).
- `./gradlew :app:testDebugUnitTest` — all existing Robolectric/Roborazzi tests pass.
