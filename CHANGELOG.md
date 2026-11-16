# Changelog

All notable changes to this project are listed here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.1] - 2026-10-28

### Added
- Info menu: Home (i) opens an Info panel with the 2048 logo in the current theme variant, the app name, "Version 0.1.1" and "Author: Apocalypse03". Close by Close button, tap outside, or Back.
- Generated theme counterparts: light variants for Default, Olive, Orange, Blue and Green; dark variants for Light blue and Pink.
- Dark-mode support: the theme follows the system light or dark setting.
- Font Size setting: a "Font Size:" slider in Settings, 70 % to 130 % in steps of 5, default 100 %. It scales UI labels only; tile numbers and the game logo keep their size. Combined with the system font scale, the total is capped at 130 %. The value is saved between launches.

### Changed
- Unified theming: 17 themes, each with a light and a dark variant. Former dark themes (ids 17..26) merged into ids 7..16. Saved ids migrate automatically.
- Theme changes follow dark-mode toggles live, with a 200 ms animation and no restart. Only the theme id is stored.
- Display order of themes: Default, then the palette skins, then the built-in themes.
- Version bump: versionName 0.1.1, versionCode 2.
- Swipe input works anywhere in the window, not only on the board. A move fires after 20 dp of travel or on a short fast flick. A swipe that starts on Back, Undo, the timer or the palette button moves the board without triggering it. Swipes are off while the theme bar or a dialog is open.
- Game header logo, score, Best and timer are 1.5x larger.
- Tile animation: slide 120 ms with an ease-out curve and sub-pixel positions, then a 150 ms settle phase with an eased merge pop (1 → 1.14 → 1) and a spawn that fades and scales in (0.6 → 1). Times still scale with the Animation speed setting.
- Settings screen scrolls vertically; the back arrow stays pinned at the top.
- Cold-start splash logo is the in-app 2048 logo drawn in the selected theme's colours, replacing the launcher icon (Android 12+; themed colours need Android 13+).

### Fixed
- Cold start no longer flashes the Default theme colours before the saved theme appears: the splash background follows the selected theme and its light or dark variant (Android 13+). On older versions the splash stays in the Default colours.

## [0.1.0]

- Initial Android port (see process_summary.md).
