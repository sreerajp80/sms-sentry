# Change log: Primary and Secondary Entity Highlighting in Messages

Implements [plans/20260901_055000_entity_highlighting_primary_secondary.md](../plans/20260901_055000_entity_highlighting_primary_secondary.md).

## What changed

- Added an offline, on-device entity extraction engine (`MessageEntityExtractor`) supporting a two-tier hierarchy:
  - **Primary Entities** (High-emphasis bold Amber styling): OTP verification codes, order dispatch/courier tracking numbers (e.g. `5001241978590`, AWB, consignment, waybill, docket IDs), and currency amounts (e.g. `Rs.15000`, `₹15,000`, `$50.00`).
  - **Secondary Entities** (Medium-emphasis teal/blue styling): URLs and web links, bank and card account identifiers (e.g. `A/c x0731`), phone numbers and toll-free helpline numbers (`1800...`, `09449...`), and shortcodes (`567676`).
- Integrated entity highlighting and smart actions across the UI:
  - In `MessageBubble` (conversation view), applied `buildAnnotatedMessageBody` so all primary and secondary entities are highlighted directly in chat bubbles.
  - In `MessageDetailScreen`, applied full entity highlighting and added quick action buttons:
    - `Copy OTP (<otp>)`
    - `Copy Tracking No (<trackingId>)`
    - `Open Link` (launch browser or copy URL)
    - `Call (<phone>)` (launch dialer or copy number)
  - In `ConversationCard` (inbox list), added `CopyTrackingChip` when a tracking ID is present.
- Created `MessageEntityExtractorTest` to test extraction and span resolution across order dispatch and banking alerts.

## Files changed

- **`app/src/main/java/in/sreerajp/sms_sentry/engine/MessageEntityExtractor.kt`** (New)
  - Entity types, highlighting tiers, robust regex matchers, overlap resolver, and AnnotatedString builder.
- **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
  - Integrated entity highlighting in `MessageBubble` and `MessageDetailScreen`.
  - Added smart action buttons for tracking IDs, links, and phone numbers in `MessageDetailScreen` and `ConversationCard`.
- **`app/src/test/java/in/sreerajp/sms_sentry/MessageEntityExtractorTest.kt`** (New)
  - Comprehensive unit test suite for entity detection, tracking IDs, URLs, and multi-entity banking messages.

## Verification

- `./gradlew testDebugUnitTest` — all unit tests pass (including new `MessageEntityExtractorTest` and existing suite).
- `./gradlew assembleDebug` — debug APK built cleanly.
- `./gradlew lintDebug` — Android lint passed with zero errors.
