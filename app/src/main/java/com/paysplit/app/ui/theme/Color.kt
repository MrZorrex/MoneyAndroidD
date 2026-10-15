package com.paysplit.app.ui.theme

import androidx.compose.ui.graphics.Color

// --- Фирменная палитра PaySplit (зелёный «финансовый» акцент) ---
val Green10 = Color(0xFF00251C)
val Green20 = Color(0xFF00382A)
val Green30 = Color(0xFF00513C)
val Green40 = Color(0xFF0F6B52)
val Green80 = Color(0xFF8BD6B4)
val Green90 = Color(0xFFA7F3D0)

val Teal10 = Color(0xFF001F26)
val Teal20 = Color(0xFF00363F)
val Teal30 = Color(0xFF004E5A)
val Teal40 = Color(0xFF136876)
val Teal80 = Color(0xFFA5CDDC)
val Teal90 = Color(0xFFC0E9FA)

val Sand40 = Color(0xFF6B5E2F)
val Sand80 = Color(0xFFDBC88A)
val Sand90 = Color(0xFFF8E4A8)

val NeutralLightBackground = Color(0xFFF5FBF7)
val NeutralLightSurface = Color(0xFFF5FBF7)
val NeutralLightSurfaceVariant = Color(0xFFDBE5DE)
val NeutralLightOnSurface = Color(0xFF171D1A)
val NeutralLightOnSurfaceVariant = Color(0xFF3F4945)

val NeutralDarkBackground = Color(0xFF0F1512)
val NeutralDarkSurface = Color(0xFF0F1512)
val NeutralDarkSurfaceVariant = Color(0xFF3F4945)
val NeutralDarkOnSurface = Color(0xFFDEE4DF)
val NeutralDarkOnSurfaceVariant = Color(0xFFBEC9C3)

val ErrorLight = Color(0xFFBA1A1A)
val ErrorLightContainer = Color(0xFFFFDAD6)
val OnErrorLightContainer = Color(0xFF410002)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorDarkContainer = Color(0xFF93000A)
val OnErrorDarkContainer = Color(0xFFFFDAD6)

// --- Семантические цвета (доходы/расходы/статусы) ---
object SemanticColors {
    val IncomeLight = Color(0xFF1B7F3B)
    val IncomeDark = Color(0xFF7BD88F)
    val ExpenseLight = Color(0xFFC62828)
    val ExpenseDark = Color(0xFFFF8A80)
    val WarningLight = Color(0xFFB8860B)
    val WarningDark = Color(0xFFFFD54F)
    val NeutralLight = Color(0xFF9E9E9E)
    val NeutralDark = Color(0xFF757575)

    /**
     * Палитра для секторов диаграмм.
     * Цвета идут контрастными парами (зелёный → синий → оранжевый …),
     * чтобы соседние секторы всегда заметно отличались друг от друга
     * и на тёмной, и на светлой теме.
     */
    val chartPalette = listOf(
        Color(0xFF43A047),
        Color(0xFF1E88E5),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
        Color(0xFFE53935),
        Color(0xFF00ACC1),
        Color(0xFFC0CA33),
        Color(0xFFEC407A),
        Color(0xFF6D4C41),
        Color(0xFF5C6BC0),
        Color(0xFF26A69A),
        Color(0xFF78909C),
    )
}
