# 2048 Android port — process summary

Date 2026-10-28. Current version 0.1.1 on branch `work/0.1.1`, cut from `main` at `e2120db` (merge of `android-port`, PR #1). 0.1.1 work is not committed yet. Nothing pushed.

## 1. Goal

- Android APK port of the Python game `2048_vertical.py` (Pygame). The brief lives in the untracked `MASTER PROJECT PROMPT.md` at the repo root.
- Architecture "A" (approved): `:game` = pure-Kotlin JVM module with the rules, golden-tested against the Python game; `:app` = Jetpack Compose UI.
- Layout uses a reference frame of 989×2048 "rp" units (`RefFrame`, `RefScope`, `refBox(cx, cy, w, h)`, `.rp`). The frame scales uniformly. Never stretch width and height independently.
- Package: `com.apocalypse03.game2048`.

## 2. Non-negotiable rules

- Do not rewrite game rules. Only additive code in `:game`.
- Never modify `2048_vertical.py` or `best_score.txt`.
- Leave the untracked `MASTER PROJECT PROMPT.md` and `Reference Pic/` alone. `Reference Pic/` is read-only source art.
- No unnecessary dependencies. Do not overwrite unrelated files.
- Record design decisions as numbered sections in `docs/DESIGN_SPEC.md`. Record status in `docs/PROJECT_STATUS.md`. Update `filemap.md` when adding or moving files.
- Ask before major or irreversible work. Commit only when asked. Never commit or push to `main`. Push only after the user decides.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (convention used for every commit on this branch).
- Never claim a test or build passed unless it was actually run in the session.
- User's working style: `/savvy-flow` orchestration (Opus plans and reviews; Haiku or Sonnet agents inspect, implement easy parts and test). Report before each commit.
- No `CLAUDE.md` exists in the repo (checked at root and in `android\`). These rules come from the brief.

## 3. Toolchain and commands

- Gradle 9.8.2 (wrapper `android\gradle\wrapper\gradle-wrapper.properties`).
- AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, Robolectric 4.17, JUnit 4.13.2. Source of truth: `android\gradle\libs.versions.toml`.
- `compileSdk` 37, `targetSdk` 36, `minSdk` 24, `versionName` 0.1.1, `versionCode` 2 (`android\app\build.gradle.kts`).
- JVM target 17 set by explicit `jvmTarget` and `compileOptions`, not `jvmToolchain(17)`. Builds run on Android Studio's JBR (JDK 25).
- AGP 9 built-in Kotlin is used. The `org.jetbrains.kotlin.android` plugin was removed.
- JaCoCo 0.8.14 on `:game`. Gate: LINE coverage ≥ 0.90, wired into `:game:check`.
- Release build: `isMinifyEnabled` and `isShrinkResources` on. Signed with the local debug key (sideload only, not for the Play Store). `proguard-rules.pro` holds only a comment.
- Full verify (PowerShell, from `android\`):
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --no-daemon --rerun-tasks :game:check :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`
- Last result (0.1.1 final verify, run 2026-10-28 with the command above): BUILD SUCCESSFUL; `:game` 78 tests, `:app` 385 tests, 0 failures; JaCoCo gate passed.
- APKs (from that run):
  - `android\app\build\outputs\apk\release\app-release.apk` (1,162,878 bytes, about 1.1 MB).
  - `android\app\build\outputs\apk\debug\app-debug.apk` (12,010,711 bytes, about 12 MB).
- Install guide: `INSTALL_ANDROID.md` at repo root.
- Emulator: SDK `C:\Users\cx09d\AppData\Local\Android\Sdk`. AVD `Medium_Phone_API_37.0` (1080×2400, density 420; AVD files present). adb at `C:\Users\cx09d\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- Screenshots go to the gitignored `android\app\build\device-shots\<name>\`.

## 4. File map (key files)

Repo root:
- `2048_vertical.py` — original Python game, behavioural oracle. Never edit.
- `best_score.txt` — best score written by the Python game. Never edit.
- `filemap.md` — file map (keep current).
- `INSTALL_ANDROID.md` — install guide.
- `CHANGELOG.md` — release notes (0.1.0, 0.1.1).
- `STYLE_ARCHITECTURE.md` — theme system design and counterpart-generation rule.
- `Master_0.1.1.md` — untracked brief for release 0.1.1.
- `MASTER PROJECT PROMPT.md` — untracked brief.
- `Reference Pic/` — untracked reference art: `2048_logo.svg`, `skin_palette_1..10.jpeg`, screenshots.
- `tools/golden/` — golden-test generator (`generate.py`), derived from the Python game.
- `docs/PROJECT_STATUS.md`, `docs/DESIGN_SPEC.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/DESIGN_AUDIT.md`, `docs/REPO_AUDIT.md`, `docs/REFERENCE_REPO_AUDIT.md`.

`android\` (Gradle root):
- `settings.gradle.kts` — modules `:app` and `:game`; repos google and mavenCentral only.
- `build.gradle.kts` — top-level plugin declarations (`apply false`).
- `gradle\libs.versions.toml` — version catalog.
- `gradle.properties` — JVM args, AndroidX flags.
- `game\build.gradle.kts` — `:game` build (jacoco, JVM 17).
- `app\build.gradle.kts` — `:app` build (Compose, release config).
- `app\proguard-rules.pro` — comment only.

`:game` sources, `android\game\src\main\kotlin\com\apocalypse03\game2048\game\`:
- `GameConfig.kt` — board sizes (`SIZE` = 4 default, `SIZES` = 4, 6).
- `Direction.kt` — LEFT / RIGHT / UP / DOWN.
- `Board.kt` — immutable N×N board with value equality; carries size.
- `Rules.kt` — move, hasLegalMoves, spawn, newGameBoard, `MoveResult`.
- `GameEngine.kt` — `GameEngine`, `GameState`, `GameStatus`, `UndoSnapshot`, `restore`. Also Princess Mode, undo history, hidden move counter.
- `MoveTrace.kt` — additive move trace used for animation timing.
- `:game` tests: `android\game\src\test\kotlin\...\game\` (GoldenTest, GameEngineTest, SpawnTest, SixBySixTest, PrincessModeTest, MoveCounterTest, MoveTraceTest, GameConfigTest). Golden data in `android\game\src\test\resources\golden\` (`moves.json`, `legal.json`, `spawn_stats.json`, `meta.json`).

`:app` sources, `android\app\src\main\kotlin\com\apocalypse03\game2048\`:
- `MainActivity.kt` — entry activity.
- `ui\AppRoot.kt` — screen switching, palette toggle and theme bar overlay, back handlers.
- `ui\AppViewModel.kt` — owns `GameEngine`; move, undo, new game, timer tap (5 taps = Princess Mode), persistence, win state.
- `ui\GameMode.kt` — Normal (4×4) and 2048⁺ (6×6).
- `ui\game\GameScreen.kt` — game screen and header (logo, score, Best, timer).
- `ui\game\BoardView.kt` — board drawing.
- `ui\game\GameInput.kt` — swipe and key input.
- `ui\game\ShowcaseBoard.kt` — static board used on Home.
- `ui\home\HomeScreen.kt` — Home (mode selector, Resume, New Game, Info button).
- `ui\info\InfoPanel.kt` — Info panel and `AUTHOR_HANDLE`.
- `ui\settings\SettingsScreen.kt` — animation speed, vibration on/off, intensity.
- `ui\results\ResultsScreen.kt` — top 20 per mode.
- `ui\components\RefFrame.kt` — `RefFrame`, `RefScope`, `refBox`, `TapTarget`, `RefIcon`, `RefText`.
- `ui\components\ThemeBar.kt` — `ThemeBar` (swipe strip) and `PaletteToggle`.
- `ui\components\Logo2048.kt`, `PillButton.kt`.
- `ui\theme\AppTheme.kt` — `AppTheme`, `ThemeSpec`, `ThemeVariant`, `getActiveVariant`, `AppThemes` (all skins, `resolve`, `migrateId`), `onAccent`, `animatedAppTheme`.
- `ui\data\SettingsStore.kt` — `SettingsStore`, `SharedPrefsSettingsStore`, `InMemorySettingsStore`.
- `ui\data\GameStore.kt` — `GameStore`, `SharedPrefsGameStore`, `InMemoryGameStore`.
- `ui\data\GameResult.kt` — Results record.
- `ui\timer\GameTimer.kt`, `TimerText.kt` — elapsed timer.
- `ui\haptics\Haptics.kt` — merge vibration.

`:app` resources, `android\app\src\main\res\`:
- `drawable\` — vector icons (`ic_undo`, `ic_settings`, `ic_list`, `ic_palette`, `ic_crown`, `ic_info`, `ic_cart`, `ic_arrow_back`, `ic_chevron_*`), `logo_2048_glyphs.xml`, `ic_launcher_foreground.xml`.
- `mipmap-anydpi-v26\ic_launcher.xml`, `ic_launcher_round.xml` — adaptive icons.
- `mipmap-{mdpi..xxxhdpi}\ic_launcher*.png` — legacy icons.
- `values\colors.xml`, `strings.xml`, `themes.xml`.
- `values-night\colors.xml` — dark window background.
- `assets\NOTICE.txt` (in `app\src\main\assets\`) — Material icon credits.

`:app` tests, `android\app\src\test\kotlin\com\apocalypse03\game2048\`:
- Root: `MainActivityTest.kt`, `MainActivityRecreateTest.kt`.
- `ui\AppViewModel*Test.kt` — view model unit tests (Mode, Nav, Persistence, Princess, Results, Resume, Settings, Timer, Win).
- `ui\*UiTest.kt` (incl. `ThemeVariantUiTest`, `InfoPanelUiTest`), `ui\ScreenshotTest.kt`, `ui\ThemeBar*Test.kt`, `ui\WindowFitTest.kt`, `ui\FontScaleTest.kt` — Robolectric UI tests.
- `ui\theme\PaletteThemeTest.kt`, `ThemeContrastTest.kt`, `TileTextContrastTest.kt`.
- `ui\data\*Test.kt`, `ui\timer\*Test.kt`, `ui\game\SwipeDirectionTest.kt`.

## 5. Features done (chronological, from git log)

- `5126e9a` Port 2048 rules to Kotlin `:game` module, golden-tested.
- `d1d6aa6` Record answers to Q2, Q3, Q7 for the P4 layout.
- `923a816` P4 visual prototype: Home, Game shell, themes, theme bar.
- `23a94f5` Wire GameEngine into Game screen with swipe, keys and undo (P5).
- `3f477a1` Responsive pass: font scale, swatch hit areas, window fit (P6).
- `555366f` Game timer with lifecycle pause and resume (P7).
- `ada8918` Princess Mode easter egg (P7b).
- `711d0fe` Persist settings and game; Resume and new-game confirmation (P8).
- `bd845ce` Narrow timer recomposition and fix review findings (P9).
- `9b10dc6` Minified, debug-signed release APK (P10).
- `3e81c64` Normal / 2048⁺ (6×6) game modes.
- `cd2c695` You Win! screen and hidden move counter (Q5).
- `3af3bbd` Settings and Results screens, tile animation, merge vibration.
- `bf706ae` Palette skins with dark variants, contrast fix, header layout.
- `6413c96` 2048 logo as launcher icon; install guide.
- `e2120db` Merge PR #1 (`android-port`) into `main`.
- 0.1.1 (uncommitted on `work/0.1.1`): unified light/dark theming, Info panel, version bump, changelog. See section 11.

## 6. Theme system (how skins work)

- `AppTheme(id, name, background, accent, onBackground, ramp)`. The tile ramp has 12 entries (`RAMP_SIZE`).
- `isLight` = background luminance > 0.5.
- Light themes: `boardBg` = mix(background, accent, 0.50); `emptyCell` = background.
- Dark themes: `boardBg` = mix(background, accent, 0.16); `emptyCell` = mix(background, accent, 0.26).
- `onAccent`: background if contrast(accent, background) ≥ 3. Otherwise `#1D1D1D` or white, whichever contrasts more.
- Tile text: dark or white, whichever contrasts more with the tile (`tileTextColor`).
- Palette skins, ids 7–16 (`AppThemes.fromPalette`): built from `Reference Pic/skin_palette_1..10.jpeg`. Background = lightest colour, accent = darkest. Names: Lavender, Lilac, Orchid, Cream, Periwinkle, Slate, Rose, Sky, Cyan, Red.
- Unified theming (0.1.1): each theme is one `ThemeSpec(id, name, light, dark)` in `ui\theme\AppTheme.kt`. Dark variants come from `AppThemes.fromPaletteDark` (background = mix(darkest, black, 0.82)). Old dark ids 17–26 merged into ids 7–16. Saved ids migrate and are written back.
- Display order in `AppThemes.all`: Default (0), palettes 7–16 (Lavender ... Red), then built-ins 1–6.
- Built-ins, ids 0–6: Default (0, the default), Olive (1), Orange (2), Blue (3), Light blue (4), Green (5), Pink (6). Unknown saved ids fall back to 0.
- Generated counterparts: light for Default, Olive, Orange, Blue, Green; dark for Light blue, Pink. Rule in `STYLE_ARCHITECTURE.md` ("Counterpart generation").
- System-following: `getActiveVariant()` (isSystemInDarkTheme) is the single resolver. Only the theme id is stored. First launch: Default in the system's variant. `MainActivity` declares `configChanges="uiMode"`, so a dark-mode toggle swaps the theme live (200 ms, no restart).
- Total: 17 themes, one swatch each.
- Theme bar (`ThemeBar.kt`): horizontal swipe strip, pitch 115 rp, first swatch centre x 84.5, y 104.5, visible span x 27..832. Snaps to whole swatches. Swatch = lightest colour as outer circle, darkest as dot.
- `PaletteToggle` (in `ThemeBar.kt`): Ø122 at (893, 97) when open, Ø69 at (898, 92) when closed.
- Game header (`GameScreen.kt`): logo 83 rp at (494, 92); score at (494, 222); "Best" at (494, 266); timer at (494, 308). Tapping the timer 5 times in a burst enters Princess Mode.
- Theme changes animate (`animatedAppTheme`).

## 7. Launcher icon

- Adaptive icon: background `@color/ic_launcher_background` = `#867CA3`. Foreground `drawable/ic_launcher_foreground.xml`: 108 dp canvas, glyph path from `logo_2048_glyphs.xml`, translate 18, scale 0.24, opaque white. Monochrome layer uses the same drawable.
- `ic_launcher_round` has the same content.
- Legacy PNGs: `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`, square and round, 48 to 192 px. Square icon is transparent outside rounded corners. Round icon is a purple circle.
- Spec: `docs/DESIGN_SPEC.md` §18.
- PNGs were rendered from the SVG in headless Edge at 1024 px, then downscaled with Python PIL (premultiplied RGBa, BOX filter). The scripts were in a temp scratchpad and are not kept. To regenerate, repeat that method.

## 8. Known issues and accepted weak spots

From the brief and `docs/PROJECT_STATUS.md` (not re-tested for this summary):
- Cream tiles 2 and 4 are barely distinguishable. Cream selected ring is faint. Rose and Pink tiles 2 and 4 are faint. Orchid tile 2 is distinct by hue only.
- ThemeBar: scroll state is not re-keyed on density change. Max scroll is not an exact pitch multiple (under 1 px). Off-screen swatches stay in the semantics tree. `isLight` flips during the theme animation.
- Home mode arrow is about 12 px off.
- `docs/IMPLEMENTATION_PLAN.md` still has old "difficulty" wording.
- `createAndroidComposeRule` is deprecated (`MainActivityTest.kt:16`).
- `__pycache__/2048_vertical.cpython-314.pyc` is tracked in git.
- Only one saved game.
- After rotation the last move re-animates.
- Results chevrons are not pink.
- Shop nav icon is inert (highlight only).
- Icon not checked on non-circular launcher masks or on a real device.
- Release is debug-signed. It cannot go to the Play Store.
- Not run on a physical device. Emulator checks only.
- 0.1.1: Cream's accent is below 3:1 contrast (pre-existing). Accent contrast is tested only for generated counterparts.
- 0.1.1: new Kotlin files use LF line endings; the rest of the repo uses CRLF.
- 0.1.1: Info panel has no "More games" or privacy links (the reference image has them; the brief asked only for icon, version, author).

## 9. Possible next steps

- Real release keystore and signing config.
- Physical-device test.
- Make Shop functional (Info done in 0.1.1).
- Cream contrast polish.
- Fix the minor notes in section 8.

## 10. How to resume in a new session

- Start by reading, in order: `process_summary.md`, `filemap.md`, the relevant `docs/DESIGN_SPEC.md` section, and `docs/PROJECT_STATUS.md`.
- Check `git status` and `git log --oneline -5`.
- Run the full verify command (section 3) before and after changes.
- Report before committing. Commit only when asked. Push only after the user decides.

## 11. Release 0.1.1

- Branch `work/0.1.1`, from `main` (`e2120db`). Version bump: `versionName` 0.1.1, `versionCode` 2 (`android\app\build.gradle.kts`). BuildConfig is enabled.
- Unified theming: 17 themes, each `ThemeSpec(id, name, light, dark)` in `ui\theme\AppTheme.kt`. Old dark themes 17–26 merged into 7–16. Saved ids migrate and are written back.
- Generated counterparts: light for Default, Olive, Orange, Blue, Green; dark for Light blue, Pink. Rule in `STYLE_ARCHITECTURE.md`; theme table in `docs/DESIGN_SPEC.md` §3.
- System-following: `getActiveVariant()` is the single resolver. `MainActivity` declares `configChanges="uiMode"`, so a dark-mode toggle swaps the theme and the theme bar live (200 ms, no restart). Only the theme id is stored. Window background: `values\colors.xml` (light), `values-night\colors.xml` (dark).
- Info menu: Home (i) opens `ui\info\InfoPanel.kt`. It shows the 2048 logo in the current variant, "2048", "Version 0.1.1" (from `BuildConfig.VERSION_NAME`), "Author: Apocalypse03" (`AUTHOR_HANDLE`), and a Close pill (spec: `docs/DESIGN_SPEC.md` §19). Dismiss by Close, tap outside, or Back. It swaps variant live while open.
- Tests: `:app` now includes `ThemeVariantUiTest` (7) and `InfoPanelUiTest` (7). Final verify 2026-10-28 (full command in §3, `--rerun-tasks`, incl. release): BUILD SUCCESSFUL; `:game` 78 tests, `:app` 385 tests, 0 failures; JaCoCo gate passed. Release APK 1,162,878 bytes.
- Changelog: `CHANGELOG.md` at repo root.
