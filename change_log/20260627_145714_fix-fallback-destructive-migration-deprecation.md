# Fix deprecated `fallbackToDestructiveMigration()` call

Implements [plans/20260627_145648_fix-fallback-destructive-migration-deprecation.md](../plans/20260627_145648_fix-fallback-destructive-migration-deprecation.md).

## Change

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt`: replaced the deprecated
  no-arg `.fallbackToDestructiveMigration()` (line 85) with the explicit overload
  `.fallbackToDestructiveMigration(dropAllTables = true)`.

`dropAllTables = true` preserves the previous behavior (drop all tables when a migration path
is missing), so runtime behavior is unchanged — this only removes the deprecation warning.
