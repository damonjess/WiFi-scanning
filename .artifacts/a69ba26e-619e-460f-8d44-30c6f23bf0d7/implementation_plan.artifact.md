# Implementation Plan - Fix Build, Crashes, and UI Issues

This plan addresses several critical issues including build failures due to Gradle/AGP mismatch, app crashes on launch and when viewing device details, and incorrect UI themes.

## User Review Required

> [!IMPORTANT]
> **Room Database Migration**: I will enable `fallbackToDestructiveMigration()` to resolve the identity-hash mismatch for schemas 9 and 10. This will result in data loss if the user has an existing database with those versions but no migration paths. Given the project context, this is the safest way to ensure the app can boot.

> [!WARNING]
> **Keystore Corruption**: If the EncryptedSharedPreferences are found to be corrupt, the database passphrase will be reset, which will cause the loss of the encrypted database. This is necessary to recover from a crash state where the app cannot open the database at all.

## Proposed Changes

### Build and Environment

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/gradle/libs.versions.toml)
- Update `agp` version from `8.7.3` to `8.9.0`.

#### [MODIFY] [themes.xml](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/res/values/themes.xml) and [values-night/themes.xml](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/res/values-night/themes.xml)
- Change parent theme to `Theme.Material3.DayNight.NoActionBar`.

---

### UI & Crashes

#### [MODIFY] [DeviceDetailScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/ui/DeviceDetailScreen.kt)
- Remove `MapView` from `DeviceHeader` to prevent `IllegalArgumentException` (0x0 size) when placed inside `LazyColumn`.
- Reformat `DeviceHeader` into a static info card showing location coordinates and classification.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/MainActivity.kt)
- Move all initialization logic (OUI, database, GATT seeding) to `Dispatchers.IO` to prevent blocking the main thread and potential ANRs/crashes.
- Wrap initialization in a try-catch block.

#### [MODIFY] [OuiVendorLookup.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/vendor/OuiVendorLookup.kt)
- Wrap asset loading in try-catch to handle missing or corrupt files gracefully.
- Ensure `initialize` runs on `Dispatchers.IO`.

---

### Database & Security

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/data/AppDatabase.kt)
- Add `fallbackToDestructiveMigration()` to the database builder.
- Add error handling for SQLCipher library loading and passphrase retrieval.

#### [MODIFY] [SecureKeyProvider.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/data/SecureKeyProvider.kt)
- Wrap `EncryptedSharedPreferences` creation in a try-catch.
- If corruption is detected, clear the shared preferences file to allow the app to generate a new key and boot.

## Verification Plan

### Automated Tests
- Run `OuiImportTest.kt` to verify OUI lookup logic.
- Run `gradlew assembleDebug` to verify build fix.

### Manual Verification
1. Launch the app and verify it no longer crashes on the permission gate or scan tab.
2. Tap a Wi-Fi or BLE row and verify `DeviceDetailScreen` loads without crashing and displays the location card.
3. Verify the theme no longer shows an unwanted ActionBar.
