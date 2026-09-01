# Security — SMS Sentry

This document details the security model, sensitive data protections, threat boundaries, and cryptographic architecture of SMS Sentry.
Read this before changing any code touching permissions, authentication, storage, P2P sync, or system notifications.

**Read first:**
- [../AGENTS.md](../AGENTS.md) — mandatory repository rules and guidelines
- [../CLAUDE.md](../CLAUDE.md) — Claude Code instructions
- [architecture.md](architecture.md) — technical system design and ingestion pipeline
- [guidelines/security.md](guidelines/security.md) — Kotlin security blueprint reference

---

## 1. Security Scope

- **App:** SMS Sentry
- **Data sensitivity level:** `High` (User SMS messages, OTP codes, financial transaction records, account balances, address book contacts)
- **Applicability Profiles:**
  - `Core Baseline`
  - `Production App Extension`
  - `Sensitive Data Extension`
- **Platform:** Android (minSdk 24, targetSdk 36, compileSdk 36)
- **Connectivity:** 100% Offline / Local-only. No cloud AI/LLM backend, no telemetry SDKs, no external network endpoints.

---

## 2. Security Objectives

1. **Zero Cloud Telemetry & Data Sovereignty:** Guarantee that all SMS parsing, classification, finance deduction, and reminder extraction execute strictly on-device without remote network calls.
2. **Confidentiality of Financial & Personal Data:** Prevent unauthorized local access to financial ledgers, bank transactions, and sensitive balances via biometric device authentication (`BiometricPrompt` and `KeyguardManager`).
3. **Cryptographically Secure P2P Sync:** Secure direct local Wi-Fi / hotspot transfers with authenticated AES-256-GCM encryption using high-entropy ephemeral pairing keys.
4. **Leak-Free Runtime & Logs:** Ensure zero leakage of SMS message bodies, OTP codes, account numbers, contact details, or cryptographic keys into logs or unencrypted exports.
5. **Robust Local Persistence:** Prevent tampering with Room SQLite tables and maintain safe sequential database migrations.

---

## 3. Threat Model Summary

### In Scope Threats
- **Casual Device Access:** Unauthorized viewing of financial account balances and transaction ledgers by someone with physical access to an unlocked device.
- **Local Network Eavesdropping:** Man-in-the-middle interception or tampering of unencrypted peer-to-peer data sync packets over shared Wi-Fi networks.
- **Log Leakage:** Accidental exposure of OTP passwords or sensitive SMS contents in system logcat outputs.
- **Brute Force on Sync Keys:** Offline brute-force attacks against P2P sync session secrets.

### Out Of Scope Threats
- **Fully Compromised / Rooted OS:** Root-level attackers with physical memory dumping or kernel modification access.
- **Physical Hardware Attacks:** Chip-off or cold-boot hardware side-channel attacks against device memory.
- **Malicious Default SMS Apps:** Other system default apps intercepting raw OS SMS broadcasts prior to SMS Sentry.

---

## 4. Sensitive Data Inventory

| Data Type | Example | Storage Location | Protection Required |
|-----------|---------|------------------|---------------------|
| Incoming & Sent SMS | Bank alerts, personal chats | Room SQLite (`messages`) | Local sandboxed app storage; no cloud sync |
| Financial Transactions | Debits, credits, balances | Room SQLite (`finance_transactions`) | Protected by Biometric Lock; hidden behind mask |
| One-Time Passwords (OTPs) | 6-digit login codes | Memory, Temporary Notifications | Auto-extracted; never logged; copied via secure intent |
| P2P Session Keys | 64-char high-entropy pairing code | In-memory during pairing session | PBKDF2-derived AES-256-GCM key; wiped after sync |
| Address Book Contacts | Resolved names, avatar URIs | In-memory `LocalContactNames` | Read-only access from `ContactsContract` |

---

## 5. Storage Model

### At Rest
- **Local Database:** Sandboxed Room SQLite database (`sms_sentry.db`, schema version 6). Accessible only by the app UID on non-rooted devices.
- **Configuration & State:** `EncryptedSharedPreferences` / private `SharedPreferences` (`theme_prefs`, app settings).
- **Application Backup:** `android:allowBackup="false"` in `AndroidManifest.xml` to prevent plaintext backup extraction via ADB.

### In Memory
- Financial totals and liquid balances are kept in `StateFlow` state and concealed in UI behind `isBalanceRevealed` biometric gates.
- Ephemeral sync sockets and PBKDF2 derived keys are closed and garbage-collected immediately following transfer completion.

### In Transit (Local Wi-Fi P2P)
- Local socket communication operates over ephemeral Wi-Fi ports.
- Payloads are authenticated and encrypted using AES-256-GCM with a fresh 12-byte random IV per session and PBKDF2-HMAC-SHA256 key derivation (300,000 iterations).

---

## 6. Cryptography Design (P2P Sync Engine)

P2P sync payload transfers use authenticated symmetric encryption implemented in `in.sreerajp.sms_sentry.engine.P2PSyncEngine`:

- **Cipher Algorithm:** `AES/GCM/NoPadding` (256-bit key).
- **Authentication Tag:** 128-bit GCM tag for integrity and authenticity verification.
- **Key Derivation Function:** `PBKDF2WithHmacSHA256` with 300,000 iterations and a 16-byte cryptographically secure random salt generated per hosting session.
- **High-Entropy Pairing Code:** 64-character code chosen from an unambiguous 31-character alphabet (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`, excluding easily confused characters `0/O/1/I/L`), providing ~320 bits of entropy.
- **Replay Protection:** Unique 12-byte IV per sync session combined with single-use TCP sockets.

---

## 7. Biometric & Device Security Lock

- **Mechanism:** Android `BiometricPrompt` with `KeyguardManager` PIN/Pattern/Password fallback.
- **Protected Areas:**
  - Liquid savings total on Dashboard.
  - Accounts & Financial Ledger tab (`AccountsScreen`).
  - Transaction contribution drill-down sheets.
- **State Behavior:** Instant hide/reveal toggle with manual tap-to-relock control and automatic relock on screen navigation.

---

## 8. Android Permissions & Privacy Boundary

The app requests only strictly required Android permissions:
- `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`: Required for SMS ingestion, thread display, and composer capabilities.
- `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`: Required for MMS reception when configured as default SMS app.
- `READ_CONTACTS`: Resolves phone numbers to contact names and avatar photos locally.
- `SCHEDULE_EXACT_ALARM`: Triggers exact obligation reminder notifications and scheduled SMS dispatch.
- `POST_NOTIFICATIONS`: Delivers local themed notifications with one-tap OTP copy action.
- `CAMERA`: Optional permission used strictly for scanning P2P sync QR codes (`ScanContract`).
- **No `INTERNET` permission in release build path:** Offline classification guaranteed by the absence of remote network calls.

---

## 9. Security Verification & Checklist

- [x] Zero network calls in classification and ingestion paths.
- [x] `android:allowBackup="false"` verified in manifest.
- [x] AES-256-GCM encryption verified with automated unit test (`P2PSyncCryptoTest`).
- [x] Biometric authentication gate verified on financial views.
- [x] Zero logging of sensitive message bodies, OTPs, or passwords in release builds.
- [x] High-entropy sync pairing key generation verified with automated tests.
