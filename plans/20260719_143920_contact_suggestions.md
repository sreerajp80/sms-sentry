# Contact Suggestions in Composer
**Status:** completed

## Files to be Changed
1. `app/src/main/java/in/sreerajp/sms_sentry/util/ContactNameResolver.kt`
   - Define `ContactSuggestion` data class.
   - Implement `queryContacts` helper function using Android's `ContactsContract.CommonDataKinds.Phone` database to find contacts matching a query.
2. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
   - Add contact suggestions StateFlow and backing MutableStateFlow.
   - Implement `searchContactSuggestions` running on `Dispatchers.IO`.
3. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
   - Collect contact suggestions Flow in `ComposeSmsDialog`.
   - Update suggestion lists dynamically based on `senderInput`.
   - Resolve contact names and photos for recent list items when `senderInput` is blank.
   - Render contact suggestions (name, phone number, and avatar image/monogram) when `senderInput` is not blank.

## Issue
Currently, when typing in the "To:" (recipient) field of a new SMS in the composer, the app only matches against recent senders in the inbox or a hardcoded demo list of numbers. It does not query or suggest matching contacts from the Android address book.

## Plan for the Fix
1. **Extend `ContactNameResolver.kt`:**
   - Define `ContactSuggestion` data class:
     ```kotlin
     data class ContactSuggestion(
         val name: String,
         val number: String,
         val photoUri: Uri? = null
     )
     ```
   - Implement `queryContacts(context: Context, query: String): List<ContactSuggestion>`:
     - Check for `READ_CONTACTS` permission.
     - Build a query URI: if query is empty, use `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`; if non-empty, filter using `ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI` with the encoded query.
     - Query columns: `DISPLAY_NAME`, `NUMBER`, and `PHOTO_URI`.
     - Distinct results by phone number.
2. **Expose suggestions in `SmsOrganizerViewModel.kt`:**
   - Define `val contactSuggestions: StateFlow<List<ContactNameResolver.ContactSuggestion>>`.
   - Implement `searchContactSuggestions(query: String)` using a coroutine launched on `Dispatchers.IO` (debounced by cancelling the previous query job) to prevent blocking the main thread.
3. **Connect to Composer UI in `SmsOrganizerUi.kt`:**
   - In `ComposeSmsDialog`, collect `viewModel.contactSuggestions`.
   - Use `LaunchedEffect(senderInput)` to trigger `viewModel.searchContactSuggestions(senderInput)`.
   - Update the LazyColumn display:
     - If `senderInput.isBlank()`, show recent senders from inbox history (matching existing behavior), but resolve names and photos from `viewModel.contactNames` and `viewModel.contactPhotos`.
     - If `senderInput.isNotBlank()`, show matching contact suggestions from `contactSuggestions`, displaying `suggestion.name` as primary text, `suggestion.number` as secondary text, and loading the contact image/monogram using `AvatarTile(..., photoUri = suggestion.photoUri)`.
     - When any suggestion is tapped, set `senderInput = suggestion.number`.

## Verification Plan
### Automated Tests
- Run Gradle unit tests to ensure no regressions:
  `.\gradlew.bat :app:testDebugUnitTest`
### Manual Verification
- Deploy to device/emulator.
- Open the "New message" composer.
- Verify that before typing, recent conversations show matching contact names/photos from address book.
- Verify that typing in the "To:" field queries the address book (by display name or phone number) and lists matching contact suggestions with monograms/photos.
- Tap a contact suggestion and verify it sets the recipient input correctly and either folds into the existing thread or is ready for message sending.
