package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.ZipBackupManager
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.AuthSessionStore
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.SessionTokenCipher
import com.example.data.remote.HttpSupabaseGateway
import com.example.data.remote.SupabaseConfig
import com.example.data.repository.RoomAndSupabaseAuthRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.AuthSession
import com.example.domain.security.PinSecurityHelper
import com.example.presentation.JuthoorViewModel
import com.example.presentation.cloud.FamilyAccountScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseNineSixSecurityHardeningTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `pin security helper uses pbkdf2 120k iterations and upgrades legacy sha256 transparently`() = runTest {
        val salt = PinSecurityHelper.generateSalt()
        val pbkdf2Hash = PinSecurityHelper.hashPin("4826", salt)
        assertTrue("Hash should use pbkdf2_sha256$120000$", pbkdf2Hash.startsWith("pbkdf2_sha256$120000$"))
        assertTrue(PinSecurityHelper.verifyPin("4826", salt, pbkdf2Hash))
        assertFalse(PinSecurityHelper.verifyPin("0000", salt, pbkdf2Hash))
        assertFalse("Current PBKDF2 hash should not need rehash", PinSecurityHelper.needsRehash(pbkdf2Hash))

        // Construct a legacy SHA-256 hash using the prior format
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(salt.toByteArray(Charsets.UTF_8))
            update(":juthoor_pin:".toByteArray(Charsets.UTF_8))
            update("4826".toByteArray(Charsets.UTF_8))
        }.digest()
        val legacyHex = digest.joinToString("") { "%02x".format(it) }

        assertTrue("Legacy SHA-256 hash must still verify", PinSecurityHelper.verifyPin("4826", salt, legacyHex))
        assertTrue("Legacy SHA-256 hash must be flagged for transparent upgrade", PinSecurityHelper.needsRehash(legacyHex))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomGenealogyRepository(db)
            val media = LocalMediaStorage(context)
            prefs.setPinLock(salt, legacyHex, enableBiometric = false)

            val viewModel = JuthoorViewModel(repo, prefs, media)
            // Wait for DataStore -> viewModelScope initial preferences emission
            var unlocked = false
            val unlockDeadline = System.currentTimeMillis() + 5_000L
            while (!unlocked && System.currentTimeMillis() < unlockDeadline) {
                shadowOf(Looper.getMainLooper()).idle()
                unlocked = viewModel.verifyAndUnlockWithPin("4826")
                if (!unlocked) Thread.sleep(25)
            }
            assertTrue("Legacy SHA-256 PIN should unlock", unlocked)

            // Wait for asynchronous viewModelScope + DataStore PBKDF2 upgrade commit
            var updatedPrefs = prefs.preferencesFlow.first()
            val rehashDeadline = System.currentTimeMillis() + 5_000L
            while (updatedPrefs.pinHash == legacyHex && System.currentTimeMillis() < rehashDeadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(25)
                updatedPrefs = prefs.preferencesFlow.first()
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertNotEquals("Legacy hash should have been upgraded", legacyHex, updatedPrefs.pinHash)
            assertTrue(updatedPrefs.pinHash.orEmpty().startsWith("pbkdf2_sha256$120000$"))
            assertFalse(PinSecurityHelper.needsRehash(updatedPrefs.pinHash))
        } finally {
            runCatching { prefs.clearPinLock() }
            db.close()
        }
    }

    @Test
    fun `viewmodel enforces brute force lockout after 5 consecutive failed pin attempts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomGenealogyRepository(db)
            val media = LocalMediaStorage(context)
            val salt = PinSecurityHelper.generateSalt()
            val hash = PinSecurityHelper.hashPin("7531", salt)
            prefs.setPinLock(salt, hash, enableBiometric = false)

            val viewModel = JuthoorViewModel(repo, prefs, media)
            // Wait until ViewModel uiState has collected loaded PIN preferences
            val readyDeadline = System.currentTimeMillis() + 5_000L
            while (!viewModel.uiState.value.preferences.pinEnabled && System.currentTimeMillis() < readyDeadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(25)
            }
            shadowOf(Looper.getMainLooper()).idle()

            repeat(5) {
                assertFalse("Wrong PIN attempt #${it + 1} must fail", viewModel.verifyAndUnlockWithPin("9999"))
            }

            // 6th attempt even with the CORRECT PIN must be blocked by cooldown lockout
            assertFalse(
                "ViewModel should enforce cooldown lockout after 5 failed attempts",
                viewModel.verifyAndUnlockWithPin("7531"),
            )
        } finally {
            runCatching { prefs.clearPinLock() }
            db.close()
        }
    }

    @Test
    fun `session token cipher aes256 gcm roundtrips rejects tampering and auth store persists cleanly`() = runTest {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val secretKey = keyGen.generateKey()

        val rawAccess = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.juthoor.payload.sig"
        val encrypted = SessionTokenCipher.encryptWithKey(rawAccess, secretKey)

        assertTrue(SessionTokenCipher.isEncryptedFormat(encrypted))
        assertFalse("Ciphertext must not expose raw JWT", encrypted.contains("eyJhbGciOi"))
        assertEquals(rawAccess, SessionTokenCipher.decryptWithKey(encrypted, secretKey))

        // Tampering ciphertext must fail GCM authentication tag verification
        val tampered = encrypted.dropLast(4) + "AAAA"
        val tamperResult = runCatching { SessionTokenCipher.decryptWithKey(tampered, secretKey) }
        assertTrue("Tampered GCM payload must throw AEADBadTagException", tamperResult.isFailure)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AuthSessionStore(context)
        val sampleSession = AuthSession(
            userId = "u-sec-1",
            email = "member@juthoor.app",
            displayName = "عضو موثق",
            accessToken = rawAccess,
            refreshToken = "refresh-token-xyz",
            expiresAtMillis = System.currentTimeMillis() + 3600_000L,
            activeFamilyId = "fam-123",
        )
        store.saveSession(sampleSession)
        val loaded = store.getSessionOnce()
        assertEquals(sampleSession.userId, loaded?.userId)
        assertEquals(sampleSession.accessToken, loaded?.accessToken)
        assertEquals(sampleSession.refreshToken, loaded?.refreshToken)
        assertEquals(sampleSession.activeFamilyId, loaded?.activeFamilyId)

        store.clearSession()
        store.updateActiveFamilyId(null)
        assertEquals(null, store.getSessionOnce())
    }

    @Test
    fun `manifest disables auto backup and zip backup version matches BuildConfig`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val allowBackupEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        assertFalse("android:allowBackup must be disabled in AndroidManifest", allowBackupEnabled)

        assertEquals(BuildConfig.VERSION_NAME, ZipBackupManager.APP_VERSION)
        assertEquals("0.8.0", ZipBackupManager.APP_VERSION)

        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = PreferencesRepository(context)
            val media = LocalMediaStorage(context)
            val backupManager = ZipBackupManager(context, db, media, prefs)
            val out = ByteArrayOutputStream()
            val manifest = backupManager.exportBackupToStream(out).getOrThrow()
            assertEquals(BuildConfig.VERSION_NAME, manifest.appVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun `custom supabase url and anon key can be saved in app ui to configure gateway dynamically`() = runTest {
        assertNotNull(SupabaseConfig.validateInputs("", "valid-key-1234567890"))
        assertNotNull(SupabaseConfig.validateInputs("ftp://bad.host", "valid-key-1234567890"))
        assertNotNull(SupabaseConfig.validateInputs("https://YOUR_PROJECT_REF.supabase.co", "valid-key-1234567890"))
        assertNotNull(SupabaseConfig.validateInputs("https://myproj.supabase.co", "short"))
        assertNull(SupabaseConfig.validateInputs("https://myproj.supabase.co/", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sample"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        val authStore = AuthSessionStore(context)
        authStore.clearSession()
        authStore.clearCustomCloudEndpoint()

        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val runtimeConfigState = MutableStateFlow(
                SupabaseConfig(url = "https://YOUR_PROJECT_REF.supabase.co", anonKey = "YOUR_SUPABASE_ANON_KEY")
            )
            val gateway = HttpSupabaseGateway(configProvider = { runtimeConfigState.value })
            assertFalse("Placeholder config should start unconfigured", gateway.isConfigured)

            val repo = RoomGenealogyRepository(db, authStore)
            val cloudRepo = RoomAndSupabaseAuthRepository(
                database = db,
                authSessionStore = authStore,
                preferencesRepository = prefs,
                supabaseGateway = gateway,
                supabaseConfigState = runtimeConfigState,
            )
            val viewModel = JuthoorViewModel(
                repository = repo,
                preferencesRepository = prefs,
                mediaStorage = LocalMediaStorage(context),
                cloudAuthRepository = cloudRepo,
            )

            composeTestRule.setContent {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                FamilyAccountScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onSignIn = viewModel::signInWithEmail,
                    onSignUp = viewModel::signUpWithEmail,
                    onSignOut = viewModel::signOutFromCloud,
                    onCreateCloudFamilySpace = viewModel::createCloudFamilySpace,
                    onRefreshFamilies = viewModel::refreshCloudFamilies,
                    onSwitchActiveFamily = viewModel::switchActiveFamily,
                    onSaveCloudConfig = viewModel::saveCustomCloudConfig,
                    onClearCloudConfig = viewModel::clearCustomCloudConfig,
                )
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                !viewModel.uiState.value.isLoading
            }
            composeTestRule.waitForIdle()

            // Card should be expanded when backend is not configured
            composeTestRule.onNodeWithTag("cloud_config_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("cloud_config_url_input").performScrollTo()
                .performTextInput("https://my-family-tree.supabase.co/")
            composeTestRule.onNodeWithTag("cloud_config_key_input").performScrollTo()
                .performTextInput("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_anon_key_value")
            composeTestRule.onNodeWithTag("cloud_config_save_button")
                .performScrollTo()
                .assertIsEnabled()
                .performClick()

            val deadline = System.currentTimeMillis() + 5_000L
            while (!viewModel.uiState.value.isCloudBackendConfigured && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(25)
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("Runtime gateway must now report configured", gateway.isConfigured)
            assertTrue("UiState must reflect configured backend", viewModel.uiState.value.isCloudBackendConfigured)
            assertEquals("https://my-family-tree.supabase.co", viewModel.uiState.value.configuredCloudUrl)
            assertTrue(viewModel.uiState.value.hasCustomCloudConfig)
        } finally {
            runCatching { authStore.clearCustomCloudEndpoint() }
            db.close()
        }
    }
}
