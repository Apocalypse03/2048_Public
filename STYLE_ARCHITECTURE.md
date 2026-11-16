# Style architecture

How themes are defined, which variant is shown, and how the choice is stored. Colour rules and
values: `docs/DESIGN_SPEC.md` §3; motion: §9.

## Schema (`ui/theme/AppTheme.kt`)

- `ThemeSpec(id, name, light, dark)`: one selectable theme. `init` requires `light.isLight`,
  `!dark.isLight`, and both variants carry the spec's id and name. `variant(v)` returns one.
- `AppTheme(id, name, background, accent, onBackground, ramp)`: one variant's palette. Derived
  colours (`boardBg`, `emptyCell`, `onAccent`, tile text) follow `isLight` as before.
- `enum class ThemeVariant { LIGHT, DARK }`.
- `AppThemes.all`: 17 specs. `byId(id)` (after `migrateId`, unknown → Default),
  `resolve(id, variant)`, `Default` = spec 0.
- `LocalAppTheme` default = `AppThemes.Default.dark`; only used outside `AppRoot`.

## Single resolver

`@Composable getActiveVariant()` returns `DARK` when `isSystemInDarkTheme()`, else `LIGHT`. It is
the only place that decides the variant. Consumers call it once and resolve through it:
`AppRoot` (the composition theme), `ThemeBar` (swatch colours), `MainActivity` (system bar icon
style). No component picks or hard-codes a variant.

## Live switching

1. The user toggles the system dark mode (uiMode changes).
2. `MainActivity` declares `android:configChanges="uiMode"`, so it is not recreated.
3. The Compose view receives the new configuration; `isSystemInDarkTheme()` recomposes.
4. `AppRoot` resolves `AppThemes.resolve(vm.themeId, getActiveVariant())`.
5. `animatedAppTheme` cross-fades the 15 colours over 200 ms, as for a theme change.

Before Compose draws, the window shows `@color/bg`: Default light (`values/colors.xml`) or
Default dark (`values-night/colors.xml`).

## Persistence and migration

- Only the theme id is stored (`SettingsStore.themeId`, SharedPreferences `theme_id`), never the
  variant. First launch: id 0 (Default) in the system's variant.
- Read: raw id → `AppThemes.migrateId` (17..26 → id − 10, the old separate dark themes) →
  unknown → 0. A migrated value is written back once.

## Theme order (theme bar, same in both modes)

Default (0), Lavender (7), Lilac, Orchid, Cream, Periwinkle, Slate, Rose, Sky, Cyan, Red (16),
Olive (1), Orange (2), Blue (3), Light blue (4), Green (5), Pink (6).

## Variants

- Palettes 7..16: light = `fromPalette`, dark = `fromPaletteDark` (same id and name).
- Built-ins 0..6 keep their original variant (dark for 0, 1, 2, 3, 5; light for 4, 6). The other
  variant is a generated counterpart (rule below), with the default `classicRamp`.

## Counterpart generation

- Light from dark: bg = mix(accent, #FFFFFF, 0.90) per sRGB channel; accent kept, but while WCAG contrast(accent, bg) < 3.0 lower HSL lightness by 0.02 (hue and saturation kept); onBackground #1D1D1D; ramp = classicRamp.
- Dark from light: bg = mix(accent, #000000, 0.82); accent kept, but while contrast < 3.0 raise HSL lightness by 0.02; onBackground #FBFBFB; ramp = classicRamp.
- Targets: text on background ≥ 4.5 (WCAG AA), accent vs background ≥ 3.0, tile text ≥ 3.0 (enforced by tests).

| id | name | new variant | background | accent | onBackground |
|---|---|---|---|---|---|
| 0 | Default | light | #FCF4F8 | #D46598 | #1D1D1D |
| 1 | Olive | light | #F5F4EF | #988C62 | #1D1D1D |
| 2 | Orange | light | #FBF2EB | #CB762C | #1D1D1D |
| 3 | Blue | light | #ECF5FA | #3192C7 | #1D1D1D |
| 4 | Light blue | dark | #171F23 | #7FABC4 | #FBFBFB |
| 5 | Green | light | #EBF5EB | #359734 | #1D1D1D |
| 6 | Pink | dark | #271B1D | #DA96A1 | #FBFBFB |
