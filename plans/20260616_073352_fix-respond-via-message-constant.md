# Fix: Unresolved reference `ACTION_RESPOND_VIA_MESSAGE`

## Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/receiver/HeadlessSmsSendService.kt`

## The issue

The IDE reports `Unresolved reference 'ACTION_RESPOND_VIA_MESSAGE'` at line 21:

```kotlin
if (intent?.action == Intent.ACTION_RESPOND_VIA_MESSAGE) {
```

`ACTION_RESPOND_VIA_MESSAGE` is **not** a member of `android.content.Intent`. The constant is
defined on `android.telephony.TelephonyManager`:

```
TelephonyManager.ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
```

So the reference fails to compile.

## The plan for the fix

1. Add the import `android.telephony.TelephonyManager`.
2. Change the comparison on line 21 from `Intent.ACTION_RESPOND_VIA_MESSAGE` to
   `TelephonyManager.ACTION_RESPOND_VIA_MESSAGE`.

No behavioral change — the underlying action string (`"android.intent.action.RESPOND_VIA_MESSAGE"`)
is identical; this only points the code at the correct constant so it compiles.
