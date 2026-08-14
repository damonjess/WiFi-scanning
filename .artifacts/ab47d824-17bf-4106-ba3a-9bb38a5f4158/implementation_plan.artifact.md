# Unified Navigation and Scan UI Refactor

This plan refactors the application to use a single bottom navigation bar and integrates WiFi and BLE scanning into a unified "Scan" tab. It also cleans up the map screen and removes redundant UI components.

## Proposed Changes

### UI & Navigation Cleanup

#### [DELETE] [BleRadarScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/ui/BleRadarScreen.kt)
Remove the redundant BLE Radar screen which had its own internal navigation.

#### [NEW] [UnifiedScanScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/ui/UnifiedScanScreen.kt)
Create a new screen that combines WiFi and BLE scanning results. This replaces the old `MainScanScreen.kt`.
- Includes a toggle between "Nearby WiFi" and "BLE Radar".
- Integrates `ScanControlCard`, `GradientStatCard`, `GpsCard`, `WifiScanRow`, and `BleDeviceRow`.
- Uses `WardrivingStatusViewModel` and `BleScanViewModel`.

#### [DELETE] [MainScanScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/ui/MainScanScreen.kt)
Deleted in favor of the new `UnifiedScanScreen.kt`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/MainActivity.kt)
- Update `AppRoot` to use the single bottom navigation bar with 4 tabs: Scan, History, Network, Map.
- Switch the first tab from `BleRadarScreen` to `UnifiedScanScreen`.
- Implement full-screen `DeviceDetailScreen` overlay based on `detailMac` state.
- Update `NavigationBar` styling to match the dark theme.

### Map Improvements

#### [MODIFY] [SightingMapScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/map/SightingMapScreen.kt)
- Update with the user-provided implementation for better lifecycle management (`onResume`, `onPause`, `onDetach`).
- Simplify markers and clustering.
- Ensure dark mode consistency.
- Add session selector and floating controls as requested.

## Verification Plan

### Automated Tests
- Run existing unit tests to ensure no regressions in data layer.

### Manual Verification
- Deploy the app and verify the bottom navigation has only 4 tabs.
- Verify the "Scan" tab correctly toggles between WiFi and BLE lists.
- Start a scan and verify stats update for both WiFi and BLE.
- Tapping a BLE device should open the `DeviceDetailScreen` full-screen.
- Verify the Map screen renders and allows session selection.
- Verify the Map screen handles lifecycle (screen rotation, backgrounding) correctly.
