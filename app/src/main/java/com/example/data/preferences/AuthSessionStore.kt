package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.juthoorAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "juthoor_auth_session")

class AuthSessionStore(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("auth_user_id")
        val EMAIL = stringPreferencesKey("auth_email")
        val DISPLAY_NAME = stringPreferencesKey("auth_display_name")
        val ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        val REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        val EXPIRES_AT = longPreferencesKey("auth_expires_at")
        val ACTIVE_FAMILY_ID = stringPreferencesKey("auth_active_family_id")
    }

    val sessionFlow: Flow<AuthSession?> = context.juthoorAuthDataStore.data.map { prefs ->
        val userId = prefs[Keys.USER_ID]
        val rawAccessToken = prefs[Keys.ACCESS_TOKEN]
        val accessToken = rawAccessToken?.let { SessionTokenCipher.decrypt(it) }
        val email = prefs[Keys.EMAIL]
        if (userId.isNullOrBlank() || accessToken.isNullOrBlank() || email.isNullOrBlank()) {
            null
        } else {
            val refreshToken = prefs[Keys.REFRESH_TOKEN]
                ?.let { SessionTokenCipher.decrypt(it) }
                .orEmpty()
            AuthSession(
                userId = userId,
                email = email,
                displayName = prefs[Keys.DISPLAY_NAME]?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = prefs[Keys.EXPIRES_AT] ?: 0L,
                activeFamilyId = prefs[Keys.ACTIVE_FAMILY_ID]?.takeIf { it.isNotBlank() },
            )
        }
    }

    val activeFamilyIdFlow: Flow<String?> = context.juthoorAuthDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_FAMILY_ID]?.takeIf { it.isNotBlank() }
    }

    suspend fun getSessionOnce(): AuthSession? = sessionFlow.first()

    suspend fun saveSession(session: AuthSession) {
        val encAccess = SessionTokenCipher.encrypt(session.accessToken)
        val encRefresh = SessionTokenCipher.encrypt(session.refreshToken)
        context.juthoorAuthDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
            prefs[Keys.DISPLAY_NAME] = session.displayName
            prefs[Keys.ACCESS_TOKEN] = encAccess
            prefs[Keys.REFRESH_TOKEN] = encRefresh
            prefs[Keys.EXPIRES_AT] = session.expiresAtMillis
            if (!session.activeFamilyId.isNullOrBlank()) {
                prefs[Keys.ACTIVE_FAMILY_ID] = session.activeFamilyId
            }
        }
    }

    suspend fun updateActiveFamilyId(familyId: String?) {
        context.juthoorAuthDataStore.edit { prefs ->
            if (familyId.isNullOrBlank()) {
                prefs.remove(Keys.ACTIVE_FAMILY_ID)
            } else {
                prefs[Keys.ACTIVE_FAMILY_ID] = familyId
            }
        }
    }

    suspend fun clearSession() {
        context.juthoorAuthDataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.EXPIRES_AT)
        }
    }
}
