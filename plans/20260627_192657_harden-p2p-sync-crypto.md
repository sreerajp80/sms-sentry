# Harden P2P sync encryption

**Status:** completed

## What this is

The P2P sync feature (Sync tab → Host Server / Connect to Peer) transfers the full
SMS database between two devices over a local socket. The current crypto in
[P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt)
is insecure-by-design and this plan replaces it with a properly hardened scheme.

## The issues (current behavior)

In [P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt):

1. **Weak key derivation** — the AES key is the user PIN padded to 16 bytes with `'0'`
   (`pin.padEnd(16, '0').take(16)`, lines 49 & 63). A 4–6 digit PIN gives a trivially
   small keyspace.
2. **AES/ECB/PKCS5Padding** (lines 51, 65) — ECB leaks plaintext structure, has no IV,
   and provides no integrity/authentication.
3. **Silent XOR fallback** — any crypto exception silently downgrades to a repeating-key
   XOR cipher (`xorCipher`, lines 57, 71–76, 81–87), which is breakable by hand.
4. **No real authentication** — the "PIN challenge" is just "does the decrypted greeting
   start with `HELLO_SYNC`" (line 155). No cryptographic proof of the shared secret.
5. **No payload integrity** — a network attacker could tamper with the JSON payload.
6. **Base64.DEFAULT** (lines 54, 67) emits newlines, which is fragile for the line-based
   (`readLine()` / `println()`) wire protocol; should be `NO_WRAP`.

## The plan for the fix

Rework the crypto and handshake **entirely inside `P2PSyncEngine.kt`**. The public API
(`startHostServer(pin, repository)`, `connectAndSync(hostIp, pin, repository)`) and the
`SyncState` sealed class are unchanged, so `SmsOrganizerViewModel` and the Sync UI need
no edits. Host and client must change together (the protocol is symmetric), so both roles
are updated in the same file.

### New cryptography
- **Key derivation:** `PBKDF2WithHmacSHA256` over the PIN with a per-session random
  16-byte salt, e.g. 120k iterations → 256-bit AES key. (Salt is not secret; it is sent
  in the clear at the start of the handshake.)
- **Cipher:** `AES/GCM/NoPadding` with a fresh random 12-byte IV per message and a
  128-bit auth tag. Wire format per encrypted message: `Base64.NO_WRAP(IV ‖ ciphertext+tag)`,
  one line. GCM tag verification gives both confidentiality **and** integrity/authentication.
- **Remove `xorCipher` and both silent fallbacks.** A crypto failure now surfaces as a
  `SyncState.Error`, never a downgrade.

### New handshake protocol (replaces lines 139–190 host / 204–275 client)
1. **Host → Client:** random salt, `Base64.NO_WRAP`, cleartext (first line).
2. Both derive `key = PBKDF2(pin, salt)`.
3. **Client → Host:** GCM-encrypt the literal `HELLO_SYNC` (fresh IV).
4. **Host:** decrypt+verify. GCM tag failure (i.e. wrong PIN ⇒ wrong key) or payload
   mismatch ⇒ send GCM-encrypted `DENIED` and set `SyncState.Error("Invalid PIN")`.
   This replaces the weak `startsWith` check with cryptographic authentication.
5. **Host → Client:** GCM-encrypt `ACCEPT_SYNC`.
6. **Host → Client:** GCM-encrypt the JSON message payload (fresh IV).
7. **Client:** decrypt+verify payload; tag failure ⇒ `SyncState.Error("Decryption failed —
   check the PIN")` (no fallback), otherwise import via `repository.processAndInsertMessage`
   exactly as today.

Backward compatibility: this is a breaking wire-protocol change — both devices must run the
updated build to sync. Acceptable for a local-only, same-app-version feature (no persisted
ciphertext is involved).

## Files to be changed

- [app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt)
  — replace `encrypt`/`decrypt`/`xorCipher` with PBKDF2 + AES/GCM helpers; add salt
  generation; rewrite `handleSyncTransaction` (host) and `connectAndSync` (client) for the
  new handshake. No public-signature changes.
- [docs/architecture.md](../docs/architecture.md) — update the "P2P sync security is
  intentionally weak / for-demo" note (around line 95) to describe the hardened scheme.
- Optionally: a unit test under `app/src/test/java/...` covering encrypt→decrypt round-trip
  and wrong-PIN rejection (only if it fits the existing Robolectric/JVM test setup;
  otherwise omitted and noted in the change log).

## Out of scope
- No TLS / no certificate PKI (PBKDF2-derived GCM over the shared PIN is the trust anchor).
- No change to the transport (still a plaintext `ServerSocket` on port 8243; the payload,
  not the socket, is now authenticated-encrypted).
- No UI changes (PIN entry, IP entry, buttons all unchanged).
