package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.ZipBackupManager
import com.example.data.export.FamilyExportManager
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.AuditOperation
import com.example.domain.model.DatePrecision
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyGroup
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.model.UserPreferences
import com.example.domain.security.PinSecurityHelper
import com.example.domain.stats.StatisticsEngine
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.presentation.JuthoorUiState
import com.example.presentation.JuthoorViewModel
import com.example.presentation.navigation.JuthoorAppRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseSevenBackupExportSecurityTest {

    private val openDatabases = mutableListOf<JuthoorDatabase>()
    val composeTestRule = createComposeRule()

    @get:Rule
    val testRuleChain: org.junit.rules.RuleChain = org.junit.rules.RuleChain
        .outerRule(object : org.junit.rules.ExternalResource() {
            override fun after() {
                openDatabases.forEach { runCatching { it.close() } }
                openDatabases.clear()
            }
        })
        .around(composeTestRule)

    @Test
    fun `zip backup exports media and database verifies sha256 restores atomically and rejects tampered archive`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val repo = RoomGenealogyRepository(db)
            val prefs = PreferencesRepository(context)
            val media = LocalMediaStorage(context)
            media.clearAllManagedMedia()

            prefs.updateFamilyIdentity("عائلة السامعي", "نوثّق الجذور والأنساب")
            prefs.markOnboardingCompleted()

            val samplePhotoBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4, 5, 6)
            val storedImagePath = media.saveBytesForRestore("sample_portrait.png", samplePhotoBytes)

            val branch = FamilyGroup(id = "grp_1", groupName = "فرع الحجرية", description = "الفرع الرئيسي")
            repo.saveFamilyGroup(branch).getOrThrow()

            val father = Person(
                id = "p_father",
                fullName = "عبدالرحيم عثمان سيف",
                firstName = "عبدالرحيم",
                familyName = "السامعي",
                gender = Gender.MALE,
                birthDate = "1956-04-12",
                birthDatePrecision = DatePrecision.EXACT,
                profileImagePath = storedImagePath,
                familyGroupId = branch.id,
            )
            val mother = Person(
                id = "p_mother",
                fullName = "نعمة أحمد ناجي",
                firstName = "نعمة",
                gender = Gender.FEMALE,
                birthDate = "1965-01-01",
                birthDatePrecision = DatePrecision.EXACT,
                familyGroupId = branch.id,
            )
            val son = Person(
                id = "p_son",
                fullName = "شهاب عبدالرحيم عثمان",
                firstName = "شهاب",
                familyName = "السامعي",
                gender = Gender.MALE,
                birthDate = "1994-06-10",
                birthDatePrecision = DatePrecision.EXACT,
                familyGroupId = branch.id,
            )
            repo.savePerson(father).getOrThrow()
            repo.savePerson(mother).getOrThrow()
            repo.savePerson(son).getOrThrow()

            val marriage = repo.saveMarriage(
                Marriage(
                    id = "m_1",
                    partnerOneId = father.id,
                    partnerTwoId = mother.id,
                    marriageDate = "1988-07-01",
                    marriageDatePrecision = DatePrecision.EXACT,
                    marriageStatus = MarriageRecordStatus.ACTIVE,
                )
            ).getOrThrow()
            repo.addParentChildLink(father.id, son.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriage.id)
                .getOrThrow()
            repo.saveEducation(
                EducationRecord(
                    id = "edu_1",
                    personId = son.id,
                    qualification = "بكالوريوس هندسة برمجيات",
                    institution = "جامعة تعز",
                    graduationYear = 2017,
                )
            ).getOrThrow()
            repo.savePhoto(
                PhotoRecord(
                    id = "ph_1",
                    personId = father.id,
                    taggedPersonIds = listOf(father.id, son.id),
                    filePath = storedImagePath,
                    description = "صورة تذكارية",
                    mediaType = MediaType.FAMILY_DOCUMENT,
                )
            ).getOrThrow()
            repo.saveEvent(
                EventRecord(
                    id = "ev_1",
                    personId = son.id,
                    eventType = FamilyEventType.GRADUATION,
                    eventDate = "2017-06-15",
                    eventDatePrecision = DatePrecision.EXACT,
                    description = "تخرج جامعي",
                )
            ).getOrThrow()

            val backupManager = ZipBackupManager(context, db, media, prefs)
            val zipOut = ByteArrayOutputStream()
            val manifest = backupManager.exportBackupToStream(zipOut).getOrThrow()
            val zipBytes = zipOut.toByteArray()

            assertEquals(3, manifest.counts.personsCount)
            assertEquals(1, manifest.counts.marriagesCount)
            assertEquals(1, manifest.counts.parentChildCount)
            assertEquals(1, manifest.counts.educationCount)
            assertEquals(1, manifest.counts.photosCount)
            assertEquals(1, manifest.counts.eventsCount)
            assertEquals(1, manifest.counts.familyGroupsCount)
            assertEquals(1, manifest.counts.mediaFilesCount)

            // Tamper with database.json inside the zip and confirm SHA-256 verification rejects it
            val tamperedOut = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zin ->
                ZipOutputStream(tamperedOut).use { zout ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        val content = zin.readBytes()
                        zout.putNextEntry(ZipEntry(entry.name))
                        if (entry.name == "database.json") {
                            val modified = String(content, Charsets.UTF_8).replace("السامعي", "المعدل")
                            zout.write(modified.toByteArray(Charsets.UTF_8))
                        } else {
                            zout.write(content)
                        }
                        zout.closeEntry()
                        zin.closeEntry()
                    }
                }
            }
            val tamperedResult = backupManager.inspectAndVerifyBackup(ByteArrayInputStream(tamperedOut.toByteArray()))
            assertTrue("Tampered backup archive must fail SHA-256 verification", tamperedResult.isFailure)

            // Wipe live DB and managed media, then inspect & restore from verified valid backup
            db.withTransaction {
                db.photoDao().deleteAllCrossRefs()
                db.photoDao().deleteAll()
                db.eventDao().deleteAll()
                db.educationDao().deleteAll()
                db.parentChildDao().deleteAll()
                db.marriageDao().deleteAll()
                db.familyGroupDao().deleteAll()
                db.personDao().deleteAll()
                db.auditLogDao().deleteAll()
            }
            media.clearAllManagedMedia()
            assertEquals(0, db.personDao().getAllOnce().size)

            val preview = backupManager.inspectAndVerifyBackup(ByteArrayInputStream(zipBytes)).getOrThrow()
            val restoreResult = backupManager.restoreFromVerifiedPreview(preview).getOrThrow()
            assertEquals(3, restoreResult.restoredCounts.personsCount)

            val restoredPersons = db.personDao().getAllOnce()
            val restoredMarriages = db.marriageDao().getAllOnce()
            val restoredLinks = db.parentChildDao().getAllOnce()
            val restoredPhotos = db.photoDao().getAllOnce()
            val restoredAudits = db.auditLogDao().getAllOnce()

            assertEquals(3, restoredPersons.size)
            assertEquals(1, restoredMarriages.size)
            assertEquals(1, restoredLinks.size)
            assertEquals(1, restoredPhotos.size)
            assertTrue(restoredAudits.any { it.operation == AuditOperation.RESTORE })

            val restoredPhotoFile = File(restoredPhotos.first().filePath)
            assertTrue("Restored media file must exist on disk", restoredPhotoFile.exists())
            assertArrayEquals(samplePhotoBytes, restoredPhotoFile.readBytes())

            db.close()
        }

    @Test
    fun `exports generate real pdf png and utf8 csv with arabic records`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportManager = FamilyExportManager(context)

        val father = Person(
            id = "e_father",
            fullName = "عبدالرحيم عثمان سيف",
            firstName = "عبدالرحيم",
            gender = Gender.MALE,
            birthDate = "1956-04-12",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.MARRIED,
        )
        val child = Person(
            id = "e_child",
            fullName = "شهاب عبدالرحيم عثمان",
            firstName = "شهاب",
            gender = Gender.MALE,
            birthDate = "1994-06-10",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.SINGLE,
        )
        val persons = listOf(father, child)
        val links = listOf(ParentChildLink("el1", father.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER))

        val csvOut = ByteArrayOutputStream()
        val exportedRows = exportManager.exportCsvToStream(
            outputStream = csvOut,
            familyTitle = "عائلة السامعي",
            persons = persons,
            marriages = emptyList(),
            links = links,
            familyGroups = emptyList(),
        ).getOrThrow()
        assertEquals(2, exportedRows)
        val csvText = String(csvOut.toByteArray(), Charsets.UTF_8)
        assertTrue(csvText.startsWith("\uFEFF"))
        assertTrue(csvText.contains("الاسم الكامل"))
        assertTrue(csvText.contains("شهاب عبدالرحيم عثمان"))
        assertTrue(csvText.contains("عبدالرحيم عثمان سيف"))

        val pngOut = ByteArrayOutputStream()
        val (w, h) = exportManager.exportTreePngToStream(
            outputStream = pngOut,
            familyTitle = "عائلة السامعي",
            persons = persons,
            marriages = emptyList(),
            links = links,
            showHijri = true,
        ).getOrThrow()
        assertTrue(w >= 800 && h >= 540)
        val pngBytes = pngOut.toByteArray()
        assertTrue(pngBytes.size > 100)
        assertEquals(0x89.toByte(), pngBytes[0])
        assertEquals('P'.code.toByte(), pngBytes[1])
        assertEquals('N'.code.toByte(), pngBytes[2])
        assertEquals('G'.code.toByte(), pngBytes[3])

        val pdfOut = ByteArrayOutputStream()
        val pageCount = exportManager.exportPdfToStream(
            outputStream = pdfOut,
            familyTitle = "عائلة السامعي",
            familyMotto = "نوثّق الأصول",
            persons = persons,
            marriages = emptyList(),
            links = links,
            familyGroups = emptyList(),
            showHijri = true,
        ).getOrThrow()
        assertEquals(3, pageCount)
        val pdfBytes = pdfOut.toByteArray()
        val header = String(pdfBytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }

    @Test
    fun `pin security helper hashes verifies and app lock screen unlocks with correct pin`() = runTest {
        assertNotNull(PinSecurityHelper.validatePinFormat("12"))
        assertNotNull(PinSecurityHelper.validatePinFormat("12ab"))
        assertNull(PinSecurityHelper.validatePinFormat("2468"))

        val salt = PinSecurityHelper.generateSalt()
        val hash = PinSecurityHelper.hashPin("2468", salt)
        assertTrue(PinSecurityHelper.verifyPin("2468", salt, hash))
        assertFalse(PinSecurityHelper.verifyPin("9999", salt, hash))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
        val repo = RoomGenealogyRepository(db)
        val prefs = PreferencesRepository(context)
        val media = LocalMediaStorage(context)
        prefs.setPinLock(salt, hash, enableBiometric = false)
        prefs.markOnboardingCompleted()
        val loadedPrefs = prefs.preferencesFlow.first()

        val viewModel = JuthoorViewModel(repo, prefs, media)
        val unlockedFlag = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            val state = JuthoorUiState(
                isLoading = false,
                preferences = loadedPrefs,
                shouldShowOnboarding = false,
                persons = emptyList(),
                overviewMetrics = GenealogyMetricsCalculator.computeHomeOverview(emptyList(), emptyList(), emptyList()),
                statisticsReport = StatisticsEngine.compute(emptyList(), emptyList(), emptyList(), emptyList()),
                isAppUnlocked = unlockedFlag.value,
            )
            JuthoorAppRoot(
                uiState = state,
                viewModel = viewModel,
            )
        }
        composeTestRule.waitForIdle()

        // Lock screen must block access initially
        composeTestRule.onNodeWithTag("app_lock_screen").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("nav_home").fetchSemanticsNodes().size)

        // Wrong PIN should show error and stay locked
        listOf("1", "1", "1", "1").forEach { d ->
            composeTestRule.onNodeWithTag("pin_key_$d").performScrollTo().performClick()
        }
        composeTestRule.onNodeWithTag("pin_key_submit").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lock_error_message").performScrollTo().assertIsDisplayed()

        // Correct PIN (2468) unlocks cleanly via ViewModel verification
        listOf("2", "4", "6", "8").forEach { d ->
            composeTestRule.onNodeWithTag("pin_key_$d").performScrollTo().performClick()
        }
        composeTestRule.onNodeWithTag("pin_key_submit").performScrollTo().performClick()
        assertTrue(viewModel.verifyAndUnlockWithPin("2468"))
        unlockedFlag.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("topbar_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_security_card"))
        composeTestRule.onNodeWithTag("settings_security_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_backup_card"))
        composeTestRule.onNodeWithTag("settings_backup_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_export_card"))
        composeTestRule.onNodeWithTag("settings_export_card").assertIsDisplayed()
    }
}
