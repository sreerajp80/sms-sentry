# Harden P2P sync: high-entropy pairing code + robustness

**Status:** completed

## What this is

Follow-up to [20260627_192657_harden-p2p-sync-crypto.md](20260627_192657_harden-p2p-sync-crypto.md),
which hardened the *payload crypto* (PBKDF2 + AES/GCM, no fallback) but explicitly left
three gaps open: the low-entropy PIN is **offline-attackable**, there is **no MITM
resistance**, and **no forward secrecy**. Plus two robustness issues: the line-based wire
protocol can be DoS'd by an unbounded line, and the imported payload is only loosely validated.

This plan closes all of those by **replacing the 4–6 digit PIN with a high-entropy,
per-session pairing code** (the user reads it off the host and types it into the client),
and adding input bounds + payload validation.

## The issues (current behavior)

In [P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt):

1. **Offline PIN attack** — host sends the salt in the clear, then the client sends
   `encrypt("HELLO_SYNC")`. `HELLO_SYNC` is a known plaintext, so an eavesdropper who
   captures salt + ciphertext can brute-force a 4–6 digit PIN offline (seconds to hours).
   Cracking the PIN decrypts the whole captured SMS payload.
2. **No MITM resistance** — nothing binds the connection to the intended peer.
3. **No forward secrecy** — the session key is a deterministic function of a reused PIN.
4. **Unbounded `readLine()`** (host + client) — a malicious peer can send a multi-GB line
   and OOM the app.
5. **Loosely-validated payload** — imported JSON fields (`timestamp`, `simId`, `body`,
   `sender`) are trusted; no size/count caps; a malformed array can fail mid-import.

## The fix

Root cause of 1–3 is that the shared secret is low-entropy. Replace it with a strong secret;
the existing PBKDF2 + AES/GCM crypto then becomes genuinely secure with **no PAKE needed**.

### High-entropy pairing code (replaces the PIN)
- **Host generates** a fresh random code each time it starts hosting, using `SecureRandom`.
  Format: **64 characters from an unambiguous alphabet** (Crockford-style: `0/O`, `1/I/L`
  removed → 32 safe chars → ~320 bits), displayed **grouped into 8 blocks of 8** for
  readability (e.g. `K7M2P9QR-3T6VWX4Y-...`). Hyphens/whitespace are stripped before use.
- **User transfers it out-of-band:** reads it on the host screen, types it into the client.
  The code is **never sent over the socket**, so it is a true pre-shared key →
  defeats MITM, and being per-session + discarded after use → forward secrecy.
- The code feeds the existing `deriveKey()` exactly where the PIN does today
  (`PBKDF2WithHmacSHA256`, per-session salt, 256-bit AES key). The `HELLO_SYNC` handshake
  is **kept as-is** — with a ~320-bit secret it is no longer offline-attackable, so the
  known-plaintext is harmless. No PAKE, no ECDH, no TLS required.
- **Never log or persist the code.** It lives only in memory during the hosting session and
  in the `SyncState.Hosting` value for display; cleared on `stopHostServer()`.

### Robustness
- **Bounded reads:** replace bare `reader.readLine()` with a helper that caps line length
  (e.g. 64 MB for the payload line, small caps for handshake lines) and aborts with
  `SyncState.Error` if exceeded. Set a socket read timeout on the host side too.
- **Payload validation:** before importing, cap message count and per-field sizes; validate
  `timestamp`/`simId` ranges; wrap the import loop so a malformed entry aborts cleanly
  (the existing `processAndInsertMessage` funnel is unchanged).

## Files to be changed

- [app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt)
  — add `generatePairingCode()`; normalize the code (strip separators) before `deriveKey`;
  add a bounded-readLine helper + host read timeout; add payload count/size/range validation.
  `SyncState.Hosting.pin` is repurposed to carry the pairing code (rename to `code` if it
  doesn't ripple into the UI too much — otherwise keep the field name and just change the value).
- [app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt)
  — host screen: display the generated code (grouped, monospaced, with a copy button) instead
  of a 4–6 digit PIN; client screen: widen the code entry field to accept the full code and
  strip separators on input. (Scope confirmed once I read the current Sync UI.)
- [app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt)
  — only if it currently generates/validates the PIN; pass-through otherwise.
- [docs/architecture.md](../docs/architecture.md) — update the P2P sync security note (~line 93)
  to describe the high-entropy code, and why offline-attack/MITM/forward-secrecy are now covered.
- Optional unit test: pairing-code entropy/format, code round-trip encrypt→decrypt, wrong-code
  rejection, oversized-line rejection — if it fits the existing Robolectric/JVM setup.

## Out of scope
- **No TLS / Noise / ECDH** — a fresh ~320-bit per-session pre-shared code already gives
  confidentiality, MITM resistance, and (per-session) forward secrecy. ECDH/Noise would only
  add protection against device compromise *during* a live session; not worth the complexity here.
- **No QR pairing** — typed code is acceptable for an occasional action. QR can be a later
  convenience add (the code format is QR-ready).
- No change to transport (still a plaintext `ServerSocket` on port 8243) or to the
  `processAndInsertMessage` ingestion funnel.

## Open question for the user
- Code length: 64 chars (your suggestion, ~320 bits) is comfortably overkill. I'll implement
  64 unless you'd prefer shorter (e.g. 32 chars / ~160 bits) to reduce typing — still far
  beyond brute-force range. Say the word and I'll adjust before implementing.
