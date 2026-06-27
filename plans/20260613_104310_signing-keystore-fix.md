# Fix signing: use smekeystore.jks for both debug and release

**Status:** completed

## Issue

`:app:validateSigningDebug` fails:

```
Keystore file 'L:\Android\sms-sentry\debug.keystore' not found for signing config 'debugConfig'.
```

The `debugConfig` signing config in [app/build.gradle.kts](../app/build.gradle.kts) points to a
non-existent `debug.keystore`. The actual keystore at the repo root is `smekeystore.jks`
(alias `sms-sentry`, store/key password identical). The user also wants the **release** build
to be signed with this same keystore instead of the env-var `my-upload-key.jks` / `upload` config.

## Decisions (confirmed with user)

- Sign **both** debug and release with `smekeystore.jks`.
- Key alias: `sms-sentry`.
- Store password == key password.
- Password is **not** hardcoded — it is read from a gitignored properties file
  (`local.properties`, already in `.gitignore`) via the property key `SME_KEYSTORE_PASSWORD`.

## Files to change

1. `app/build.gradle.kts`
   - Add loading of `local.properties` at the top of the `android { }` block (or file top)
     to read `SME_KEYSTORE_PASSWORD`. Fall back to an env var `SME_KEYSTORE_PASSWORD` so CI
     can still build.
   - Replace the two existing `signingConfigs` (`release`, `debugConfig`) with a single
     config named `sme`:
     - `storeFile = file("${rootDir}/smekeystore.jks")`
     - `keyAlias = "sms-sentry"`
     - `storePassword = keyPassword = <password from local.properties / env>`
   - Point both `buildTypes.release` and `buildTypes.debug` at `signingConfigs.getByName("sme")`.

2. `docs/build-and-test.md`
   - Update the "Signing gotchas" section: both build types now use `smekeystore.jks` with
     alias `sms-sentry`; password comes from `SME_KEYSTORE_PASSWORD` in `local.properties`.
     Drop the now-obsolete `debug.keystore` / `debug.keystore.base64` reconstruction note and
     the release env-var (`KEYSTORE_PATH` etc.) description.

3. `README.md`
   - Step 5 currently tells users to remove the `debugConfig` signing line. Update to reflect
     the single `sme` config and the `SME_KEYSTORE_PASSWORD` property requirement.

## User action required (not done by me)

Add to `local.properties` (gitignored):

```
SME_KEYSTORE_PASSWORD=<your keystore password>
```

## Notes

- `local.properties` is already gitignored, so the secret stays out of version control.
- `debug.keystore` is still listed in `.gitignore`; leaving that line is harmless but I can
  remove it for tidiness if desired.
