package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.ZipBackupManager
import com.example.data.local.JuthoorDatabase
import com.example.data.local.JuthoorDatabaseMigrations
import com.example.data.local.entity.FamilyInvitationEntity
import com.example.data.local.entity.FamilyMemberEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.AuditOperation
import com.example.domain.model.DatePrecision
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MaritalStatus
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.MultiUserDefaults
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseNineTwoSchemaAndMigrationTest {

    private val openedDatabases = mutableListOf<JuthoorDatabase>()
    private val dbFilesToDelete = mutableListOf<String>()

    @After
    fun tearDown() {
        openedDatabases.forEach { runCatching { it.close() } }
        openedDatabases.clear()
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFilesToDelete.forEach { name ->
            runCatching { context.deleteDatabase(name) }
        }
        dbFilesToDelete.clear()
    }

    @Test
    fun `migration 1 to 2 preserves all populated v1 records and validates schema cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_v1_populated_migration.db"
        dbFilesToDelete.add(dbName)
        context.deleteDatabase(dbName)

        // Create and populate exact Room schema v1 SQLite database
        val helper = V1ExactSchemaOpenHelper(context, dbName)
        helper.writableDatabase.use { sqlDb ->
            sqlDb.execSQL("PRAGMA foreign_keys=ON")
            sqlDb.execSQL(
                """
                INSERT INTO `family_groups` (`id`, `groupName`, `description`, `rootPersonId`, `createdAt`)
                VALUES ('grp_main', 'آل السامعي', 'الفرع الرئيسي', NULL, 1700000000000)
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `persons` (`id`, `fullName`, `firstName`, `middleName`, `familyName`, `gender`, `birthDate`, `birthDatePrecision`, `birthPlace`, `deathDate`, `deathDatePrecision`, `deathPlace`, `biography`, `occupation`, `currentMaritalStatus`, `profileImagePath`, `familyGroupId`, `notes`, `createdAt`, `updatedAt`)
                VALUES
                ('p_founder', 'عبدالرحيم عثمان سيف السامعي', 'عبدالرحيم', 'عثمان سيف', 'السامعي', 'MALE', '1956-04-12', 'EXACT', 'تعز', NULL, 'UNKNOWN', NULL, 'مؤسس العائلة', 'متقاعد', 'MARRIED', NULL, 'grp_main', NULL, 1700000001000, 1700000001000),
                ('p_wife_1', 'نعمة أحمد ناجي قاسم', 'نعمة', 'أحمد ناجي', 'قاسم', 'FEMALE', '1965-01-01', 'EXACT', NULL, NULL, 'UNKNOWN', NULL, NULL, 'ربة بيت', 'MARRIED', NULL, 'grp_main', NULL, 1700000002000, 1700000002000),
                ('p_wife_2', 'فاطمة محمد علي', 'فاطمة', 'محمد', 'علي', 'FEMALE', '1972-03-10', 'EXACT', NULL, NULL, 'UNKNOWN', NULL, NULL, NULL, 'MARRIED', NULL, 'grp_main', NULL, 1700000003000, 1700000003000),
                ('p_son_1', 'شهاب عبدالرحيم عثمان السامعي', 'شهاب', 'عبدالرحيم عثمان', 'السامعي', 'MALE', '1994-06-10', 'EXACT', NULL, NULL, 'UNKNOWN', NULL, NULL, 'مهندس برمجيات', 'SINGLE', NULL, 'grp_main', NULL, 1700000004000, 1700000004000)
                """.trimIndent()
            )
            sqlDb.execSQL("UPDATE `family_groups` SET `rootPersonId` = 'p_founder' WHERE `id` = 'grp_main'")
            sqlDb.execSQL(
                """
                INSERT INTO `marriages` (`id`, `partnerOneId`, `partnerTwoId`, `marriageDate`, `marriageDatePrecision`, `marriagePlace`, `marriageStatus`, `endDate`, `endReason`, `notes`, `createdAt`, `updatedAt`)
                VALUES
                ('m_1', 'p_founder', 'p_wife_1', '1988-07-01', 'EXACT', 'تعز', 'ACTIVE', NULL, NULL, NULL, 1700000005000, 1700000005000),
                ('m_2', 'p_founder', 'p_wife_2', '1998-05-15', 'EXACT', 'صنعاء', 'ACTIVE', NULL, NULL, NULL, 1700000006000, 1700000006000)
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `parent_child` (`id`, `parentId`, `childId`, `relationshipType`, `marriageId`, `notes`, `createdAt`)
                VALUES
                ('pc_1', 'p_founder', 'p_son_1', 'BIOLOGICAL_FATHER', 'm_1', NULL, 1700000007000),
                ('pc_2', 'p_wife_1', 'p_son_1', 'BIOLOGICAL_MOTHER', 'm_1', NULL, 1700000008000)
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `education` (`id`, `personId`, `qualification`, `specialization`, `institution`, `graduationYear`, `notes`)
                VALUES ('edu_1', 'p_son_1', 'بكالوريوس', 'هندسة برمجيات', 'جامعة تعز', 2017, NULL)
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `photos` (`id`, `personId`, `filePath`, `description`, `photoDate`, `photoDatePrecision`, `mediaType`, `createdAt`)
                VALUES ('ph_1', 'p_founder', '/tmp/founder.png', 'صورة عائلية', '2020-01-01', 'EXACT', 'FAMILY_DOCUMENT', 1700000009000)
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `photo_person_cross_ref` (`photoId`, `personId`)
                VALUES ('ph_1', 'p_founder'), ('ph_1', 'p_son_1')
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `events` (`id`, `personId`, `eventType`, `eventDate`, `eventDatePrecision`, `eventPlace`, `description`)
                VALUES ('ev_1', 'p_son_1', 'GRADUATION', '2017-06-15', 'EXACT', 'تعز', 'تخرج')
                """.trimIndent()
            )
            sqlDb.execSQL(
                """
                INSERT INTO `audit_log` (`id`, `entityType`, `entityId`, `operation`, `oldValue`, `newValue`, `operationDate`)
                VALUES ('aud_1', 'Persons', 'p_founder', 'CREATE', NULL, 'عبدالرحيم عثمان سيف السامعي', 1700000001000)
                """.trimIndent()
            )
        }
        helper.close()

        // Open with Room v2 and MIGRATION_1_2 ONLY (no fallbackToDestructiveMigration)
        val migratedDb = Room.databaseBuilder(context, JuthoorDatabase::class.java, dbName)
            .addMigrations(JuthoorDatabaseMigrations.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also { openedDatabases.add(it) }

        val persons = migratedDb.personDao().getAllOnce()
        val marriages = migratedDb.marriageDao().getAllOnce()
        val links = migratedDb.parentChildDao().getAllOnce()
        val education = migratedDb.educationDao().getAllOnce()
        val photos = migratedDb.photoDao().getAllOnce()
        val crossRefs = migratedDb.photoDao().getAllCrossRefsOnce()
        val events = migratedDb.eventDao().getAllOnce()
        val groups = migratedDb.familyGroupDao().getAllOnce()
        val audits = migratedDb.auditLogDao().getAllOnce()
        val families = migratedDb.familySpaceDao().getAllOnce()

        assertEquals(4, persons.size)
        assertEquals(2, marriages.size)
        assertEquals(2, links.size)
        assertEquals(1, education.size)
        assertEquals(1, photos.size)
        assertEquals(2, crossRefs.size)
        assertEquals(1, events.size)
        assertEquals(1, groups.size)
        assertEquals(1, audits.size)

        // Verify new default columns and backfilled timestamps
        assertTrue(persons.all { it.familyId == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID && it.version == 1L && !it.isDeleted && it.syncState == SyncState.LOCAL_ONLY })
        assertEquals(1700000007000L, links.first { it.id == "pc_1" }.updatedAt)
        assertEquals(1700000009000L, photos.first().updatedAt)
        assertEquals(1700000000000L, groups.first().updatedAt)

        // Verify local_default_family container row was seeded cleanly with founder rootPersonId
        assertEquals(1, families.size)
        assertEquals(MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID, families.first().id)
        assertEquals("p_founder", families.first().rootPersonId)
        assertEquals(SyncState.LOCAL_ONLY, families.first().syncState)
    }

    @Test
    fun `migration 1 to 2 on empty v1 database does not fabricate sample family row`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_v1_empty_migration.db"
        dbFilesToDelete.add(dbName)
        context.deleteDatabase(dbName)

        val helper = V1ExactSchemaOpenHelper(context, dbName)
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, JuthoorDatabase::class.java, dbName)
            .addMigrations(JuthoorDatabaseMigrations.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also { openedDatabases.add(it) }

        assertEquals(0, migratedDb.personDao().getAllOnce().size)
        assertEquals(0, migratedDb.familySpaceDao().getAllOnce().size)
    }

    @Test
    fun `user account stays separated from person record and family_members enforces unique constraints`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openedDatabases.add(it) }

        val now = System.currentTimeMillis()
        val familyId = "fam_al_samei"
        db.familySpaceDao().upsert(
            FamilySpaceEntity(
                id = familyId,
                name = "عائلة السامعي",
                motto = "نوثّق الأصول",
                ownerUserId = "usr_founder",
                rootPersonId = null,
                createdAt = now,
                updatedAt = now,
                syncState = SyncState.SYNCED,
            )
        )
        db.userAccountDao().upsert(
            UserAccountEntity(
                id = "usr_founder",
                email = "founder@juthoor.example",
                displayName = "عبدالرحيم عثمان",
                avatarStorageKey = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        db.userAccountDao().upsert(
            UserAccountEntity(
                id = "usr_son",
                email = "shehab@juthoor.example",
                displayName = "شهاب عبدالرحيم",
                avatarStorageKey = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        // Persons can exist without user accounts (e.g., ancestors/children)
        db.personDao().upsert(
            PersonEntity(
                id = "per_son",
                fullName = "شهاب عبدالرحيم عثمان السامعي",
                firstName = "شهاب",
                middleName = "عبدالرحيم عثمان",
                familyName = "السامعي",
                gender = Gender.MALE,
                birthDate = "1994-06-10",
                birthDatePrecision = DatePrecision.EXACT,
                birthPlace = null,
                deathDate = null,
                deathDatePrecision = DatePrecision.UNKNOWN,
                deathPlace = null,
                biography = null,
                occupation = null,
                currentMaritalStatus = MaritalStatus.SINGLE,
                profileImagePath = null,
                familyGroupId = null,
                notes = null,
                createdAt = now,
                updatedAt = now,
                familyId = familyId,
                syncState = SyncState.SYNCED,
            )
        )

        // Link usr_son account to per_son in family_members
        db.familyMemberDao().upsert(
            FamilyMemberEntity(
                id = "mem_son",
                familyId = familyId,
                userId = "usr_son",
                linkedPersonId = "per_son",
                role = FamilyRole.BRANCH_MANAGER,
                branchRootPersonId = "per_son",
                canInvite = false,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
                invitedByUserId = "usr_founder",
                joinedAt = now,
                updatedAt = now,
                syncState = SyncState.SYNCED,
            )
        )

        val linkedMember = db.familyMemberDao().getActiveByLinkedPerson(familyId, "per_son")
        assertNotNull(linkedMember)
        assertEquals("usr_son", linkedMember?.userId)
        assertEquals(FamilyRole.BRANCH_MANAGER, linkedMember?.role)

        // Attempting to link a second user account to the exact same person in the same family must fail
        db.userAccountDao().upsert(
            UserAccountEntity(
                id = "usr_duplicate",
                email = "dup@juthoor.example",
                displayName = "مستخدم آخر",
                avatarStorageKey = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        var duplicatePersonRejected = false
        try {
            db.familyMemberDao().insertAll(
                listOf(
                    FamilyMemberEntity(
                        id = "mem_dup_person",
                        familyId = familyId,
                        userId = "usr_duplicate",
                        linkedPersonId = "per_son",
                        role = FamilyRole.MEMBER,
                        branchRootPersonId = "per_son",
                        canInvite = false,
                        canExport = true,
                        canManageMedia = false,
                        status = MembershipStatus.ACTIVE,
                        invitedByUserId = "usr_founder",
                        joinedAt = now,
                        updatedAt = now,
                    )
                )
            )
        } catch (_: SQLiteConstraintException) {
            duplicatePersonRejected = true
        }
        assertTrue("Duplicate linkedPersonId in the same family must violate unique index", duplicatePersonRejected)

        // Invitation entity persists hashed code and expiresAt metadata cleanly
        db.familyInvitationDao().upsert(
            FamilyInvitationEntity(
                id = "inv_1",
                familyId = familyId,
                targetPersonId = "per_son",
                assignedRole = FamilyRole.BRANCH_MANAGER,
                branchRootPersonId = "per_son",
                inviteCodeHash = "hash_abc123",
                inviteCodeHint = "JTH-8F2K",
                maxUses = 1,
                usesCount = 0,
                expiresAt = now + 86_400_000L,
                status = InvitationStatus.PENDING,
                createdByUserId = "usr_founder",
                acceptedByUserId = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        assertEquals("inv_1", db.familyInvitationDao().getByCodeHash("hash_abc123")?.id)
    }

    @Test
    fun `synced records use soft delete tombstones and outbox while local_only deletes cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openedDatabases.add(it) }
        val repo = RoomGenealogyRepository(db)

        val familyId = "fam_cloud"
        val father = repo.savePerson(
            Person(
                id = "p_f",
                fullName = "عبدالرحيم عثمان",
                firstName = "عبدالرحيم",
                gender = Gender.MALE,
                familyId = familyId,
                syncState = SyncState.SYNCED,
            )
        ).getOrThrow()
        val mother = repo.savePerson(
            Person(
                id = "p_m",
                fullName = "نعمة أحمد",
                firstName = "نعمة",
                gender = Gender.FEMALE,
                familyId = familyId,
                syncState = SyncState.SYNCED,
            )
        ).getOrThrow()
        val child = repo.savePerson(
            Person(
                id = "p_c",
                fullName = "شهاب عبدالرحيم عثمان",
                firstName = "شهاب",
                gender = Gender.MALE,
                familyId = familyId,
                syncState = SyncState.SYNCED,
            )
        ).getOrThrow()

        val marriage = repo.saveMarriage(
            Marriage(
                id = "m_cloud",
                partnerOneId = father.id,
                partnerTwoId = mother.id,
                marriageStatus = MarriageRecordStatus.ACTIVE,
                familyId = familyId,
                syncState = SyncState.SYNCED,
            )
        ).getOrThrow()

        val link = repo.addParentChildLink(
            parentId = father.id,
            childId = child.id,
            relationshipType = ParentRelationshipType.BIOLOGICAL_FATHER,
            marriageId = marriage.id,
        ).getOrThrow()
        // Mark link SYNCED to verify soft-delete + revival
        db.parentChildDao().upsert(db.parentChildDao().getById(link.id)!!.copy(syncState = SyncState.SYNCED))

        repo.deleteParentChildLink(link.id).getOrThrow()
        // Active query hides tombstone
        assertTrue(repo.observeAllParentChildLinks().first().none { it.id == link.id })
        // Tombstone still exists for sync push
        val tombstone = db.parentChildDao().getByIdIncludingDeleted(link.id)
        assertNotNull(tombstone)
        assertTrue(tombstone!!.isDeleted)
        assertEquals(SyncState.PENDING_DELETE, tombstone.syncState)
        assertEquals(2L, tombstone.version)

        // Outbox holds a SOFT_DELETE operation for the link
        val outboxEntries = db.syncOutboxDao().getPendingForFamily(familyId)
        assertTrue(
            outboxEntries.any {
                it.entityType == SyncEntityType.PARENT_CHILD &&
                    it.entityId == link.id &&
                    it.operation == SyncOutboxOperation.SOFT_DELETE &&
                    it.status == SyncOutboxStatus.PENDING
            }
        )

        // Re-adding exact same parent-child relationship revives tombstone cleanly instead of failing unique index
        val revived = repo.addParentChildLink(
            parentId = father.id,
            childId = child.id,
            relationshipType = ParentRelationshipType.BIOLOGICAL_FATHER,
            marriageId = marriage.id,
        ).getOrThrow()
        assertEquals(link.id, revived.id)
        assertEquals(3L, revived.version)
        assertFalse(revived.isDeleted)

        // Local-only person is deleted cleanly without tombstone
        val localTemp = repo.savePerson(
            Person(
                id = "p_local_temp",
                fullName = "سجل محلي مؤقت",
                firstName = "سجل",
                gender = Gender.MALE,
                syncState = SyncState.LOCAL_ONLY,
            )
        ).getOrThrow()
        repo.deletePersonSafely(localTemp.id).getOrThrow()
        assertNull(db.personDao().getByIdIncludingDeleted(localTemp.id))
    }

    @Test
    fun `zip backup restores legacy schemaVersion 1 archive into Room v2 cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openedDatabases.add(it) }
        val prefs = PreferencesRepository(context)
        val media = LocalMediaStorage(context)
        media.clearAllManagedMedia()
        val backupManager = ZipBackupManager(context, db, media, prefs)

        // Build a strict v1-format database.json (without familyId/version/updatedAt on parentChild/education/events)
        val v1DatabaseJson = JSONObject().apply {
            put("schemaVersion", 1)
            put("familyTitle", "عائلة السامعي")
            put("familyMotto", "نوثّق الجذور")
            put(
                "familyGroups",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "grp_1")
                        put("groupName", "الفرع الرئيسي")
                        put("description", JSONObject.NULL)
                        put("rootPersonId", "p_1")
                        put("createdAt", 1700000000000L)
                    }
                )
            )
            put(
                "persons",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "p_1")
                            put("fullName", "عبدالرحيم عثمان سيف")
                            put("firstName", "عبدالرحيم")
                            put("middleName", "عثمان سيف")
                            put("familyName", "السامعي")
                            put("gender", "MALE")
                            put("birthDate", "1956-04-12")
                            put("birthDatePrecision", "EXACT")
                            put("currentMaritalStatus", "SINGLE")
                            put("familyGroupId", "grp_1")
                            put("createdAt", 1700000001000L)
                            put("updatedAt", 1700000001000L)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "p_2")
                            put("fullName", "شهاب عبدالرحيم عثمان")
                            put("firstName", "شهاب")
                            put("gender", "MALE")
                            put("birthDate", "1994-06-10")
                            put("birthDatePrecision", "EXACT")
                            put("currentMaritalStatus", "SINGLE")
                            put("familyGroupId", "grp_1")
                            put("createdAt", 1700000002000L)
                            put("updatedAt", 1700000002000L)
                        }
                    )
                }
            )
            put("marriages", JSONArray())
            put(
                "parentChildLinks",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "pc_v1")
                        put("parentId", "p_1")
                        put("childId", "p_2")
                        put("relationshipType", ParentRelationshipType.BIOLOGICAL_FATHER.name)
                        put("marriageId", JSONObject.NULL)
                        put("notes", JSONObject.NULL)
                        put("createdAt", 1700000003000L)
                    }
                )
            )
            put(
                "education",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "edu_v1")
                        put("personId", "p_2")
                        put("qualification", "بكالوريوس")
                        put("specialization", "هندسة برمجيات")
                        put("institution", "جامعة تعز")
                        put("graduationYear", 2017)
                        put("notes", JSONObject.NULL)
                    }
                )
            )
            put("photos", JSONArray())
            put("photoPersonCrossRefs", JSONArray())
            put(
                "events",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "ev_v1")
                        put("personId", "p_2")
                        put("eventType", FamilyEventType.GRADUATION.name)
                        put("eventDate", "2017-06-15")
                        put("eventDatePrecision", "EXACT")
                        put("eventPlace", "تعز")
                        put("description", "تخرج")
                    }
                )
            )
            put("auditLogs", JSONArray())
        }.toString(2)

        val dbBytes = v1DatabaseJson.toByteArray(Charsets.UTF_8)
        val dbSha = sha256(dbBytes)
        val overallSha = sha256(
            "fmt=1|schema=1|counts=2,0,1,1,0,1,1,0|db=${dbSha.lowercase()}".toByteArray(Charsets.UTF_8)
        )
        val manifestJson = JSONObject().apply {
            put("formatVersion", 1)
            put("schemaVersion", 1)
            put("appVersion", "0.8.0")
            put("exportedAtMillis", 1700000005000L)
            put("familyTitle", "عائلة السامعي")
            put("familyMotto", "نوثّق الجذور")
            put(
                "counts",
                JSONObject().apply {
                    put("personsCount", 2)
                    put("marriagesCount", 0)
                    put("parentChildCount", 1)
                    put("educationCount", 1)
                    put("photosCount", 0)
                    put("eventsCount", 1)
                    put("familyGroupsCount", 1)
                    put("auditLogsCount", 0)
                    put("mediaFilesCount", 0)
                }
            )
            put("databaseSha256", dbSha)
            put("mediaSha256", JSONObject())
            put("overallPackageSha256", overallSha)
        }.toString(2).toByteArray(Charsets.UTF_8)

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("database.json"))
            zip.write(dbBytes)
            zip.closeEntry()
        }

        val preview = backupManager.inspectAndVerifyBackup(ByteArrayInputStream(zipOut.toByteArray())).getOrThrow()
        assertEquals(1, preview.manifest.schemaVersion)

        val restoreResult = backupManager.restoreFromVerifiedPreview(preview).getOrThrow()
        assertEquals(2, restoreResult.restoredCounts.personsCount)

        val restoredPersons = db.personDao().getAllOnce()
        val restoredLinks = db.parentChildDao().getAllOnce()
        val restoredFamilies = db.familySpaceDao().getAllOnce()
        assertEquals(2, restoredPersons.size)
        assertEquals(1, restoredLinks.size)
        assertTrue(restoredPersons.all { it.familyId == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID })
        assertEquals(1, restoredFamilies.size)
        assertEquals("p_1", restoredFamilies.first().rootPersonId)
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds an exact Room schema v1 SQLite database matching 1.json DDL verbatim.
     */
    private class V1ExactSchemaOpenHelper(
        context: Context,
        dbName: String,
    ) : SQLiteOpenHelper(context, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `firstName` TEXT NOT NULL, `middleName` TEXT, `familyName` TEXT, `gender` TEXT NOT NULL, `birthDate` TEXT, `birthDatePrecision` TEXT NOT NULL, `birthPlace` TEXT, `deathDate` TEXT, `deathDatePrecision` TEXT NOT NULL, `deathPlace` TEXT, `biography` TEXT, `occupation` TEXT, `currentMaritalStatus` TEXT NOT NULL, `profileImagePath` TEXT, `familyGroupId` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`familyGroupId`) REFERENCES `family_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_fullName` ON `persons` (`fullName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_familyName` ON `persons` (`familyName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_gender` ON `persons` (`gender`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_currentMaritalStatus` ON `persons` (`currentMaritalStatus`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_familyGroupId` ON `persons` (`familyGroupId`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `marriages` (`id` TEXT NOT NULL, `partnerOneId` TEXT NOT NULL, `partnerTwoId` TEXT NOT NULL, `marriageDate` TEXT, `marriageDatePrecision` TEXT NOT NULL, `marriagePlace` TEXT, `marriageStatus` TEXT NOT NULL, `endDate` TEXT, `endReason` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`partnerOneId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`partnerTwoId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marriages_partnerOneId` ON `marriages` (`partnerOneId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marriages_partnerTwoId` ON `marriages` (`partnerTwoId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marriages_marriageStatus` ON `marriages` (`marriageStatus`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `parent_child` (`id` TEXT NOT NULL, `parentId` TEXT NOT NULL, `childId` TEXT NOT NULL, `relationshipType` TEXT NOT NULL, `marriageId` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`parentId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`childId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`marriageId`) REFERENCES `marriages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_child_parentId` ON `parent_child` (`parentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_child_childId` ON `parent_child` (`childId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_child_marriageId` ON `parent_child` (`marriageId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_parent_child_parentId_childId_relationshipType` ON `parent_child` (`parentId`, `childId`, `relationshipType`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `education` (`id` TEXT NOT NULL, `personId` TEXT NOT NULL, `qualification` TEXT NOT NULL, `specialization` TEXT, `institution` TEXT, `graduationYear` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_education_personId` ON `education` (`personId`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, `personId` TEXT, `filePath` TEXT NOT NULL, `description` TEXT, `photoDate` TEXT, `photoDatePrecision` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_personId` ON `photos` (`personId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_mediaType` ON `photos` (`mediaType`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `photo_person_cross_ref` (`photoId` TEXT NOT NULL, `personId` TEXT NOT NULL, PRIMARY KEY(`photoId`, `personId`), FOREIGN KEY(`photoId`) REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_person_cross_ref_photoId` ON `photo_person_cross_ref` (`photoId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_person_cross_ref_personId` ON `photo_person_cross_ref` (`personId`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `personId` TEXT, `eventType` TEXT NOT NULL, `eventDate` TEXT, `eventDatePrecision` TEXT NOT NULL, `eventPlace` TEXT, `description` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_personId` ON `events` (`personId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_eventType` ON `events` (`eventType`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `family_groups` (`id` TEXT NOT NULL, `groupName` TEXT NOT NULL, `description` TEXT, `rootPersonId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`rootPersonId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_groups_rootPersonId` ON `family_groups` (`rootPersonId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_groups_groupName` ON `family_groups` (`groupName`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `audit_log` (`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `oldValue` TEXT, `newValue` TEXT, `operationDate` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_entityType` ON `audit_log` (`entityType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_entityId` ON `audit_log` (`entityId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_operationDate` ON `audit_log` (`operationDate`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6c1860d3b0ef34aea54d73bf05811771')")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
