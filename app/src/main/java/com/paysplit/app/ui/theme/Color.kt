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

    /** Палитра для секторов диаграмм. */
    val chartPalette = listOf(
        Color(0xFF2E7D5B),
        Color(0xFF4C9F70),
        Color(0xFF7BC796),
        Color(0xFFB8860B),
        Color(0xFFE0A458),
        Color(0xFF3D7EA6),
        Color(0xFF6BA8C9),
        Color(0xFF9C6ADE),
        Color(0xFFC77DBB),
        Color(0xFFD96A6A),
        Color(0xFF8D9E63),
        Color(0xFF607D8B),
    )
}
