# Change log: rename namespace + applicationId to `in.sreerajp.sms_sentry`

Implements [plans/20260613_095029_rename-namespace.md](../plans/20260613_095029_rename-namespace.md).

## What changed

Renamed the app identity from the placeholder `com.example` to `in.sreerajp.sms_sentry`
for both `namespace` and `applicationId`.

### Build config
- **app/build.gradle.kts**: `namespace` and `applicationId` → `"in.sreerajp.sms_sentry"`.

### Source relocation
Moved the package trees to match the new package (main, test, androidTest):
- `app/src/<set>/java/com/example/` → `app/src/<set>/java/in/sreerajp/sms_sentry/`
- Removed the now-empty `com/example` directories.

### Kotlin sources (20 files)
- `package` / `import` / inline fully-qualified references rewritten from `com.example`
  to `` `in`.sreerajp.sms_sentry ``. The first segment `in` is backtick-escaped because
  it is a hard keyword in Kotlin.
- **String literals** (broadcast action constants, the androidTest `packageName`
  assertion) rewritten to plain `in.sreerajp.sms_sentry` (no backticks).

### Manifest
- **app/src/main/AndroidManifest.xml**: the three `<action>` names
  (`ACTION_COPY_OTP`, `ACTION_DELETE_MESSAGE`, `ACTION_OPEN_APP`) reprefixed to
  `in.sreerajp.sms_sentry.*`. Relative component names (`.MainActivity`,
  `.receiver.*`) were left unchanged — they resolve against the new namespace.

### Docs
- **docs/architecture.md**: identity reference updated.
- **docs/build-and-test.md**: single-test example FQNs updated.

## Decision recorded
- Kept the `in.*` root (over an alternative like `dev.*`) per the user's choice,
  accepting backtick escaping in Kotlin code.

## Verification
- `grep` confirms zero residual `com.example` in code/manifest/docs (only the plan file
  retains it, as historical record).
- No leftover `com/example` source directories.
- Full Gradle compile not run: repo has no Gradle wrapper (per CLAUDE.md), so a build
  was not executed in this environment.

## Note for the user
Because `applicationId` changed, the app installs as a **new** package; any previously
installed `com.example` build will coexist rather than upgrade in place.
