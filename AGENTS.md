# AGENTS.md — SMS Sentry

This file is read by AI agents and LLM coding assistants (Gemini, Antigravity, Cursor, Windsurf, Codex, etc.) at the start of every session in this repository.
Read it before making any change. See the docs table below for full detail.

---

## Project identity

| Field | Value |
|-------|-------|
| App name | SMS Sentry |
| Type | Offline SMS organizer, classifier (Personal/Finance/Reminder/Spam), finance ledger, reminder extractor, and P2P sync |
| Platform | Android (minSdk 24, targetSdk 36, compileSdk 36) |
| Package / namespace | `in.sreerajp.sms_sentry` |
| Kotlin | 2.2.10 |
| Compose BOM | 2024.09.00 |
| AGP | 8.13.2 |
| JDK | 17 (Test JVM 21 for SDK 36 Robolectric) |
| State management | ViewModel + StateFlow (`SmsOrganizerViewModel`) |
| Navigation | Single-Activity tab switching + custom backstack in `MainActivity` / `SmsOrganizerUi.kt` |
| Database | Room (`AppDatabase`) + EncryptedSharedPreferences |
| Orientation | Portrait only |
| Connectivity | Offline-first / local-only — no external cloud/LLM runtime path (local Wi-Fi P2P sync only) |

---

## Read these docs before working

| Document | Read when |
|----------|-----------|
| [docs/GUIDELINES_MANIFEST.md](docs/GUIDELINES_MANIFEST.md) | Index of shared Kotlin guidelines and standards (submodule at `docs/guidelines/`) |
| [docs/architecture.md](docs/architecture.md) | Changing structure, screens, state, services, models, ingestion funnel, repositories |
| [docs/build-and-test.md](docs/build-and-test.md) | Build/install/test commands, Robolectric single-test syntax, Roborazzi screenshots |
| [docs/features.md](docs/features.md) | Feature inventory, classification rules, UI specifications |
| [docs/security.md](docs/security.md) | Touching permissions, logging, storage, crypto, manifest |
| [docs/release_process.md](docs/release_process.md) | Building a release, versioning, release checklist |
| [docs/guidelines/kotlin_project_engineering_standard.md](docs/guidelines/kotlin_project_engineering_standard.md) | Project-agnostic engineering standard (structure, UI, logging, security, git) |

> If a doc is copied into this project's own `docs/`, the local copy wins over the submodule.

---

## Package naming

One identifier everywhere: the source package, the build `namespace`, and the `applicationId`
are all `in.sreerajp.sms_sentry`.

Note that `in` is a Kotlin keyword, so it must be backticked in source — in
every `package` line and in every import of our own code:

```kotlin
package `in`.sreerajp.sms_sentry.ui
import `in`.sreerajp.sms_sentry.data.SmsRepository
```

---

## Hard rules (must follow — these override convenience)

1. **Offline classification & privacy first.** All SMS parsing, spam detection, finance extraction, and reminders run 100% locally on-device. No runtime network calls, external analytics, or cloud AI/LLM APIs.
2. **Single ingestion funnel.** All incoming, synced, or simulated messages MUST flow through `SmsRepository.processAndInsertMessage()` to guarantee consistent classification, finance deduction, and reminder extraction.
3. **Never crash on malformed SMS input.** All regex, date, and finance parsers must handle unexpected input gracefully without throwing unhandled exceptions.

---

## Architecture rules

- **Layout:** Single-module MVVM under `app/src/main/java/in/sreerajp/sms_sentry/` — packages: `config/`, `data/`, `engine/`, `receiver/`, `service/`, `sync/`, `ui/`, `util/`, `MainActivity.kt`.
- **Layer boundaries:** Composables must not directly perform database operations or access raw content resolvers. Always route through `SmsOrganizerViewModel` and `SmsRepository`.
- **State:** UI consumes `StateFlow` from `SmsOrganizerViewModel`. State transitions are explicit.
- **Models:** Room entities and domain models are immutable data classes (`copy()`).

---

## Build & run commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on device/emulator
./gradlew testDebugUnitTest      # run JVM/Robolectric unit tests
./gradlew connectedDebugAndroidTest  # run instrumented tests
./gradlew lint                   # Android lint (must be clean)
```

For running a single test:
```bash
./gradlew testDebugUnitTest --tests "in.sreerajp.sms_sentry.ContactGroupingTest"
```

---

## Security rules

- Never log message bodies, OTP codes, sensitive finance details, contact names, or crypto secrets in production or release builds.
- Store sensitive configuration and encryption keys in `EncryptedSharedPreferences` or Android Keystore.
- P2P sync payload transfers use authenticated AES-GCM encryption with short-lived session keys.

---

## String resources

- All user-visible text in UI screens must come from `res/values/strings.xml` via `stringResource()` or `context.getString()` — never hardcoded raw string literals in Composables.
- Literals are permitted only for internal logs, debugging tags, route/action identifiers, and regex patterns.

---

## Code style / naming

- Files: `PascalCase.kt` for classes/Composables, `camelCase.kt` for standalone utility files.
- Classes/Objects: `PascalCase`; functions/variables: `camelCase`; constants: `SCREAMING_SNAKE_CASE`.
- Package names: `lowercase` (always backtick `` `in` ``).
- Keep Android Lint at zero errors.

---

## Testing rules

- Unit and Robolectric tests live under `app/src/test/java/in/sreerajp/sms_sentry/`.
- Screenshot regression tests use Roborazzi.
- Add or update tests whenever you modify classification rules, reminder scheduling, contact resolving, or finance extraction logic.

---

## Where things live

```
AGENTS.md            # this file — project rules for AI agents / LLMs
CLAUDE.md            # Claude Code native project rules
docs/                # design docs & guidelines manifest
docs/guidelines/     # shared Kotlin guidelines submodule
plans/               # one plan per change (see workflow rules)
change_log/          # one log per implemented change
app/src/main/        # app source (Kotlin, resources, AndroidManifest)
app/src/test/        # unit & Robolectric tests
app/src/androidTest/ # instrumented tests
```

---

## Workflow rules (mandatory — from global rules)

Every change follows plan-before-changing and log-after-changing:

1. **Plan before changing.** Write a full plan to `plans/` named
   `yyyymmdd_hhMMss_<short-slug>.md` with a `**Status:**` line, the files to change, the issue,
   and the fix. Then **STOP and get explicit approval** before editing/creating/deleting any
   project file (other than the plan). A question or ambiguous reply is not approval.
2. **Log after changing.** After implementing, write a change log to `change_log/` named
   `yyyymmdd_hhMMss_<short-slug>.md` describing what changed and referencing its plan.
3. **Relative paths & privacy only.** `plans/` and `change_log/` files are committed and may become
   public on the internet. They MUST use relative repository paths only (never absolute system
   paths like `C:\...`, `l:\...`, or `file:///...`). They MUST NOT contain any **local system
   details** — OS user name, computer/host name, home or drive-letter paths, network share names,
   LAN/internal IP addresses, local server URLs with ports, device serial numbers, personal email
   addresses — or any secret (API keys, tokens, passwords, keystore passphrases, credentials, PII).
   Write them as if a stranger will read them; nothing should reveal the machine they came from.

Create `plans/` and `change_log/` if they do not exist.

---

## Communication rules

- **Always use simple English.** Write all responses, plans, change logs, and explanations in
  plain, simple English. Short sentences, common words. Explain any jargon you must use.

---

## What AI agents must always / never do

**Always:**
- Read `AGENTS.md` and `CLAUDE.md` before making changes.
- Follow the plan-before-changing and log-after-changing workflow.
- Route all message insertion through `SmsRepository.processAndInsertMessage()`.
- Use relative repository paths only in plans and logs.

**Never:**
- Introduce network calls or remote dependencies into the SMS classification engine.
- Put direct database / DAO operations inside Composable UI functions.
- Log sensitive SMS contents, OTPs, or authentication keys.
