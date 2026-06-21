# Change log: Fix `ACTION_RESPOND_VIA_MESSAGE` unresolved reference

Implements plan: `plans/20260616_073352_fix-respond-via-message-constant.md`

## What changed

`app/src/main/java/in/sreerajp/sms_sentry/receiver/HeadlessSmsSendService.kt`:

1. Added import `android.telephony.TelephonyManager`.
2. Changed the action comparison from `Intent.ACTION_RESPOND_VIA_MESSAGE` to
   `TelephonyManager.ACTION_RESPOND_VIA_MESSAGE`, where the constant is actually defined.

## Why

`ACTION_RESPOND_VIA_MESSAGE` is not a member of `android.content.Intent`; it lives on
`android.telephony.TelephonyManager`. The previous reference failed to compile
("Unresolved reference 'ACTION_RESPOND_VIA_MESSAGE'").

## Impact

No behavioral change. Both constants resolve to the same action string
(`"android.intent.action.RESPOND_VIA_MESSAGE"`); this only fixes the compile error by
referencing the correct class.
