# Change log: use smekeystore.jks for both debug and release

Implements [plans/20260613_104310_signing-keystore-fix.md](../plans/20260613_104310_signing-keystore-fix.md).

## What changed

### `app/build.gradle.kts`
- Added `import java.util.Properties` and a top-level `smeKeystorePassword` lookup that reads
  `SME_KEYSTORE_PASSWORD` from `local.properties` (gitignored), falling back to the
  `SME_KEYSTORE_PASSWORD` environment variable.
- Replaced the two signing configs (`release` env-var config + `debugConfig` pointing at the
  missing `debug.keystore`) with a single `sme` config:
  - `storeFile = file("${rootDir}/smekeystore.jks")`
  - `keyAlias = "sms-sentry"`
  - `storePassword` / `keyPassword` = `smeKeystorePassword`
- Pointed both `buildTypes.release` and `buildTypes.debug` at `signingConfigs.getByName("sme")`.

### `docs/build-and-test.md`
- Rewrote the "Signing gotchas" section: both build types use `smekeystore.jks` (alias
  `sms-sentry`) with the password from `SME_KEYSTORE_PASSWORD`. Removed the obsolete
  `debug.keystore` / `debug.keystore.base64` and release env-var (`KEYSTORE_PATH` etc.) notes.

### `README.md`
- Replaced step 5 (removing the `debugConfig` line) with an instruction to add
  `SME_KEYSTORE_PASSWORD` to `local.properties`.

## Fixes
- Resolves the `:app:validateSigningDebug` failure: `Keystore file '...debug.keystore' not found
  for signing config 'debugConfig'`.

## User action required
- Add `SME_KEYSTORE_PASSWORD=<your keystore password>` to `local.properties` before building.

## Notes / not done
- `debug.keystore` line left in `.gitignore` (harmless). `my-upload-key.jks` env-var path and the
  `debug.keystore.base64` file were not deleted; they are now unused but left in place.
