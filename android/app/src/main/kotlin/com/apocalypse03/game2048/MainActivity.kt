package com.apocalypse03.game2048

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.apocalypse03.game2048.ui.theme.AppThemes
import com.apocalypse03.game2048.ui.theme.getActiveVariant
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apocalypse03.game2048.ui.AppRoot
import com.apocalypse03.game2048.ui.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
            val variant = getActiveVariant()
            LaunchedEffect(vm.themeId, variant) {
                // Dark icons on a light variant (background luminance > 0.5), light icons otherwise.
                val lightTheme = AppThemes.resolve(vm.themeId, variant).isLight
                val style = if (lightTheme) {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(vm.themeId) {
                // Next cold start: the system splash window uses the selected theme's background.
                // The system persists it by style name; below API 33 the splash stays Default.
                if (Build.VERSION.SDK_INT >= 33) {
                    splashScreen.setSplashScreenTheme(SplashThemes.styleFor(vm.themeId))
                }
            }
            AppRoot(vm)
        }
    }
}
