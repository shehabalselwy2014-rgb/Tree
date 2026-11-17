package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.juthoorDataStore: DataStore<Preferences> by preferencesDataStore(name = "juthoor_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val FAMILY_TITLE = stringPreferencesKey("family_title")
        val FAMILY_MOTTO = stringPreferencesKey("family_motto")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_HIJRI_DATES = booleanPreferencesKey("show_hijri_dates")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
    }

    val preferencesFlow: Flow<UserPreferences> = context.juthoorDataStore.data.map { prefs ->
        val themeModeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)
        val salt = prefs[Keys.PIN_SALT]
        val hash = prefs[Keys.PIN_HASH]
        val pinEnabled = (prefs[Keys.PIN_ENABLED] ?: false) && !salt.isNullOrBlank() && !hash.isNullOrBlank()
        UserPreferences(
            familyTitle = prefs[Keys.FAMILY_TITLE]?.takeIf { it.isNotBlank() } ?: "عائلة جِذور",
            familyMotto = prefs[Keys.FAMILY_MOTTO]?.takeIf { it.isNotBlank() }
                ?: "نوثّق الأصول، ونحفظ امتداد الأجيال",
            themeMode = themeMode,
            showHijriDates = prefs[Keys.SHOW_HIJRI_DATES] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            pinEnabled = pinEnabled,
            biometricEnabled = pinEnabled && (prefs[Keys.BIOMETRIC_ENABLED] ?: false),
            pinSalt = salt,
            pinHash = hash,
        )
    }

    suspend fun updateFamilyIdentity(title: String, motto: String) {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.FAMILY_TITLE] = title.trim().ifBlank { "عائلة جِذور" }
            prefs[Keys.FAMILY_MOTTO] = motto.trim().ifBlank { "نوثّق الأصول، ونحفظ امتداد الأجيال" }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setShowHijriDates(enabled: Boolean) {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.SHOW_HIJRI_DATES] = enabled
        }
    }

    suspend fun markOnboardingCompleted() {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setPinLock(salt: String, hash: String, enableBiometric: Boolean? = null) {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_ENABLED] = true
            if (enableBiometric != null) {
                prefs[Keys.BIOMETRIC_ENABLED] = enableBiometric
            }
        }
    }

    suspend fun clearPinLock() {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.PIN_HASH)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.juthoorDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }
}
