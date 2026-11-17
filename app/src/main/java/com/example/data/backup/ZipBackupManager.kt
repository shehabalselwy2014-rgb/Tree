package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.local.JuthoorDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.EducationEntity
import com.example.data.local.entity.EventEntity
import com.example.data.local.entity.FamilyGroupEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.MarriageEntity
import com.example.data.local.entity.ParentChildEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.local.entity.PhotoEntity
import com.example.data.local.entity.PhotoPersonCrossRef
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.domain.backup.BackupCounts
import com.example.domain.backup.BackupManifest
import com.example.domain.backup.BackupPreview
import com.example.domain.backup.RestoreResult
import com.example.domain.model.AuditOperation
import com.example.domain.model.DatePrecision
import com.example.domain.model.FamilyEventType
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.ParentRelationshipType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipBackupManager(
    private val context: Context,
    private val database: JuthoorDatabase,
    private val mediaStorage: LocalMediaStorage,
    private val preferencesRepository: PreferencesRepository,
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val SUPPORTED_SCHEMA_VERSION = 2
        val APP_VERSION: String
            get() = BuildConfig.VERSION_NAME
        private const val ENTRY_MANIFEST = "manifest.json"
        private const val ENTRY_DATABASE = "database.json"
        private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }

    suspend fun exportBackupToUri(uri: Uri): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("تعذر فتح ملف الوجهة لحفظ النسخة الاحتياطية.")
            out.use { stream ->
                exportBackupToStream(stream).getOrThrow()
            }
        }
    }

    suspend fun exportBackupToStream(outputStream: OutputStream): Result<BackupManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prefs = preferencesRepository.preferencesFlow.first()
                val persons = database.personDao().getAllOnce()
                val marriages = database.marriageDao().getAllOnce()
                val parentChild = database.parentChildDao().getAllOnce()
                val education = database.educationDao().getAllOnce()
                val photos = database.photoDao().getAllOnce()
                val crossRefs = database.photoDao().getAllCrossRefsOnce()
                val events = database.eventDao().getAllOnce()
                val familyGroups = database.familyGroupDao().getAllOnce()
                val auditLogs = database.auditLogDao().getAllOnce()

                // Collect existing media files and rewrite paths cleanly into media/ entries
                val mediaEntryMap = linkedMapOf<String, ByteArray>()
                val pathToZipEntry = hashMapOf<String, String>()
                var mediaIndex = 0

                fun registerMediaPath(rawPath: String?): String? {
                    if (rawPath.isNullOrBlank()) return null
                    pathToZipEntry[rawPath]?.let { return it }
                    val file = File(rawPath)
                    if (!file.exists() || !file.isFile) return null
                    val safeFileName = sanitizeFileName(file.name)
                    val entryName = "media/${mediaIndex++}_$safeFileName"
                    mediaEntryMap[entryName] = file.readBytes()
                    pathToZipEntry[rawPath] = entryName
                    return entryName
                }

                val exportedPersons = persons.map { person ->
                    val mappedImage = registerMediaPath(person.profileImagePath) ?: person.profileImagePath
                    person.copy(profileImagePath = mappedImage)
                }
                val exportedPhotos = photos.map { photo ->
                    val mappedFile = registerMediaPath(photo.filePath) ?: photo.filePath
                    photo.copy(filePath = mappedFile)
                }

                val dbJson = JSONObject().apply {
                    put("schemaVersion", SUPPORTED_SCHEMA_VERSION)
                    put("familyTitle", prefs.familyTitle)
                    put("familyMotto", prefs.familyMotto)
                    put("persons", JSONArray().apply { exportedPersons.forEach { put(it.toJson()) } })
                    put("marriages", JSONArray().apply { marriages.forEach { put(it.toJson()) } })
                    put("parentChildLinks", JSONArray().apply { parentChild.forEach { put(it.toJson()) } })
                    put("education", JSONArray().apply { education.forEach { put(it.toJson()) } })
                    put("photos", JSONArray().apply { exportedPhotos.forEach { put(it.toJson()) } })
                    put("photoPersonCrossRefs", JSONArray().apply { crossRefs.forEach { put(it.toJson()) } })
                    put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
                    put("familyGroups", JSONArray().apply { familyGroups.forEach { put(it.toJson()) } })
                    put("auditLogs", JSONArray().apply { auditLogs.forEach { put(it.toJson()) } })
                }.toString(2)

                val dbBytes = dbJson.toByteArray(Charsets.UTF_8)
                val dbSha256 = sha256Hex(dbBytes)
                val mediaShaMap = mediaEntryMap.mapValues { (_, bytes) -> sha256Hex(bytes) }
                val counts = BackupCounts(
                    personsCount = exportedPersons.size,
                    marriagesCount = marriages.size,
                    parentChildCount = parentChild.size,
                    educationCount = education.size,
                    photosCount = exportedPhotos.size,
                    eventsCount = events.size,
                    familyGroupsCount = familyGroups.size,
                    auditLogsCount = auditLogs.size,
                    mediaFilesCount = mediaEntryMap.size,
                )
                val exportedAt = System.currentTimeMillis()
                val overallSha256 = computeOverallDigest(
                    formatVersion = FORMAT_VERSION,
                    schemaVersion = SUPPORTED_SCHEMA_VERSION,
                    counts = counts,
                    databaseSha256 = dbSha256,
                    mediaShaMap = mediaShaMap,
                )

                val manifest = BackupManifest(
                    formatVersion = FORMAT_VERSION,
                    schemaVersion = SUPPORTED_SCHEMA_VERSION,
                    appVersion = APP_VERSION,
                    exportedAtMillis = exportedAt,
                    familyTitle = prefs.familyTitle,
                    familyMotto = prefs.familyMotto,
                    counts = counts,
                    databaseSha256 = dbSha256,
                    mediaSha256 = mediaShaMap,
                    overallPackageSha256 = overallSha256,
                )
                val manifestBytes = manifest.toJson().toString(2).toByteArray(Charsets.UTF_8)

                ZipOutputStream(outputStream).use { zip ->
                    zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    zip.write(manifestBytes)
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(ENTRY_DATABASE))
                    zip.write(dbBytes)
                    zip.closeEntry()

                    mediaEntryMap.forEach { (entryName, bytes) ->
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
                manifest
            }
        }

    suspend fun inspectBackupFromUri(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("تعذر فتح ملف النسخة الاحتياطية المختار.")
            input.use { stream ->
                inspectAndVerifyBackup(stream).getOrThrow()
            }
        }
    }

    suspend fun inspectAndVerifyBackup(inputStream: InputStream): Result<BackupPreview> =
        withContext(Dispatchers.IO) {
            runCatching {
                var manifestBytes: ByteArray? = null
                var databaseBytes: ByteArray? = null
                val mediaEntries = linkedMapOf<String, ByteArray>()

                ZipInputStream(inputStream).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        validateZipEntryName(name)
                        if (!entry.isDirectory) {
                            val bytes = readEntryBytesBounded(zip)
                            when {
                                name == ENTRY_MANIFEST -> manifestBytes = bytes
                                name == ENTRY_DATABASE -> databaseBytes = bytes
                                name.startsWith("media/") -> mediaEntries[name] = bytes
                            }
                        }
                        zip.closeEntry()
                    }
                }

                val rawManifest = manifestBytes
                    ?: error("ملف النسخة الاحتياطية غير صالح: ملف manifest.json مفقود.")
                val rawDbBytes = databaseBytes
                    ?: error("ملف النسخة الاحتياطية غير صالح: ملف database.json مفقود.")

                val manifest = parseManifest(JSONObject(String(rawManifest, Charsets.UTF_8)))
                if (manifest.schemaVersion > SUPPORTED_SCHEMA_VERSION || manifest.schemaVersion <= 0) {
                    error("إصدار قاعدة البيانات في النسخة (${manifest.schemaVersion}) غير مدعوم.")
                }

                // 1. Verify database.json SHA-256 checksum
                val actualDbSha = sha256Hex(rawDbBytes)
                if (!actualDbSha.equals(manifest.databaseSha256, ignoreCase = true)) {
                    error("فشل التحقق من سلامة البيانات: بصمة SHA-256 لقاعدة البيانات غير مطابقة (الملف تالف أو معدل).")
                }

                // 2. Verify all bundled media SHA-256 checksums
                if (mediaEntries.keys != manifest.mediaSha256.keys) {
                    error("فشل التحقق من سلامة الصور والوثائق المرفقة في النسخة الاحتياطية.")
                }
                manifest.mediaSha256.forEach { (entryName, expectedSha) ->
                    val mediaBytes = mediaEntries[entryName]
                        ?: error("ملف وسائط مفقود داخل النسخة: $entryName")
                    val actualMediaSha = sha256Hex(mediaBytes)
                    if (!actualMediaSha.equals(expectedSha, ignoreCase = true)) {
                        error("تلف في أحد ملفات الوسائط المرفقة ($entryName) - فشل فحص SHA-256.")
                    }
                }

                // 3. Verify package-level integrity digest
                val actualOverall = computeOverallDigest(
                    formatVersion = manifest.formatVersion,
                    schemaVersion = manifest.schemaVersion,
                    counts = manifest.counts,
                    databaseSha256 = actualDbSha,
                    mediaShaMap = manifest.mediaSha256,
                )
                if (!actualOverall.equals(manifest.overallPackageSha256, ignoreCase = true)) {
                    error("فشل التحقق الشامل من حزمة النسخة الاحتياطية (Overall SHA-256).")
                }

                // 4. Dry-parse database.json to guarantee structure validity before user confirmation
                val dbJsonString = String(rawDbBytes, Charsets.UTF_8)
                parseSnapshot(dbJsonString, remapMediaPath = { it })

                BackupPreview(
                    manifest = manifest,
                    rawDatabaseJson = dbJsonString,
                    mediaEntries = mediaEntries,
                )
            }
        }

    suspend fun restoreFromVerifiedPreview(preview: BackupPreview): Result<RestoreResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Re-verify integrity immediately before modifying database
                val dbBytes = preview.rawDatabaseJson.toByteArray(Charsets.UTF_8)
                val dbSha = sha256Hex(dbBytes)
                if (!dbSha.equals(preview.manifest.databaseSha256, ignoreCase = true)) {
                    error("فشل فحص السلامة قبل الاستعادة مباشرة.")
                }

                val restoredMediaPaths = hashMapOf<String, String>()
                preview.mediaEntries.keys.forEach { entryName ->
                    val targetName = "restored_${UUID.randomUUID().toString().take(8)}_${sanitizeFileName(File(entryName).name)}"
                    val dummyBytes = preview.mediaEntries[entryName] ?: ByteArray(0)
                    // Stage exact target path via LocalMediaStorage after transaction, or precompute safe path:
                    val savedPath = mediaStorage.saveBytesForRestore(targetName, dummyBytes)
                    restoredMediaPaths[entryName] = savedPath
                }

                val snapshot = parseSnapshot(preview.rawDatabaseJson) { rawPath ->
                    if (rawPath != null && rawPath.startsWith("media/")) {
                        restoredMediaPaths[rawPath] ?: rawPath
                    } else {
                        rawPath
                    }
                }

                try {
                    database.withTransaction {
                        // Reverse FK deletion order
                        database.photoDao().deleteAllCrossRefs()
                        database.photoDao().deleteAll()
                        database.eventDao().deleteAll()
                        database.educationDao().deleteAll()
                        database.parentChildDao().deleteAll()
                        database.marriageDao().deleteAll()
                        database.familyGroupDao().deleteAll()
                        database.personDao().deleteAll()
                        database.auditLogDao().deleteAll()

                        // Ensure each restored familyId has a local FamilySpaceEntity container row
                        val restoredFamilyIds = (snapshot.persons.map { it.familyId } + snapshot.familyGroups.map { it.familyId }).distinct()
                        val now = System.currentTimeMillis()
                        restoredFamilyIds.forEach { fid ->
                            if (database.familySpaceDao().getById(fid) == null) {
                                database.familySpaceDao().upsert(
                                    FamilySpaceEntity(
                                        id = fid,
                                        name = snapshot.familyTitle.ifBlank { preview.manifest.familyTitle.ifBlank { "عائلة جِذور" } },
                                        motto = snapshot.familyMotto.ifBlank { "نوثّق الأصول، ونحفظ امتداد الأجيال" },
                                        ownerUserId = null,
                                        rootPersonId = snapshot.familyGroups.firstOrNull { it.familyId == fid }?.rootPersonId,
                                        createdAt = now,
                                        updatedAt = now,
                                    )
                                )
                            }
                        }

                        // Forward FK insertion order
                        if (snapshot.familyGroups.isNotEmpty()) {
                            database.familyGroupDao().insertAll(snapshot.familyGroups)
                        }
                        if (snapshot.persons.isNotEmpty()) {
                            database.personDao().insertAll(snapshot.persons)
                        }
                        if (snapshot.marriages.isNotEmpty()) {
                            database.marriageDao().insertAll(snapshot.marriages)
                        }
                        if (snapshot.parentChildLinks.isNotEmpty()) {
                            database.parentChildDao().insertAll(snapshot.parentChildLinks)
                        }
                        if (snapshot.education.isNotEmpty()) {
                            database.educationDao().insertAll(snapshot.education)
                        }
                        if (snapshot.photos.isNotEmpty()) {
                            database.photoDao().insertAll(snapshot.photos)
                        }
                        if (snapshot.crossRefs.isNotEmpty()) {
                            database.photoDao().insertCrossRefs(snapshot.crossRefs)
                        }
                        if (snapshot.events.isNotEmpty()) {
                            database.eventDao().insertAll(snapshot.events)
                        }
                        if (snapshot.auditLogs.isNotEmpty()) {
                            database.auditLogDao().insertAll(snapshot.auditLogs)
                        }

                        // Verify restored row counts inside the transaction before commit
                        val verifiedPersons = database.personDao().getAllOnce().size
                        val verifiedMarriages = database.marriageDao().getAllOnce().size
                        val verifiedLinks = database.parentChildDao().getAllOnce().size
                        val verifiedPhotos = database.photoDao().getAllOnce().size
                        if (verifiedPersons != preview.manifest.counts.personsCount ||
                            verifiedMarriages != preview.manifest.counts.marriagesCount ||
                            verifiedLinks != preview.manifest.counts.parentChildCount ||
                            verifiedPhotos != preview.manifest.counts.photosCount
                        ) {
                            error("عدم تطابق أعداد السجلات المستعادة مع بيان النسخة الاحتياطية؛ تم التراجع تلقائياً.")
                        }

                        database.auditLogDao().insert(
                            AuditLogEntity(
                                id = UUID.randomUUID().toString(),
                                entityType = "BACKUP_RESTORE",
                                entityId = "zip_restore",
                                operation = AuditOperation.RESTORE,
                                oldValue = null,
                                newValue = "استعادة نسخة احتياطية: ${verifiedPersons} فرداً، ${verifiedMarriages} زواجاً",
                                operationDate = System.currentTimeMillis(),
                            )
                        )
                    }
                } catch (t: Throwable) {
                    // Roll back staged restored files if DB transaction rolled back
                    restoredMediaPaths.values.forEach { mediaStorage.deleteFileIfManaged(it) }
                    throw t
                }

                if (snapshot.familyTitle.isNotBlank()) {
                    preferencesRepository.updateFamilyIdentity(
                        title = snapshot.familyTitle,
                        motto = snapshot.familyMotto.ifBlank { "نوثّق الأصول، ونحفظ امتداد الأجيال" },
                    )
                }

                RestoreResult(
                    restoredCounts = preview.manifest.counts,
                    familyTitle = snapshot.familyTitle.ifBlank { preview.manifest.familyTitle },
                )
            }
        }

    private data class ParsedSnapshot(
        val familyTitle: String,
        val familyMotto: String,
        val persons: List<PersonEntity>,
        val marriages: List<MarriageEntity>,
        val parentChildLinks: List<ParentChildEntity>,
        val education: List<EducationEntity>,
        val photos: List<PhotoEntity>,
        val crossRefs: List<PhotoPersonCrossRef>,
        val events: List<EventEntity>,
        val familyGroups: List<FamilyGroupEntity>,
        val auditLogs: List<AuditLogEntity>,
    )

    private fun parseSnapshot(
        rawJson: String,
        remapMediaPath: (String?) -> String?,
    ): ParsedSnapshot {
        val root = JSONObject(rawJson)
        val schemaVersion = root.optInt("schemaVersion", SUPPORTED_SCHEMA_VERSION)
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            error("إصدار مخطط البيانات غير مدعوم.")
        }

        val persons = root.optJSONArray("persons").toEntityList { obj ->
            PersonEntity(
                id = obj.getString("id"),
                fullName = obj.getString("fullName"),
                firstName = obj.getString("firstName"),
                middleName = obj.optNullableString("middleName"),
                familyName = obj.optNullableString("familyName"),
                gender = Gender.valueOf(obj.getString("gender")),
                birthDate = obj.optNullableString("birthDate"),
                birthDatePrecision = DatePrecision.valueOf(obj.optString("birthDatePrecision", DatePrecision.UNKNOWN.name)),
                birthPlace = obj.optNullableString("birthPlace"),
                deathDate = obj.optNullableString("deathDate"),
                deathDatePrecision = DatePrecision.valueOf(obj.optString("deathDatePrecision", DatePrecision.UNKNOWN.name)),
                deathPlace = obj.optNullableString("deathPlace"),
                biography = obj.optNullableString("biography"),
                occupation = obj.optNullableString("occupation"),
                currentMaritalStatus = MaritalStatus.valueOf(obj.optString("currentMaritalStatus", MaritalStatus.SINGLE.name)),
                profileImagePath = remapMediaPath(obj.optNullableString("profileImagePath")),
                familyGroupId = obj.optNullableString("familyGroupId"),
                notes = obj.optNullableString("notes"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                remoteAvatarKey = obj.optNullableString("remoteAvatarKey"),
                avatarSha256 = obj.optNullableString("avatarSha256"),
                version = obj.optLong("version", 1L),
            )
        }

        val marriages = root.optJSONArray("marriages").toEntityList { obj ->
            MarriageEntity(
                id = obj.getString("id"),
                partnerOneId = obj.getString("partnerOneId"),
                partnerTwoId = obj.getString("partnerTwoId"),
                marriageDate = obj.optNullableString("marriageDate"),
                marriageDatePrecision = DatePrecision.valueOf(obj.optString("marriageDatePrecision", DatePrecision.UNKNOWN.name)),
                marriagePlace = obj.optNullableString("marriagePlace"),
                marriageStatus = MarriageRecordStatus.valueOf(obj.optString("marriageStatus", MarriageRecordStatus.ACTIVE.name)),
                endDate = obj.optNullableString("endDate"),
                endReason = obj.optNullableString("endReason"),
                notes = obj.optNullableString("notes"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                version = obj.optLong("version", 1L),
            )
        }

        val parentChildLinks = root.optJSONArray("parentChildLinks").toEntityList { obj ->
            ParentChildEntity(
                id = obj.getString("id"),
                parentId = obj.getString("parentId"),
                childId = obj.getString("childId"),
                relationshipType = ParentRelationshipType.valueOf(obj.getString("relationshipType")),
                marriageId = obj.optNullableString("marriageId"),
                notes = obj.optNullableString("notes"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                version = obj.optLong("version", 1L),
                updatedAt = obj.optLong("updatedAt", obj.optLong("createdAt", System.currentTimeMillis())),
            )
        }

        val education = root.optJSONArray("education").toEntityList { obj ->
            val now = System.currentTimeMillis()
            EducationEntity(
                id = obj.getString("id"),
                personId = obj.getString("personId"),
                qualification = obj.getString("qualification"),
                specialization = obj.optNullableString("specialization"),
                institution = obj.optNullableString("institution"),
                graduationYear = if (obj.isNull("graduationYear")) null else obj.getInt("graduationYear"),
                notes = obj.optNullableString("notes"),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                version = obj.optLong("version", 1L),
                createdAt = obj.optLong("createdAt", now),
                updatedAt = obj.optLong("updatedAt", now),
            )
        }

        val photos = root.optJSONArray("photos").toEntityList { obj ->
            PhotoEntity(
                id = obj.getString("id"),
                personId = obj.optNullableString("personId"),
                filePath = remapMediaPath(obj.getString("filePath")) ?: obj.getString("filePath"),
                description = obj.optNullableString("description"),
                photoDate = obj.optNullableString("photoDate"),
                photoDatePrecision = DatePrecision.valueOf(obj.optString("photoDatePrecision", DatePrecision.UNKNOWN.name)),
                mediaType = MediaType.valueOf(obj.optString("mediaType", MediaType.PERSON_PORTRAIT.name)),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                remoteStorageKey = obj.optNullableString("remoteStorageKey"),
                sha256 = obj.optNullableString("sha256"),
                version = obj.optLong("version", 1L),
                updatedAt = obj.optLong("updatedAt", obj.optLong("createdAt", System.currentTimeMillis())),
            )
        }

        val crossRefs = root.optJSONArray("photoPersonCrossRefs").toEntityList { obj ->
            PhotoPersonCrossRef(
                photoId = obj.getString("photoId"),
                personId = obj.getString("personId"),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            )
        }

        val events = root.optJSONArray("events").toEntityList { obj ->
            val now = System.currentTimeMillis()
            EventEntity(
                id = obj.getString("id"),
                personId = obj.optNullableString("personId"),
                eventType = FamilyEventType.valueOf(obj.getString("eventType")),
                eventDate = obj.optNullableString("eventDate"),
                eventDatePrecision = DatePrecision.valueOf(obj.optString("eventDatePrecision", DatePrecision.UNKNOWN.name)),
                eventPlace = obj.optNullableString("eventPlace"),
                description = obj.optNullableString("description"),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                version = obj.optLong("version", 1L),
                createdAt = obj.optLong("createdAt", now),
                updatedAt = obj.optLong("updatedAt", now),
            )
        }

        val familyGroups = root.optJSONArray("familyGroups").toEntityList { obj ->
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            FamilyGroupEntity(
                id = obj.getString("id"),
                groupName = obj.getString("groupName"),
                description = obj.optNullableString("description"),
                rootPersonId = obj.optNullableString("rootPersonId"),
                createdAt = createdAt,
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                version = obj.optLong("version", 1L),
                updatedAt = obj.optLong("updatedAt", createdAt),
            )
        }

        val auditLogs = root.optJSONArray("auditLogs").toEntityList { obj ->
            AuditLogEntity(
                id = obj.getString("id"),
                entityType = obj.getString("entityType"),
                entityId = obj.getString("entityId"),
                operation = AuditOperation.valueOf(obj.getString("operation")),
                oldValue = obj.optNullableString("oldValue"),
                newValue = obj.optNullableString("newValue"),
                operationDate = obj.optLong("operationDate", System.currentTimeMillis()),
                familyId = obj.optString("familyId", com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID),
                actorUserId = obj.optNullableString("actorUserId"),
            )
        }

        return ParsedSnapshot(
            familyTitle = root.optString("familyTitle", ""),
            familyMotto = root.optString("familyMotto", ""),
            persons = persons,
            marriages = marriages,
            parentChildLinks = parentChildLinks,
            education = education,
            photos = photos,
            crossRefs = crossRefs,
            events = events,
            familyGroups = familyGroups,
            auditLogs = auditLogs,
        )
    }

    private fun parseManifest(obj: JSONObject): BackupManifest {
        val countsObj = obj.getJSONObject("counts")
        val counts = BackupCounts(
            personsCount = countsObj.getInt("personsCount"),
            marriagesCount = countsObj.getInt("marriagesCount"),
            parentChildCount = countsObj.getInt("parentChildCount"),
            educationCount = countsObj.optInt("educationCount", 0),
            photosCount = countsObj.optInt("photosCount", 0),
            eventsCount = countsObj.optInt("eventsCount", 0),
            familyGroupsCount = countsObj.optInt("familyGroupsCount", 0),
            auditLogsCount = countsObj.optInt("auditLogsCount", 0),
            mediaFilesCount = countsObj.optInt("mediaFilesCount", 0),
        )
        val mediaShaObj = obj.optJSONObject("mediaSha256") ?: JSONObject()
        val mediaShaMap = linkedMapOf<String, String>()
        mediaShaObj.keys().forEach { key ->
            mediaShaMap[key] = mediaShaObj.getString(key)
        }
        return BackupManifest(
            formatVersion = obj.getInt("formatVersion"),
            schemaVersion = obj.getInt("schemaVersion"),
            appVersion = obj.optString("appVersion", APP_VERSION),
            exportedAtMillis = obj.getLong("exportedAtMillis"),
            familyTitle = obj.optString("familyTitle", "عائلة جِذور"),
            familyMotto = obj.optString("familyMotto", ""),
            counts = counts,
            databaseSha256 = obj.getString("databaseSha256"),
            mediaSha256 = mediaShaMap,
            overallPackageSha256 = obj.getString("overallPackageSha256"),
        )
    }

    private fun BackupManifest.toJson(): JSONObject = JSONObject().apply {
        put("formatVersion", formatVersion)
        put("schemaVersion", schemaVersion)
        put("appVersion", appVersion)
        put("exportedAtMillis", exportedAtMillis)
        put("familyTitle", familyTitle)
        put("familyMotto", familyMotto)
        put(
            "counts",
            JSONObject().apply {
                put("personsCount", counts.personsCount)
                put("marriagesCount", counts.marriagesCount)
                put("parentChildCount", counts.parentChildCount)
                put("educationCount", counts.educationCount)
                put("photosCount", counts.photosCount)
                put("eventsCount", counts.eventsCount)
                put("familyGroupsCount", counts.familyGroupsCount)
                put("auditLogsCount", counts.auditLogsCount)
                put("mediaFilesCount", counts.mediaFilesCount)
            },
        )
        put("databaseSha256", databaseSha256)
        put(
            "mediaSha256",
            JSONObject().apply {
                mediaSha256.forEach { (k, v) -> put(k, v) }
            },
        )
        put("overallPackageSha256", overallPackageSha256)
    }

    private fun computeOverallDigest(
        formatVersion: Int,
        schemaVersion: Int,
        counts: BackupCounts,
        databaseSha256: String,
        mediaShaMap: Map<String, String>,
    ): String {
        val sb = StringBuilder()
        sb.append("fmt=").append(formatVersion)
            .append("|schema=").append(schemaVersion)
            .append("|counts=").append(counts.personsCount).append(',')
            .append(counts.marriagesCount).append(',')
            .append(counts.parentChildCount).append(',')
            .append(counts.educationCount).append(',')
            .append(counts.photosCount).append(',')
            .append(counts.eventsCount).append(',')
            .append(counts.familyGroupsCount).append(',')
            .append(counts.mediaFilesCount)
            .append("|db=").append(databaseSha256.lowercase())
        mediaShaMap.entries.sortedBy { it.key }.forEach { (k, v) ->
            sb.append("|media=").append(k).append(':').append(v.lowercase())
        }
        return sha256Hex(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun validateZipEntryName(name: String) {
        if (name.isBlank() || name.startsWith("/") || name.contains("..") || name.contains("\\")) {
            error("مسار غير آمن داخل ملف النسخة الاحتياطية: $name")
        }
    }

    private fun readEntryBytesBounded(input: InputStream): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) {
                error("حجم أحد الملفات داخل النسخة الاحتياطية يتجاوز الحد الآمن.")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "asset.bin" }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    private inline fun <T> JSONArray?.toEntityList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val list = ArrayList<T>(length())
        for (i in 0 until length()) {
            list.add(mapper(getJSONObject(i)))
        }
        return list
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (!has(key) || isNull(key)) null else getString(key)
    }

    private fun PersonEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("fullName", fullName)
        put("firstName", firstName)
        put("middleName", middleName)
        put("familyName", familyName)
        put("gender", gender.name)
        put("birthDate", birthDate)
        put("birthDatePrecision", birthDatePrecision.name)
        put("birthPlace", birthPlace)
        put("deathDate", deathDate)
        put("deathDatePrecision", deathDatePrecision.name)
        put("deathPlace", deathPlace)
        put("biography", biography)
        put("occupation", occupation)
        put("currentMaritalStatus", currentMaritalStatus.name)
        put("profileImagePath", profileImagePath)
        put("familyGroupId", familyGroupId)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("familyId", familyId)
        put("remoteAvatarKey", remoteAvatarKey)
        put("avatarSha256", avatarSha256)
        put("version", version)
    }

    private fun MarriageEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("partnerOneId", partnerOneId)
        put("partnerTwoId", partnerTwoId)
        put("marriageDate", marriageDate)
        put("marriageDatePrecision", marriageDatePrecision.name)
        put("marriagePlace", marriagePlace)
        put("marriageStatus", marriageStatus.name)
        put("endDate", endDate)
        put("endReason", endReason)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("familyId", familyId)
        put("version", version)
    }

    private fun ParentChildEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("parentId", parentId)
        put("childId", childId)
        put("relationshipType", relationshipType.name)
        put("marriageId", marriageId)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("familyId", familyId)
        put("version", version)
    }

    private fun EducationEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("personId", personId)
        put("qualification", qualification)
        put("specialization", specialization)
        put("institution", institution)
        put("graduationYear", graduationYear)
        put("notes", notes)
        put("familyId", familyId)
        put("version", version)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun PhotoEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("personId", personId)
        put("filePath", filePath)
        put("description", description)
        put("photoDate", photoDate)
        put("photoDatePrecision", photoDatePrecision.name)
        put("mediaType", mediaType.name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("familyId", familyId)
        put("remoteStorageKey", remoteStorageKey)
        put("sha256", sha256)
        put("version", version)
    }

    private fun PhotoPersonCrossRef.toJson() = JSONObject().apply {
        put("photoId", photoId)
        put("personId", personId)
        put("familyId", familyId)
        put("updatedAt", updatedAt)
    }

    private fun EventEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("personId", personId)
        put("eventType", eventType.name)
        put("eventDate", eventDate)
        put("eventDatePrecision", eventDatePrecision.name)
        put("eventPlace", eventPlace)
        put("description", description)
        put("familyId", familyId)
        put("version", version)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun FamilyGroupEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("groupName", groupName)
        put("description", description)
        put("rootPersonId", rootPersonId)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("familyId", familyId)
        put("version", version)
    }

    private fun AuditLogEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("entityType", entityType)
        put("entityId", entityId)
        put("operation", operation.name)
        put("oldValue", oldValue)
        put("newValue", newValue)
        put("operationDate", operationDate)
        put("familyId", familyId)
        put("actorUserId", actorUserId)
    }
}
