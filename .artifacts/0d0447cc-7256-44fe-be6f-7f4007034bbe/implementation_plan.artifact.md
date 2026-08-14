# Redesign History and Map Screens

Update the `HistoryScreen` and `SightingMapScreen` with a new, production-ready dark theme UI that includes advanced filtering, signal badges, and glassmorphism elements.

## Proposed Changes

### UI Components

#### [MODIFY] [Color.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/ui/theme/Color.kt)
- Add `TextMuted` color constant.

#### [MODIFY] [HistoryScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/ui/HistoryScreen.kt)
- Replace content with new redesigned UI.
- Implement `SubTab`, `FilterChipStyled`, `WifiRecordCard`, `BleRecordCard`, and a custom `FlowRow`.
- Integrate with `HistoryViewModel`.

#### [MODIFY] [SightingMapScreen.kt](file:///C:/Users/Damon/AndroidStudioProjects/WiFiscanning/app/src/main/java/com/damon/wifiaudit/map/SightingMapScreen.kt)
- Replace content with new redesigned UI.
- Implement `MapControlButton`, `LegendItem`, and keep/update the `createCircleMarker` helper.
- Integrate with `MapViewModel`.

## Verification Plan

### Manual Verification
- Deploy the app to a device or emulator.
- Navigate to the History screen and verify the new search bar, tabs, and record cards.
- Navigate to the Map screen and verify the session selector, map controls, and markers.
- Ensure all themes and colors match the requested design.
