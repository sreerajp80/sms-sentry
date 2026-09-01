# Plan: Primary and Secondary Entity Highlighting in Messages

**Status:** completed

## What the user wants

Highlight important information in SMS message bubbles and detail views with a two-tier visual hierarchy:
- **Primary Highlights**: OTP, Tracking ID (order dispatch / AWB / consignment numbers), and Transaction / Currency Amounts.
- **Secondary Highlights**: URLs / Links, Account numbers (e.g. `A/c x0731`, card numbers), Phone numbers / helpline numbers / shortcodes, and other reference IDs.

In the user's example screenshots:
1. **Order dispatch message**:
   - Primary: Tracking number (`5001241978590` / `tracking No.5001241978590`)
   - Secondary: URL (`https://kcsms.co/BOMSHV/sYFQP7z`)
2. **ATM / Bank transaction message**:
   - Primary: OTP (`1149`), Amount (`Rs.15000`)
   - Secondary: Account number (`A/c x0731`), Phone numbers / shortcodes (`567676`, `1800111109`, `09449112211`)

## The issue / gap

Currently:
- Only OTP is detected and highlighted in `MessageDetailScreen` with an amber highlight (`Color(0xFFE0A900)`).
- Conversation view (`MessageBubble`) displays plain raw text (`msg.body`) with no highlighting.
- Tracking numbers, currency amounts, URLs, account numbers, and phone numbers are not highlighted or differentiated into primary vs secondary tiers.
- Quick action chips in the detail view only exist for OTP; there are no quick action chips/buttons for tracking IDs, URLs, or phone numbers.

## Proposed Changes

1. **`app/src/main/java/in/sreerajp/sms_sentry/engine/MessageEntityExtractor.kt`** (New File)
   - Define `EntityType` (OTP, TRACKING_ID, AMOUNT, URL, ACCOUNT_NUMBER, PHONE_NUMBER, REFERENCE_ID).
   - Define `HighlightTier` (`PRIMARY` for OTP, Tracking ID, Amount; `SECONDARY` for URL, Account Number, Phone Number, Reference ID).
   - Implement robust local regex matchers for:
     - **OTP**: 4–8 digits with OTP keywords (leveraging/integrating `detectOtp` rules).
     - **Tracking ID**: Patterns matching `tracking (?:no|id|num|number)?[\s.:#-]*([a-zA-Z0-9]{6,25})`, `awb[\s.:#-]*([a-zA-Z0-9]+)`, `consignment[\s.:#-]*([a-zA-Z0-9]+)`, `waybill[\s.:#-]*([a-zA-Z0-9]+)`, `docket[\s.:#-]*([a-zA-Z0-9]+)`.
     - **Amount**: Currency symbols (`Rs.`, `₹`, `$`, `INR`, `USD`, `EUR`, `€`, `GBP`, `£`) with numeric amounts and commas/decimals.
     - **URL**: `https?://[^\s]+`, `www\.[^\s]+`, or known shortlink domains (`bit.ly`, `tinyurl.com`, `kcsms.co`, etc.).
     - **Account Number**: `(?:a/c|acct|account|card)\s*(?:no\.?|ending in|ending with)?\s*([xX*•·\d\s-]*\d{3,5})`.
     - **Phone Number**: 10–12 digit mobile/landline numbers, toll-free `1800...`/`1860...`, and 5–6 digit action shortcodes (e.g. `send ... to 567676`).
   - Extract entities, resolve overlaps (prioritizing Primary over Secondary, longer matches over shorter), and return a sorted list of non-overlapping spans.
   - Provide helper `buildAnnotatedMessageBody(body, isDarkTheme, ...)` to generate Compose `AnnotatedString` with appropriate `SpanStyle`:
     - **Primary**: Bold / extra bold with warm amber/gold styling (`Color(0xFFE0A900)` and `Color(0xFFFFB300).copy(alpha = 0.18f)` background).
     - **Secondary**: Semi-bold with teal/blue styling (`Color(0xFF0288D1)` / `Color(0xFF4FC3F7)` and subtle background tint, underline for URLs).

2. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   - Update `MessageBubble` in `ThreadScreen` to use `buildAnnotatedMessageBody(msg.body)` instead of plain `msg.body`.
   - Update `MessageDetailScreen` to use `buildAnnotatedMessageBody(msg.body)` and display contextual action chips/buttons:
     - Copy OTP button (when OTP present)
     - Copy Tracking ID button (when Tracking ID present)
     - Open Link / Copy URL button (when URL present)
     - Call / Copy Phone button (when Phone number present)
   - Ensure strings are properly localized in `res/values/strings.xml` per project guidelines.

3. **`app/src/main/res/values/strings.xml`**
   - Add string resources for new action buttons (`copy_tracking_id`, `open_link`, `call_number`, `copy_phone`, etc.).

4. **`app/src/test/java/in/sreerajp/sms_sentry/MessageEntityExtractorTest.kt`** (New Test File)
   - Unit tests covering:
     - Order dispatch with tracking number and URL (screenshot 1 case).
     - ATM cash withdrawal with OTP, Amount, Account, and Phone numbers (screenshot 2 case).
     - Non-overlapping resolution and correct primary vs secondary tier assignments.

## Verification plan

- Run unit tests: `./gradlew testDebugUnitTest`
- Assemble debug APK: `./gradlew assembleDebug`
