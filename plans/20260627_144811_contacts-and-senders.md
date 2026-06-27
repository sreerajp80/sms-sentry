# Contacts & senders: photo/monogram avatars, add-to-contacts, sender info screen

**Status:** completed

> Scope note: three related "who is this sender" features. Independent of the composer/messaging
> UX plan ([20260627_131311_composer-and-messaging-ux.md](20260627_131311_composer-and-messaging-ux.md))
> and the dual-SIM routing plan. Grouped because all three build on `ContactNameResolver` /
> `LocalContactNames` and the per-sender thread. Each part below can be approved / implemented /
> logged on its own if you'd rather stage them.

## What the issue is

1. **No contact photos or colored monogram avatars.** `AvatarTile`
   ([SmsOrganizerUi.kt:2515](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2515))
   only ever draws a single-letter monogram tinted by **category** color (so two different banks
   look identical), with no per-sender color and no profile photo — even when the sender is a
   saved contact with a picture. `ContactNameResolver`
   ([util/ContactNameResolver.kt](../app/src/main/java/in/sreerajp/sms_sentry/util/ContactNameResolver.kt))
   resolves the *name* but never fetches `PHOTO_URI`.

2. **No "add unknown sender to contacts" shortcut.** From a thread with an unsaved number there's
   no way to create/link a contact — the thread overflow menu
   ([SmsOrganizerUi.kt:2051](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2051))
   has Mute / Select / Block / Report / Delete only. The user must copy the number and switch apps.

3. **No per-sender info / reputation view.** Spam is reported per-sender
   (`reportSpamSender`, [SmsOrganizerViewModel.kt:516](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt#L516))
   and senders can be blocked, but there's no aggregated screen showing a sender's message count,
   first-seen date, category breakdown, or current block/spam status.

## Design decisions

- **Avatars stay name/photo-driven, fall back to monogram.** Extend `ContactNameResolver` to also
  resolve a `photoUri` (it already does the `PhoneLookup` query + permission check + cache — add
  `PhoneLookup.PHOTO_URI` to the projection). Expose a parallel `LocalContactPhotos` composition
  local (mirrors the existing `LocalContactNames` at
  [SmsOrganizerUi.kt:96](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L96)),
  populated by the ViewModel alongside `contactNames`. `AvatarTile` gains an optional
  `photoUri: Uri?`; when present it renders the photo (via Coil `AsyncImage`, already a transitive
  dep — confirm in `libs.versions.toml`, else use a tiny `ImageDecoder`/`Bitmap` loader to avoid a
  new dependency), otherwise the monogram.
- **Monogram keeps its current category-color fill** (per your call) when there's no contact photo —
  no per-sender coloring. The only avatar change is the photo overlay; the monogram path is
  unchanged.
- **Add-to-contacts uses the system intent, no new permission.** Fire
  `Intent(ContactsContract.Intents.Insert.ACTION)` (or `ACTION_INSERT_OR_EDIT`) pre-filled with the
  phone number and hand off to the Contacts app. This needs **no** `WRITE_CONTACTS` and works
  whether or not we're the default SMS app. Only shown when the sender is a phone-number-like,
  currently-unresolved sender (reuse `ContactNameResolver.looksLikePhoneNumber` logic; expose it or
  a small `isUnknownContact(sender)` helper).
- **Sender info is a new full-screen view, navigated like the message detail.** Add a nullable
  `openedSenderInfo: String?` state + `openSenderInfo(sender)` / `closeSenderInfo()` on the
  ViewModel (mirrors `openedMessage`/`openMessage` at
  [SmsOrganizerViewModel.kt:425](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt#L425)),
  and a `SenderInfoScreen` composable that derives its stats from `allMessages` filtered by sender
  (count, first/last seen, per-category counts, muted/blocked/spam flags). No new Room entity —
  it's a derived view, consistent with how Accounts/Reminders work.

## Files to change

1. **`util/ContactNameResolver.kt`** — add `photoUri(context, sender): Uri?` (or fold name+photo
   into a single `ContactInfo` result so one query serves both), adding `PHOTO_URI` to the
   projection; keep the existing cache/permission/`looksLikePhoneNumber` logic. Expose
   `isPhoneNumberLike(sender)` (rename/surface the private predicate) for the add-to-contacts gate.

2. **`ui/SmsOrganizerViewModel.kt`**:
   - `contactPhotos: StateFlow<Map<String, Uri>>` populated next to `contactNames` (same refresh
     trigger / permission-grant path).
   - `openedSenderInfo` state + `openSenderInfo(sender)` / `closeSenderInfo()`.
   - `senderStats(sender): SenderStats` (or a small derived data class computed in the screen from
     `allMessages` — keep it where the existing per-sender filters live).

3. **`ui/SmsOrganizerUi.kt`**:
   - `AvatarTile`: add optional `photoUri`; render the photo when present, otherwise the existing
     category-colored monogram (unchanged). Update its call sites
     (ConversationCard [L1733](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L1733),
     ThreadScreen header [L2015](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2015),
     and the others at 1590 / 2677) to pass the resolved photo.
   - Add `LocalContactPhotos` composition local + provide it from `SmsOrganizerApp` like
     `LocalContactNames`.
   - Thread overflow menu: add **"Add to contacts"** (only when `isUnknownContact(sender)`) firing
     the insert intent, and **"Sender info"** → `viewModel.openSenderInfo(sender)`.
   - New `SenderInfoScreen(viewModel, sender)`: big avatar + name/number, message count, first/last
     seen, per-category breakdown chips, current status (Muted / Blocked / Reported spam), and
     quick actions (Add to contacts, Mute toggle, Block, Report spam) reusing existing VM methods.
   - Route `openedSenderInfo` in the top-level screen switch (next to `openedMessage` /
     `openedThread`) with a `BackHandler` to `closeSenderInfo()`.

4. **`gradle/libs.versions.toml`** *(only if Coil is needed and not already present)* — add Coil
   Compose for photo loading; skip if a bitmap loader suffices.

## Out of scope (note, don't implement now)

- Writing contacts in-app (we hand off to the system Contacts app; no `WRITE_CONTACTS`).
- Cross-device/global spam reputation or any network lookup — reputation is purely local
  aggregation over this device's `allMessages`.
- Multi-number contacts / merging several senders that map to one contact into a single thread.
- A senders-list/directory tab (the info screen is reached per-thread, not as a global browser).

## Verification

- A saved contact with a photo shows the photo in the inbox card and thread header; senders
  without a photo show the existing category-colored monogram (unchanged).
- Open a thread from an unsaved number → overflow shows "Add to contacts" → tapping opens the
  system Contacts insert screen pre-filled with the number. A saved contact hides that item.
- Overflow "Sender info" opens the screen; counts, first/last-seen, category breakdown and
  Muted/Blocked/Spam status match the conversation; quick actions work and Back returns to the
  thread.
- Revoking/granting READ_CONTACTS and `clearCache()` refreshes names *and* photos.
- Existing Robolectric/Roborazzi tests still pass (add a screenshot test for `SenderInfoScreen`
  if the suite covers screens).
