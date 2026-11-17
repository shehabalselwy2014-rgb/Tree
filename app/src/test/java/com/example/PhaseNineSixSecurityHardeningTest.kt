package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.ZipBackupManager
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.AuthSessionStore
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.SessionTokenCipher
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.AuthSession
import com.example.domain.security.PinSecurityHelper
import com.example.presentation.JuthoorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseNineSixSecurityHardeningTest {

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
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomGenealogyRepository(db)
            val prefs = PreferencesRepository(context)
            val media = LocalMediaStorage(context)
            prefs.setPinLock(salt, legacyHex, enableBiometric = false)

            val viewModel = JuthoorViewModel(repo, prefs, media)
            // Drain Dispatchers.Main so ViewModel init collects initial preferences
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(viewModel.verifyAndUnlockWithPin("4826"))
            shadowOf(Looper.getMainLooper()).idle()

            val updatedPrefs = prefs.preferencesFlow.first()
            assertNotEquals("Legacy hash should have been upgraded", legacyHex, updatedPrefs.pinHash)
            assertTrue(updatedPrefs.pinHash.orEmpty().startsWith("pbkdf2_sha256$120000$"))
            assertFalse(PinSecurityHelper.needsRehash(updatedPrefs.pinHash))
        } finally {
            db.close()
        }
    }

    @Test
    fun `viewmodel enforces brute force lockout after 5 consecutive failed pin attempts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomGenealogyRepository(db)
            val prefs = PreferencesRepository(context)
            val media = LocalMediaStorage(context)
            val salt = PinSecurityHelper.generateSalt()
            val hash = PinSecurityHelper.hashPin("7531", salt)
            prefs.setPinLock(salt, hash, enableBiometric = false)

            val viewModel = JuthoorViewModel(repo, prefs, media)
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
}
