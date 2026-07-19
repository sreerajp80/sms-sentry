# Change Log - Contact Suggestions in Composer

This change log documents the changes made to implement contacts address-book autocomplete when typing in the "To:" field of the composer.

## Plan Referenced
- Plan: [20260719_143920_contact_suggestions.md](file:///l:/Android/sms-sentry/plans/20260719_143920_contact_suggestions.md)

## Changes Implemented

1. **Extended `ContactNameResolver.kt`:**
   - Defined [ContactSuggestion](file:///l:/Android/sms-sentry/app/src/main/java/in/sreerajp/sms_sentry/util/ContactNameResolver.kt) data class to represent contacts in suggestions with name, number, and photo.
   - Added `queryContacts(context, query)` to query `ContactsContract.CommonDataKinds.Phone` database dynamically, filtering matches by display name or number utilizing Android's smart `CONTENT_FILTER_URI` autocomplete lookup.

2. **Updated `SmsOrganizerViewModel.kt`:**
   - Exposed `contactSuggestions` Flow of matching contact suggestion results.
   - Implemented asynchronous thread-safe `searchContactSuggestions(query)` query execution on `Dispatchers.IO` with auto-canceling previous jobs.

3. **Modified Composer UI in `SmsOrganizerUi.kt`:**
   - Collected `contactSuggestions` flow state inside the `ComposeSmsDialog` view.
   - Linked suggestions query executing asynchronously using `LaunchedEffect(senderInput)`.
   - Redesigned suggestions area to display address book contact matches with name, phone, and profile pictures (using `AvatarTile` with `photoUri`) when typing.
   - Integrated contact name/photo resolving for items in the recent fallback senders list shown when the search input is empty.
   - Wired selection clicks to fill the composer's `senderInput` field.

4. **Added Robolectric Test Coverage:**
   - Created [ContactSuggestionsTest.kt](file:///l:/Android/sms-sentry/app/src/test/java/in/sreerajp/sms_sentry/ContactSuggestionsTest.kt) to test suggestions retrieval and filter matching by name or number under Robolectric.

## Verification Results
- Executed [ContactSuggestionsTest](file:///l:/Android/sms-sentry/app/src/test/java/in/sreerajp/sms_sentry/ContactSuggestionsTest.kt) successfully: all assertions passed.
- Compiled the debug target using `./gradlew.bat :app:compileDebugKotlin` successfully.
- Verified existing classification tests with `./gradlew.bat :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"`: passed successfully.
