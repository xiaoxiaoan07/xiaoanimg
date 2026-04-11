import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import ui.MainScreen

@Composable
fun App(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    MiuixTheme(
        colors = if (isDarkTheme) darkColorScheme() else lightColorScheme(),
        smoothRounding = false,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
        ) {
            MainScreen()
        }
    }
}
