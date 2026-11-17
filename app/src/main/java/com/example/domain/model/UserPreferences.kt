package com.example.domain.model

enum class ThemeMode(val arabicLabel: String) {
    SYSTEM("تلقائي (حسب النظام)"),
    LIGHT("الوضع الفاتح"),
    DARK("الوضع الداكن"),
}

data class UserPreferences(
    val familyTitle: String = "عائلة جِذور",
    val familyMotto: String = "نوثّق الأصول، ونحفظ امتداد الأجيال",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHijriDates: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null,
)
