# Change log — P2P sync: high-entropy pairing code + robustness

Implements [plans/20260627_195915_p2p-highentropy-code-and-hardening.md](../plans/20260627_195915_p2p-highentropy-code-and-hardening.md).
Follow-up to [change_log/20260627_194339_harden-p2p-sync-crypto.md](20260627_194339_harden-p2p-sync-crypto.md),
which hardened the payload crypto but left the low-entropy PIN (offline-attackable), MITM,
forward secrecy, and wire-protocol robustness open.

## What changed

### `app/.../engine/P2PSyncEngine.kt`
- **Replaced the low-entropy PIN with a high-entropy pairing code.** Added
  `generatePairingCode()` — a fresh 64-char code over a 31-symbol unambiguous alphabet
  (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`, no `0/O/1/I/L`) → ~320 bits, drawn from `SecureRandom`.
  Added `normalizeCode()` (uppercase + strip non-alphabet) so the host and a client typing the
  hyphen-grouped code derive the same key. `startHostServer`/`connectAndSync` normalize the
  incoming secret and feed it to the unchanged `deriveKey` (PBKDF2-HMAC-SHA256 + per-session
  salt) / AES-GCM path. Because the code is high-entropy, per-session, and transferred
  out-of-band (never sent over the socket), this closes the offline-attack, MITM, and
  forward-secrecy gaps **without** a PAKE/ECDH/TLS. The `HELLO_SYNC` handshake is retained
  (now harmless as a known plaintext).
- **DoS hardening.** Added `readBoundedLine()` replacing all `BufferedReader.readLine()` calls
  (4 KB cap on handshake lines, 64 MB on the payload line; aborts on overflow). Set a 30 s
  socket read timeout on both host and client.
- **Untrusted-payload validation.** Cap imported message count (100 000), per-field length
  (100 000 chars for `sender`/`body`), and reject non-positive `timestamp` before the data
  reaches `repository.processAndInsertMessage`.
- `SyncState.Hosting.pin` renamed to `code`. The crypto companion was made non-`private` to
  expose `PAIRING_CODE_GROUP` to the UI formatter.

### `app/.../ui/SmsOrganizerViewModel.kt`
- Added `generatePairingCode()` pass-through to the engine.

### `app/.../ui/SmsOrganizerUi.kt` (Sync screen)
- Host now generates the pairing code via `viewModel.generatePairingCode()` (was a 4-digit
  `Random().nextInt(10000)`).
- Added `formatPairingCode()` helper (groups the raw code into hyphen-separated 8-char blocks).
- Host config panel and the active-Hosting card display the code in a monospace font with a
  copy-to-clipboard button and an explanatory note that it is one-time and never sent over the
  network.
- Join panel field relabeled "Pairing code from host", monospace, multi-line (up to 3 lines)
  to fit the longer code.
- Added `import androidx.compose.ui.text.font.FontFamily` and
  `import in.sreerajp.sms_sentry.engine.P2PSyncEngine`.

### `docs/architecture.md`
- Rewrote the P2P sync security note to describe the high-entropy pairing code, why
  offline-attack/MITM/forward-secrecy are now covered without a PAKE, and the new
  bounded-read / payload-validation robustness.

## Verification
- `./gradlew.bat :app:assembleDebug` → **BUILD SUCCESSFUL**.
- Added unit tests (the plan's optional item, completed on follow-up request):
  `app/src/test/java/in/sreerajp/sms_sentry/P2PSyncCryptoTest.kt` — 8 Robolectric tests
  covering pairing-code length/alphabet, uniqueness, encrypt→decrypt round-trip, fresh-IV
  (ciphertexts differ), wrong-code GCM rejection, tampered-ciphertext rejection, and
  `normalizeCode` so a typed grouped/lower-case code derives the host's key. To enable this,
  the crypto primitives (`newSalt`/`deriveKey`/`encrypt`/`decrypt`/`normalizeCode`) were changed
  from `private` to `internal` as a documented module-local test seam.
- `./gradlew.bat :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.P2PSyncCryptoTest"`
  → **BUILD SUCCESSFUL**, 8 tests / 0 failures.

## Notes / breaking change
- Wire protocol is unchanged in shape but the secret semantics differ; both devices must run
  this build. More importantly, the **secret is now a 64-char code, not a PIN** — users must
  copy the longer code between devices (a copy button is provided on the host).
