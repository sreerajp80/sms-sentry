# Fix deprecated `fallbackToDestructiveMigration()` call

**Status:** completed

## Issue

`SmsDatabase.kt:85` calls the no-arg `RoomDatabase.Builder.fallbackToDestructiveMigration()`,
which is deprecated in the current Room version:

> 'fun fallbackToDestructiveMigration(): RoomDatabase.Builder<SmsDatabase>' is deprecated.
> Replace by overloaded version with parameter to indicate if all tables should be dropped or not.

The deprecated no-arg overload historically dropped **all** tables on a failed/missing
migration. Room replaced it with `fallbackToDestructiveMigration(dropAllTables: Boolean)` to
make that behavior explicit.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt`

## Fix

Replace:

```kotlin
.fallbackToDestructiveMigration()
```

with:

```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

`dropAllTables = true` preserves the existing (pre-deprecation) behavior — drop every table
when a migration path is missing — so runtime behavior is unchanged; only the deprecated API
call is removed.

## Verification

- Compile the module to confirm the deprecation warning is gone and no new errors appear.
