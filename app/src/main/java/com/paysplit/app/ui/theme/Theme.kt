package com.paysplit.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.paysplit.app.data.model.ThemeMode

/** true — сейчас активна тёмная тема (учитывает выбор пользователя). */
val LocalIsDarkTheme: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Color(0xFF4B635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DE),
    onSecondaryContainer = Color(0xFF072018),
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    background = NeutralLightBackground,
    onBackground = NeutralLightOnSurface,
    surface = NeutralLightSurface,
    onSurface = NeutralLightOnSurface,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightOnSurfaceVariant,
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C3),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorLightContainer,
    onErrorContainer = OnErrorLightContainer,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Color(0xFFB1CCC1),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF334B43),
    onSecondaryContainer = Color(0xFFCDE9DE),
    tertiary = Teal80,
    onTertiary = Teal20,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    background = NeutralDarkBackground,
    onBackground = NeutralDarkOnSurface,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkOnSurface,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkOnSurfaceVariant,
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorDarkContainer,
    onErrorContainer = OnErrorDarkContainer,
)

/** Цвета доходов/расходов с учётом текущей темы. */
object PayColors {
    val income: Color
        @Composable get() = if (LocalIsDarkTheme.current) SemanticColors.IncomeDark
        else SemanticColors.IncomeLight

    val expense: Color
        @Composable get() = if (LocalIsDarkTheme.current) SemanticColors.ExpenseDark
        else SemanticColors.ExpenseLight

    val warning: Color
        @Composable get() = if (LocalIsDarkTheme.current) SemanticColors.WarningDark
        else SemanticColors.WarningLight

    val neutral: Color
        @Composable get() = if (LocalIsDarkTheme.current) SemanticColors.NeutralDark
        else SemanticColors.NeutralLight

    /** Цвет статуса категории. */
    fun ofStatus(status: com.paysplit.app.data.model.CategoryStatus): Color = when (status) {
        com.paysplit.app.data.model.CategoryStatus.FULL -> income
        com.paysplit.app.data.model.CategoryStatus.PARTIAL -> warning
        com.paysplit.app.data.model.CategoryStatus.NONE -> neutral
        com.paysplit.app.data.model.CategoryStatus.OVERSPEND -> expense
    }
}

@Composable
fun PaySplitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PaySplitTypography,
            content = content,
        )
    }
}
