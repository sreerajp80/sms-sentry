# Change log — Harden P2P sync encryption

Implements [plans/20260627_192657_harden-p2p-sync-crypto.md](../plans/20260627_192657_harden-p2p-sync-crypto.md).

## What changed

### [app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt)
Replaced the demo-grade crypto with an authenticated scheme:

- **Removed** `xorCipher` and both silent XOR fallbacks, the PIN-zero-padded AES key, and
  `AES/ECB/PKCS5Padding`. A crypto failure now aborts the sync as `SyncState.Error` instead
  of downgrading.
- **Key derivation:** new `deriveKey()` uses `PBKDF2WithHmacSHA256` (120k iterations,
  256-bit key) over a per-session 16-byte random salt from `SecureRandom`.
- **Cipher:** `encrypt`/`decrypt` now use `AES/GCM/NoPadding` with a fresh random 12-byte IV
  and 128-bit tag per message. Wire format is `Base64.NO_WRAP(IV ‖ ciphertext+tag)` (single
  line, safe for the `readLine`/`println` protocol). `decrypt` throws on tag mismatch.
- **Handshake (host `handleSyncTransaction`):** sends the salt in the clear first, derives
  the key, then authenticates the client by GCM-decrypting its greeting and checking it
  equals `HELLO_SYNC`. A wrong PIN → wrong key → tag failure → `DENIED` + error. This
  replaces the old non-cryptographic `startsWith("HELLO_SYNC")` check.
- **Handshake (client `connectAndSync`):** reads the salt, derives the key, sends a
  GCM-encrypted `HELLO_SYNC`, then verifies the `ACCEPT_SYNC` response and decrypts the
  payload with tag verification (no fallback). Import path via
  `repository.processAndInsertMessage` is unchanged.
- Added imports: `SecureRandom`, `SecretKeyFactory`, `GCMParameterSpec`, `PBEKeySpec`.
- New crypto constants live in a `private companion object`.

Public API (`startHostServer`, `connectAndSync`) and the `SyncState` sealed class are
unchanged, so `SmsOrganizerViewModel` and the Sync UI required no edits.

### [docs/architecture.md](../docs/architecture.md)
Rewrote the "P2P sync security is intentionally weak / for-demo" note to describe the
hardened PBKDF2 + AES/GCM scheme, the salt-first handshake, the GCM-tag authentication, and
the fact that the transport socket itself is still plaintext (payload, not socket, is
protected).

## Compatibility
Breaking wire-protocol change — both devices must run this build to sync. No persisted
ciphertext is affected.

## Verification
`./gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL. No unit test added (the existing
suite is Robolectric/Roborazzi UI-oriented and the engine crypto is private); the encrypt→
decrypt round-trip and wrong-PIN rejection were reasoned through against the GCM semantics.

## Out of scope (unchanged)
No TLS/PKI, no transport-level encryption, no UI changes, still port 8243.
