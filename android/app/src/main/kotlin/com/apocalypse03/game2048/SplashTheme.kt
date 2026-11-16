package com.apocalypse03.game2048

import androidx.annotation.StyleRes

/**
 * Maps a theme id to its static splash style (`Theme.Game2048.Splash<id>`).
 *
 * On a cold start the system draws the splash window before the app process runs, so the only
 * way to show the selected theme's background is `SplashScreen.setSplashScreenTheme` (API 33+),
 * which needs a style resource. The system persists the chosen style by resource NAME, so the
 * style names must stay stable across versions. The colours behind them are kept in sync with
 * `AppThemes` by `SplashThemeTest`.
 */
internal object SplashThemes {
    private val styles = intArrayOf(
        R.style.Theme_Game2048_Splash0,
        R.style.Theme_Game2048_Splash1,
        R.style.Theme_Game2048_Splash2,
        R.style.Theme_Game2048_Splash3,
        R.style.Theme_Game2048_Splash4,
        R.style.Theme_Game2048_Splash5,
        R.style.Theme_Game2048_Splash6,
        R.style.Theme_Game2048_Splash7,
        R.style.Theme_Game2048_Splash8,
        R.style.Theme_Game2048_Splash9,
        R.style.Theme_Game2048_Splash10,
        R.style.Theme_Game2048_Splash11,
        R.style.Theme_Game2048_Splash12,
        R.style.Theme_Game2048_Splash13,
        R.style.Theme_Game2048_Splash14,
        R.style.Theme_Game2048_Splash15,
        R.style.Theme_Game2048_Splash16,
    )

    /** The splash style for [themeId]; an unknown id falls back to the Default theme (id 0). */
    @StyleRes
    fun styleFor(themeId: Int): Int = styles.getOrElse(themeId) { styles[0] }
}
