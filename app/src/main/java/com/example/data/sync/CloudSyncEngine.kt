package com.example.data.sync

import androidx.room.withTransaction
import com.example.data.local.JuthoorDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.EducationEntity
import com.example.data.local.entity.EventEntity
import com.example.data.local.entity.FamilyGroupEntity
import com.example.data.local.entity.FamilyMemberEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.MarriageEntity
import com.example.data.local.entity.ParentChildEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.local.entity.PhotoEntity
import com.example.data.local.entity.PhotoPersonCrossRef
import com.example.data.local.entity.SyncConflictEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.data.remote.RemotePushOutcome
import com.example.data.remote.SupabaseGateway
import com.example.domain.model.AuditOperation
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.DatePrecision
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.SyncCycleReport
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CloudSyncEngine(
    private val database: JuthoorDatabase,
    private val supabaseGateway: SupabaseGateway,
    private val mediaStorage: LocalMediaStorage?,
    private val preferencesRepository: PreferencesRepository,
) {
    private val familySpaceDao = database.familySpaceDao()
    private val familyMemberDao = database.familyMemberDao()
    private val userAccountDao = database.userAccountDao()
    private val familyGroupDao = database.familyGroupDao()
    private val personDao = database.personDao()
    private val marriageDao = database.marriageDao()
    private val parentChildDao = database.parentChildDao()
    private val educationDao = database.educationDao()
    private val photoDao = database.photoDao()
    private val eventDao = database.eventDao()
    private val syncOutboxDao = database.syncOutboxDao()
    private val syncMetadataDao = database.syncMetadataDao()
    private val syncConflictDao = database.syncConflictDao()
    private val auditLogDao = database.auditLogDao()

    suspend fun syncFamily(
        accessToken: String,
        currentUserId: String,
        familyId: String,
    ): SyncCycleReport {
        val now = System.currentTimeMillis()
        ensurePendingRecordsQueued(familyId, now)

        val mediaUploads = uploadPendingMediaIfNeeded(accessToken, familyId)
        val pushSummary = pushOutbox(accessToken, currentUserId, familyId)

        val previousWatermark = syncMetadataDao.get(familyId, SyncEntityType.FAMILY)?.lastPulledUpdatedAt ?: 0L
        val delta = supabaseGateway.pullFamilyDelta(
            accessToken = accessToken,
            familyId = familyId,
            sinceUpdatedAt = previousWatermark,
        ).getOrThrow()

        val pulledCount = applyDeltaTransactionally(familyId, delta.rawJson, delta.serverTimeMillis)
        val mediaDownloads = downloadMissingMediaIfNeeded(accessToken, familyId)

        val finishTime = System.currentTimeMillis()
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                familyId = familyId,
                entityType = SyncEntityType.FAMILY,
                lastPulledUpdatedAt = maxOf(previousWatermark, delta.serverTimeMillis),
                lastSyncedAt = finishTime,
            )
        )

        return SyncCycleReport(
            pushedCount = pushSummary.pushedCount,
            pulledCount = pulledCount,
            conflictCount = pushSummary.conflictCount,
            uploadedMediaCount = mediaUploads,
            downloadedMediaCount = mediaDownloads,
            syncedAtMillis = finishTime,
        )
    }

    suspend fun resolveConflict(
        accessToken: String,
        currentUserId: String,
        conflictId: String,
        choice: ConflictResolutionChoice,
    ) {
        val conflict = syncConflictDao.getById(conflictId)
            ?: error("تعذر العثور على سجل التعارض.")
        if (conflict.resolved) return

        when (choice) {
            ConflictResolutionChoice.KEEP_LOCAL -> {
                val payloadJson = buildCurrentPayloadJson(conflict.entityType, conflict.entityId)
                    ?: conflict.localPayloadJson
                val operation = determineOperation(conflict.entityType, conflict.entityId)
                val outcome = supabaseGateway.pushGenealogyMutation(
                    accessToken = accessToken,
                    familyId = conflict.familyId,
                    entityType = conflict.entityType.name,
                    entityId = conflict.entityId,
                    operation = operation.name,
                    clientVersion = maxOf(conflict.localVersion, conflict.serverVersion),
                    forceOverwrite = true,
                    payloadJson = payloadJson,
                ).getOrThrow()

                if (outcome is RemotePushOutcome.Applied) {
                    database.withTransaction {
                        markEntitySynced(conflict.entityType, conflict.entityId, outcome.version, outcome.updatedAtMillis)
                        syncOutboxDao.deleteForEntity(conflict.familyId, conflict.entityType, conflict.entityId)
                        syncConflictDao.markResolved(conflictId)
                        auditLogDao.insert(
                            AuditLogEntity(
                                id = UUID.randomUUID().toString(),
                                entityType = "SyncConflicts",
                                entityId = conflict.entityId,
                                operation = AuditOperation.UPDATE,
                                oldValue = "v${conflict.serverVersion}",
                                newValue = "حل التعارض: الاحتفاظ بالنسخة المحلية (v${outcome.version})",
                                operationDate = outcome.updatedAtMillis,
                                familyId = conflict.familyId,
                                actorUserId = currentUserId,
                                syncState = SyncState.LOCAL_ONLY,
                            )
                        )
                    }
                }
            }

            ConflictResolutionChoice.USE_SERVER -> {
                val serverObj = JSONObject(conflict.serverPayloadJson)
                val now = System.currentTimeMillis()
                database.withTransaction {
                    applySingleServerObject(conflict.familyId, conflict.entityType, serverObj, now)
                    syncOutboxDao.deleteForEntity(conflict.familyId, conflict.entityType, conflict.entityId)
                    syncConflictDao.markResolved(conflictId)
                    auditLogDao.insert(
                        AuditLogEntity(
                            id = UUID.randomUUID().toString(),
                            entityType = "SyncConflicts",
                            entityId = conflict.entityId,
                            operation = AuditOperation.UPDATE,
                            oldValue = "v${conflict.localVersion}",
                            newValue = "حل التعارض: اعتماد نسخة الخادم (v${conflict.serverVersion})",
                            operationDate = now,
                            familyId = conflict.familyId,
                            actorUserId = currentUserId,
                            syncState = SyncState.LOCAL_ONLY,
                        )
                    )
                }
                downloadMissingMediaIfNeeded(accessToken, conflict.familyId)
            }
        }
    }

    private suspend fun ensurePendingRecordsQueued(familyId: String, now: Long) {
        suspend fun ensure(type: SyncEntityType, entityId: String, version: Long, isDeleted: Boolean) {
            val active = syncOutboxDao.findActiveForEntity(familyId, type, entityId)
            if (active == null) {
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        id = UUID.randomUUID().toString(),
                        familyId = familyId,
                        entityType = type,
                        entityId = entityId,
                        operation = if (isDeleted) SyncOutboxOperation.SOFT_DELETE else SyncOutboxOperation.UPSERT,
                        payloadJson = JSONObject().put("id", entityId).toString(),
                        baseVersion = version,
                        status = SyncOutboxStatus.PENDING,
                        retryCount = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }

        familyGroupDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.FAMILY_GROUP, it.id, it.version, it.isDeleted) }
        personDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.PERSON, it.id, it.version, it.isDeleted) }
        marriageDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.MARRIAGE, it.id, it.version, it.isDeleted) }
        parentChildDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.PARENT_CHILD, it.id, it.version, it.isDeleted) }
        educationDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.EDUCATION, it.id, it.version, it.isDeleted) }
        photoDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.PHOTO, it.id, it.version, it.isDeleted) }
        eventDao.getAllForFamilyOnce(familyId)
            .filter { it.syncState == SyncState.PENDING_UPSERT || it.syncState == SyncState.PENDING_DELETE }
            .forEach { ensure(SyncEntityType.EVENT, it.id, it.version, it.isDeleted) }
    }

    private suspend fun uploadPendingMediaIfNeeded(accessToken: String, familyId: String): Int {
        val storage = mediaStorage ?: return 0
        var uploaded = 0

        personDao.getAllForFamilyOnce(familyId).forEach { person ->
            if (!person.isDeleted && !person.profileImagePath.isNullOrBlank()) {
                val bytes = storage.readBytesIfExists(person.profileImagePath)
                if (bytes != null && bytes.isNotEmpty()) {
                    val sha = storage.computeSha256(bytes)
                    if (person.remoteAvatarKey.isNullOrBlank() || person.avatarSha256 != sha) {
                        val (ext, mime) = storage.detectExtensionAndMime(person.profileImagePath)
                        val objectKey = "$familyId/avatar_${person.id}_${sha.take(8)}.$ext"
                        val result = supabaseGateway.uploadFamilyMedia(accessToken, objectKey, bytes, mime)
                        if (result.isSuccess) {
                            personDao.upsert(
                                person.copy(
                                    remoteAvatarKey = objectKey,
                                    avatarSha256 = sha,
                                    syncState = if (person.syncState == SyncState.SYNCED) SyncState.PENDING_UPSERT else person.syncState,
                                )
                            )
                            uploaded++
                        }
                    }
                }
            }
        }

        photoDao.getAllForFamilyOnce(familyId).forEach { photo ->
            if (!photo.isDeleted && photo.filePath.isNotBlank()) {
                val bytes = storage.readBytesIfExists(photo.filePath)
                if (bytes != null && bytes.isNotEmpty()) {
                    val sha = storage.computeSha256(bytes)
                    if (photo.remoteStorageKey.isNullOrBlank() || photo.sha256 != sha) {
                        val (ext, mime) = storage.detectExtensionAndMime(photo.filePath)
                        val objectKey = "$familyId/photo_${photo.id}_${sha.take(8)}.$ext"
                        val result = supabaseGateway.uploadFamilyMedia(accessToken, objectKey, bytes, mime)
                        if (result.isSuccess) {
                            photoDao.upsert(
                                photo.copy(
                                    remoteStorageKey = objectKey,
                                    sha256 = sha,
                                    syncState = if (photo.syncState == SyncState.SYNCED) SyncState.PENDING_UPSERT else photo.syncState,
                                )
                            )
                            uploaded++
                        }
                    }
                }
            }
        }

        return uploaded
    }

    private suspend fun downloadMissingMediaIfNeeded(accessToken: String, familyId: String): Int {
        val storage = mediaStorage ?: return 0
        var downloaded = 0

        personDao.getAllForFamilyOnce(familyId).forEach { person ->
            val key = person.remoteAvatarKey
            if (!person.isDeleted && !key.isNullOrBlank()) {
                val existingBytes = storage.readBytesIfExists(person.profileImagePath)
                val existingSha = existingBytes?.let { storage.computeSha256(it) }
                val needsDownload = existingBytes == null || (!person.avatarSha256.isNullOrBlank() && existingSha != person.avatarSha256)
                if (needsDownload) {
                    val bytes = supabaseGateway.downloadFamilyMedia(accessToken, key).getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val fileName = key.substringAfterLast("/").ifBlank { "avatar_${person.id}.jpg" }
                        val savedPath = storage.saveBytesForRestore(fileName, bytes)
                        personDao.upsert(
                            person.copy(
                                profileImagePath = savedPath,
                                avatarSha256 = person.avatarSha256 ?: storage.computeSha256(bytes),
                            )
                        )
                        downloaded++
                    }
                }
            }
        }

        photoDao.getAllForFamilyOnce(familyId).forEach { photo ->
            val key = photo.remoteStorageKey
            if (!photo.isDeleted && !key.isNullOrBlank()) {
                val existingBytes = storage.readBytesIfExists(photo.filePath)
                val existingSha = existingBytes?.let { storage.computeSha256(it) }
                val needsDownload = existingBytes == null || (!photo.sha256.isNullOrBlank() && existingSha != photo.sha256)
                if (needsDownload) {
                    val bytes = supabaseGateway.downloadFamilyMedia(accessToken, key).getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val fileName = key.substringAfterLast("/").ifBlank { "photo_${photo.id}.jpg" }
                        val savedPath = storage.saveBytesForRestore(fileName, bytes)
                        photoDao.upsert(
                            photo.copy(
                                filePath = savedPath,
                                sha256 = photo.sha256 ?: storage.computeSha256(bytes),
                            )
                        )
                        downloaded++
                    }
                }
            }
        }

        return downloaded
    }

    private data class PushSummary(val pushedCount: Int, val conflictCount: Int)

    private suspend fun pushOutbox(
        accessToken: String,
        currentUserId: String,
        familyId: String,
    ): PushSummary {
        val pending = syncOutboxDao.getPendingForFamily(familyId)
            .sortedWith(compareBy<SyncOutboxEntity> { pushOrder(it.entityType) }.thenBy { it.createdAt })

        var pushed = 0
        var conflicts = 0

        for (entry in pending) {
            val payloadJson = buildCurrentPayloadJson(entry.entityType, entry.entityId) ?: entry.payloadJson
            val clientVersion = resolveClientVersion(entry.entityType, entry.entityId, entry.baseVersion)

            val res = supabaseGateway.pushGenealogyMutation(
                accessToken = accessToken,
                familyId = familyId,
                entityType = entry.entityType.name,
                entityId = entry.entityId,
                operation = entry.operation.name,
                clientVersion = clientVersion,
                forceOverwrite = false,
                payloadJson = payloadJson,
            )

            res.onSuccess { outcome ->
                val now = System.currentTimeMillis()
                when (outcome) {
                    is RemotePushOutcome.Applied -> {
                        database.withTransaction {
                            markEntitySynced(entry.entityType, entry.entityId, outcome.version, outcome.updatedAtMillis)
                            syncOutboxDao.deleteById(entry.id)
                        }
                        pushed++
                    }

                    is RemotePushOutcome.Conflict -> {
                        database.withTransaction {
                            markEntityConflicted(entry.entityType, entry.entityId)
                            syncOutboxDao.updateStatus(
                                id = entry.id,
                                status = SyncOutboxStatus.CONFLICT,
                                retryCount = entry.retryCount + 1,
                                lastError = "تعارض نسخ: الخادم يحمل الإصدار ${outcome.serverVersion}",
                                updatedAt = now,
                            )
                            val existingConflict = syncConflictDao.findUnresolvedForEntity(familyId, entry.entityType, entry.entityId)
                            syncConflictDao.upsert(
                                SyncConflictEntity(
                                    id = existingConflict?.id ?: UUID.randomUUID().toString(),
                                    familyId = familyId,
                                    entityType = entry.entityType,
                                    entityId = entry.entityId,
                                    localVersion = clientVersion,
                                    serverVersion = outcome.serverVersion,
                                    localPayloadJson = payloadJson,
                                    serverPayloadJson = outcome.serverRecordJson,
                                    conflictReason = "تم تعديل ${entry.entityType.arabicLabel} على جهاز آخر (نسختك v$clientVersion مقابل الخادم v${outcome.serverVersion}).",
                                    detectedAt = now,
                                    resolved = false,
                                )
                            )
                            auditLogDao.insert(
                                AuditLogEntity(
                                    id = UUID.randomUUID().toString(),
                                    entityType = "SyncConflicts",
                                    entityId = entry.entityId,
                                    operation = AuditOperation.UPDATE,
                                    oldValue = "v$clientVersion",
                                    newValue = "تعارض مزامنة مع الخادم (v${outcome.serverVersion})",
                                    operationDate = now,
                                    familyId = familyId,
                                    actorUserId = currentUserId,
                                    syncState = SyncState.LOCAL_ONLY,
                                )
                            )
                        }
                        conflicts++
                    }
                }
            }.onFailure { err ->
                syncOutboxDao.updateStatus(
                    id = entry.id,
                    status = SyncOutboxStatus.FAILED,
                    retryCount = entry.retryCount + 1,
                    lastError = err.message ?: "تعذر رفع التعديل للخادم.",
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

        return PushSummary(pushedCount = pushed, conflictCount = conflicts)
    }

    private suspend fun applyDeltaTransactionally(
        familyId: String,
        rawJson: String,
        serverTimeMillis: Long,
    ): Int {
        val root = JSONObject(rawJson)
        var applied = 0
        var updatedFamilyTitleAndMotto: Pair<String, String>? = null

        database.withTransaction {
            val famObj = root.optJSONObject("family")
            if (famObj != null && !hasPendingLocalChange(familyId, SyncEntityType.FAMILY, famObj.optString("id"))) {
                val entity = parseFamilySpace(famObj)
                familySpaceDao.upsert(entity)
                updatedFamilyTitleAndMotto = entity.name to entity.motto
                applied++
            }

            val groupArr = root.optJSONArray("family_groups") ?: JSONArray()
            for (i in 0 until groupArr.length()) {
                val obj = groupArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.FAMILY_GROUP, id)) {
                    familyGroupDao.upsert(parseFamilyGroup(familyId, obj))
                    applied++
                }
            }

            val personArr = root.optJSONArray("persons") ?: JSONArray()
            for (i in 0 until personArr.length()) {
                val obj = personArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.PERSON, id)) {
                    val existing = personDao.getByIdIncludingDeleted(id)
                    personDao.upsert(parsePerson(familyId, obj, existing?.profileImagePath))
                    applied++
                }
            }

            val marriageArr = root.optJSONArray("marriages") ?: JSONArray()
            for (i in 0 until marriageArr.length()) {
                val obj = marriageArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.MARRIAGE, id)) {
                    val p1 = obj.getString("partner_one_id")
                    val p2 = obj.getString("partner_two_id")
                    if (personDao.getByIdIncludingDeleted(p1) != null && personDao.getByIdIncludingDeleted(p2) != null) {
                        marriageDao.upsert(parseMarriage(familyId, obj))
                        applied++
                    }
                }
            }

            val linksArr = root.optJSONArray("parent_child_relationships") ?: JSONArray()
            for (i in 0 until linksArr.length()) {
                val obj = linksArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.PARENT_CHILD, id)) {
                    val parentId = obj.getString("parent_id")
                    val childId = obj.getString("child_id")
                    if (personDao.getByIdIncludingDeleted(parentId) != null && personDao.getByIdIncludingDeleted(childId) != null) {
                        val relType = runCatching {
                            ParentRelationshipType.valueOf(obj.optString("relationship_type", ParentRelationshipType.BIOLOGICAL_FATHER.name))
                        }.getOrDefault(ParentRelationshipType.BIOLOGICAL_FATHER)
                        val matchByTuple = parentChildDao.findExactIncludingDeleted(parentId, childId, relType)
                        if (matchByTuple != null && matchByTuple.id != id) {
                            parentChildDao.deleteById(matchByTuple.id)
                        }
                        val marriageId = obj.optNullableString("marriage_id")
                            ?.takeIf { marriageDao.getByIdIncludingDeleted(it) != null }
                        parentChildDao.upsert(parseParentChild(familyId, obj, marriageId))
                        applied++
                    }
                }
            }

            val eduArr = root.optJSONArray("education") ?: JSONArray()
            for (i in 0 until eduArr.length()) {
                val obj = eduArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.EDUCATION, id)) {
                    val personId = obj.getString("person_id")
                    if (personDao.getByIdIncludingDeleted(personId) != null) {
                        educationDao.upsert(parseEducation(familyId, obj))
                        applied++
                    }
                }
            }

            val photosArr = root.optJSONArray("person_photos") ?: JSONArray()
            val pulledTags = root.optJSONArray("photo_person_tags") ?: JSONArray()
            val tagsByPhotoId = mutableMapOf<String, MutableList<PhotoPersonCrossRef>>()
            for (i in 0 until pulledTags.length()) {
                val tObj = pulledTags.getJSONObject(i)
                val photoId = tObj.optString("photo_id")
                val personId = tObj.optString("person_id")
                if (photoId.isNotBlank() && personId.isNotBlank()) {
                    tagsByPhotoId.getOrPut(photoId) { mutableListOf() }.add(
                        PhotoPersonCrossRef(
                            photoId = photoId,
                            personId = personId,
                            familyId = familyId,
                            isDeleted = tObj.optBoolean("is_deleted", false),
                            updatedAt = tObj.optLong("updated_at", serverTimeMillis),
                            syncState = SyncState.SYNCED,
                        )
                    )
                }
            }

            for (i in 0 until photosArr.length()) {
                val obj = photosArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.PHOTO, id)) {
                    val existing = photoDao.getByIdIncludingDeleted(id)
                    val personId = obj.optNullableString("person_id")
                        ?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
                    val photoEntity = parsePhoto(familyId, obj, personId, existing?.filePath.orEmpty())
                    photoDao.upsert(photoEntity)
                    val photoTags = tagsByPhotoId[id].orEmpty()
                        .filter { !it.isDeleted && personDao.getByIdIncludingDeleted(it.personId) != null }
                    if (photoTags.isNotEmpty() || obj.optBoolean("is_deleted", false)) {
                        photoDao.clearCrossRefsForPhoto(id)
                        if (photoTags.isNotEmpty() && !photoEntity.isDeleted) {
                            photoDao.insertCrossRefs(photoTags)
                        }
                    }
                    applied++
                }
            }

            val eventsArr = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until eventsArr.length()) {
                val obj = eventsArr.getJSONObject(i)
                val id = obj.getString("id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.EVENT, id)) {
                    val personId = obj.optNullableString("person_id")
                        ?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
                    eventDao.upsert(parseEvent(familyId, obj, personId))
                    applied++
                }
            }

            val membersArr = root.optJSONArray("family_members") ?: JSONArray()
            for (i in 0 until membersArr.length()) {
                val obj = membersArr.getJSONObject(i)
                val memberId = obj.getString("member_id")
                if (!hasPendingLocalChange(familyId, SyncEntityType.FAMILY_MEMBER, memberId)) {
                    val userId = obj.getString("user_id")
                    val email = obj.optString("email", "")
                    val displayName = obj.optNullableString("display_name") ?: email.substringBefore("@").ifBlank { "عضو" }
                    val existingUser = userAccountDao.getById(userId)
                    if (email.isNotBlank()) {
                        userAccountDao.upsert(
                            UserAccountEntity(
                                id = userId,
                                email = email,
                                displayName = displayName,
                                avatarStorageKey = existingUser?.avatarStorageKey,
                                createdAt = existingUser?.createdAt ?: serverTimeMillis,
                                updatedAt = serverTimeMillis,
                            )
                        )
                    }
                    val linkedId = obj.optNullableString("linked_person_id")
                        ?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
                    if (!linkedId.isNullOrBlank()) {
                        val stale = familyMemberDao.getActiveByLinkedPerson(familyId, linkedId)
                        if (stale != null && stale.id != memberId) {
                            familyMemberDao.upsert(stale.copy(linkedPersonId = null, updatedAt = serverTimeMillis))
                        }
                    }
                    val branchId = obj.optNullableString("branch_root_person_id")
                        ?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
                    familyMemberDao.upsert(
                        FamilyMemberEntity(
                            id = memberId,
                            familyId = familyId,
                            userId = userId,
                            linkedPersonId = linkedId,
                            role = runCatching { FamilyRole.valueOf(obj.optString("role", FamilyRole.MEMBER.name)) }
                                .getOrDefault(FamilyRole.MEMBER),
                            branchRootPersonId = branchId,
                            canInvite = obj.optBoolean("can_invite", false),
                            canExport = obj.optBoolean("can_export", true),
                            canManageMedia = obj.optBoolean("can_manage_media", false),
                            status = runCatching { MembershipStatus.valueOf(obj.optString("status", MembershipStatus.ACTIVE.name)) }
                                .getOrDefault(MembershipStatus.ACTIVE),
                            invitedByUserId = obj.optNullableString("invited_by_user_id"),
                            joinedAt = obj.optLong("joined_at", serverTimeMillis),
                            updatedAt = obj.optLong("updated_at", serverTimeMillis),
                            version = obj.optLong("version", 1L),
                            syncState = SyncState.SYNCED,
                        )
                    )
                    applied++
                }
            }
        }

        updatedFamilyTitleAndMotto?.let { (title, motto) ->
            preferencesRepository.updateFamilyIdentity(title = title, motto = motto)
        }

        return applied
    }

    private suspend fun applySingleServerObject(
        familyId: String,
        entityType: SyncEntityType,
        obj: JSONObject,
        now: Long,
    ) {
        when (entityType) {
            SyncEntityType.FAMILY -> familySpaceDao.upsert(parseFamilySpace(obj))
            SyncEntityType.FAMILY_GROUP -> familyGroupDao.upsert(parseFamilyGroup(familyId, obj))
            SyncEntityType.PERSON -> {
                val existing = personDao.getByIdIncludingDeleted(obj.getString("id"))
                personDao.upsert(parsePerson(familyId, obj, existing?.profileImagePath))
            }
            SyncEntityType.MARRIAGE -> marriageDao.upsert(parseMarriage(familyId, obj))
            SyncEntityType.PARENT_CHILD -> {
                val marriageId = obj.optNullableString("marriage_id")
                parentChildDao.upsert(parseParentChild(familyId, obj, marriageId))
            }
            SyncEntityType.EDUCATION -> educationDao.upsert(parseEducation(familyId, obj))
            SyncEntityType.EVENT -> eventDao.upsert(parseEvent(familyId, obj, obj.optNullableString("person_id")))
            SyncEntityType.PHOTO -> {
                val existing = photoDao.getByIdIncludingDeleted(obj.getString("id"))
                photoDao.upsert(parsePhoto(familyId, obj, obj.optNullableString("person_id"), existing?.filePath.orEmpty()))
            }
            SyncEntityType.FAMILY_MEMBER, SyncEntityType.PHOTO_TAG -> {
                // Handled by delta pull
            }
        }
    }

    private suspend fun hasPendingLocalChange(familyId: String, type: SyncEntityType, entityId: String): Boolean {
        if (syncConflictDao.findUnresolvedForEntity(familyId, type, entityId) != null) return true
        if (syncOutboxDao.findActiveForEntity(familyId, type, entityId) != null) return true
        return false
    }

    private suspend fun buildCurrentPayloadJson(entityType: SyncEntityType, entityId: String): String? {
        return when (entityType) {
            SyncEntityType.FAMILY -> familySpaceDao.getById(entityId)?.let { fam ->
                JSONObject().apply {
                    put("id", fam.id)
                    put("name", fam.name)
                    put("motto", fam.motto)
                    put("root_person_id", fam.rootPersonId ?: JSONObject.NULL)
                    put("deleted_at", fam.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.FAMILY_GROUP -> familyGroupDao.getByIdIncludingDeleted(entityId)?.let { g ->
                JSONObject().apply {
                    put("id", g.id)
                    put("group_name", g.groupName)
                    put("description", g.description ?: JSONObject.NULL)
                    put("root_person_id", g.rootPersonId ?: JSONObject.NULL)
                    put("created_at", g.createdAt)
                    put("deleted_at", g.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.PERSON -> personDao.getByIdIncludingDeleted(entityId)?.let { p ->
                JSONObject().apply {
                    put("id", p.id)
                    put("full_name", p.fullName)
                    put("first_name", p.firstName)
                    put("middle_name", p.middleName ?: JSONObject.NULL)
                    put("family_name", p.familyName ?: JSONObject.NULL)
                    put("gender", p.gender.name)
                    put("birth_date", p.birthDate ?: JSONObject.NULL)
                    put("birth_date_precision", p.birthDatePrecision.name)
                    put("birth_place", p.birthPlace ?: JSONObject.NULL)
                    put("death_date", p.deathDate ?: JSONObject.NULL)
                    put("death_date_precision", p.deathDatePrecision.name)
                    put("death_place", p.deathPlace ?: JSONObject.NULL)
                    put("biography", p.biography ?: JSONObject.NULL)
                    put("occupation", p.occupation ?: JSONObject.NULL)
                    put("current_marital_status", p.currentMaritalStatus.name)
                    put("remote_avatar_key", p.remoteAvatarKey ?: JSONObject.NULL)
                    put("avatar_sha256", p.avatarSha256 ?: JSONObject.NULL)
                    put("family_group_id", p.familyGroupId ?: JSONObject.NULL)
                    put("notes", p.notes ?: JSONObject.NULL)
                    put("created_at", p.createdAt)
                    put("deleted_at", p.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.MARRIAGE -> marriageDao.getByIdIncludingDeleted(entityId)?.let { m ->
                JSONObject().apply {
                    put("id", m.id)
                    put("partner_one_id", m.partnerOneId)
                    put("partner_two_id", m.partnerTwoId)
                    put("marriage_date", m.marriageDate ?: JSONObject.NULL)
                    put("marriage_date_precision", m.marriageDatePrecision.name)
                    put("marriage_place", m.marriagePlace ?: JSONObject.NULL)
                    put("marriage_status", m.marriageStatus.name)
                    put("end_date", m.endDate ?: JSONObject.NULL)
                    put("end_reason", m.endReason ?: JSONObject.NULL)
                    put("notes", m.notes ?: JSONObject.NULL)
                    put("created_at", m.createdAt)
                    put("deleted_at", m.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.PARENT_CHILD -> parentChildDao.getByIdIncludingDeleted(entityId)?.let { link ->
                JSONObject().apply {
                    put("id", link.id)
                    put("parent_id", link.parentId)
                    put("child_id", link.childId)
                    put("relationship_type", link.relationshipType.name)
                    put("marriage_id", link.marriageId ?: JSONObject.NULL)
                    put("notes", link.notes ?: JSONObject.NULL)
                    put("created_at", link.createdAt)
                    put("deleted_at", link.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.EDUCATION -> educationDao.getByIdIncludingDeleted(entityId)?.let { edu ->
                JSONObject().apply {
                    put("id", edu.id)
                    put("person_id", edu.personId)
                    put("qualification", edu.qualification)
                    put("specialization", edu.specialization ?: JSONObject.NULL)
                    put("institution", edu.institution ?: JSONObject.NULL)
                    put("graduation_year", edu.graduationYear ?: JSONObject.NULL)
                    put("notes", edu.notes ?: JSONObject.NULL)
                    put("created_at", edu.createdAt)
                    put("deleted_at", edu.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.PHOTO -> photoDao.getByIdIncludingDeleted(entityId)?.let { photo ->
                val tags = photoDao.getTaggedPersons(photo.id).map { it.personId }
                JSONObject().apply {
                    put("id", photo.id)
                    put("person_id", photo.personId ?: JSONObject.NULL)
                    put("remote_storage_key", photo.remoteStorageKey ?: JSONObject.NULL)
                    put("sha256", photo.sha256 ?: JSONObject.NULL)
                    put("description", photo.description ?: JSONObject.NULL)
                    put("photo_date", photo.photoDate ?: JSONObject.NULL)
                    put("photo_date_precision", photo.photoDatePrecision.name)
                    put("media_type", photo.mediaType.name)
                    put("tagged_person_ids", JSONArray(tags))
                    put("created_at", photo.createdAt)
                    put("deleted_at", photo.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.EVENT -> eventDao.getByIdIncludingDeleted(entityId)?.let { ev ->
                JSONObject().apply {
                    put("id", ev.id)
                    put("person_id", ev.personId ?: JSONObject.NULL)
                    put("event_type", ev.eventType.name)
                    put("event_date", ev.eventDate ?: JSONObject.NULL)
                    put("event_date_precision", ev.eventDatePrecision.name)
                    put("event_place", ev.eventPlace ?: JSONObject.NULL)
                    put("description", ev.description ?: JSONObject.NULL)
                    put("created_at", ev.createdAt)
                    put("deleted_at", ev.deletedAt ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.FAMILY_MEMBER -> familyMemberDao.getById(entityId)?.let { fm ->
                JSONObject().apply {
                    put("member_id", fm.id)
                    put("linked_person_id", fm.linkedPersonId ?: JSONObject.NULL)
                    put("branch_root_person_id", fm.branchRootPersonId ?: JSONObject.NULL)
                }.toString()
            }

            SyncEntityType.PHOTO_TAG -> null
        }
    }

    private suspend fun resolveClientVersion(
        entityType: SyncEntityType,
        entityId: String,
        fallback: Long,
    ): Long = when (entityType) {
        SyncEntityType.FAMILY -> familySpaceDao.getById(entityId)?.version ?: fallback
        SyncEntityType.FAMILY_GROUP -> familyGroupDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.PERSON -> personDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.MARRIAGE -> marriageDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.PARENT_CHILD -> parentChildDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.EDUCATION -> educationDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.PHOTO -> photoDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.EVENT -> eventDao.getByIdIncludingDeleted(entityId)?.version ?: fallback
        SyncEntityType.FAMILY_MEMBER -> familyMemberDao.getById(entityId)?.version ?: fallback
        SyncEntityType.PHOTO_TAG -> fallback
    }

    private suspend fun determineOperation(
        entityType: SyncEntityType,
        entityId: String,
    ): SyncOutboxOperation {
        val isDeleted = when (entityType) {
            SyncEntityType.FAMILY_GROUP -> familyGroupDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            SyncEntityType.PERSON -> personDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            SyncEntityType.MARRIAGE -> marriageDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            SyncEntityType.PARENT_CHILD -> parentChildDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            SyncEntityType.EDUCATION -> educationDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            SyncEntityType.PHOTO -> photoDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            SyncEntityType.EVENT -> eventDao.getByIdIncludingDeleted(entityId)?.isDeleted ?: false
            else -> false
        }
        return if (isDeleted) SyncOutboxOperation.SOFT_DELETE else SyncOutboxOperation.UPSERT
    }

    private suspend fun markEntitySynced(
        entityType: SyncEntityType,
        entityId: String,
        version: Long,
        updatedAt: Long,
    ) {
        when (entityType) {
            SyncEntityType.FAMILY -> familySpaceDao.getById(entityId)?.let {
                familySpaceDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.FAMILY_GROUP -> familyGroupDao.getByIdIncludingDeleted(entityId)?.let {
                familyGroupDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.PERSON -> personDao.getByIdIncludingDeleted(entityId)?.let {
                personDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.MARRIAGE -> marriageDao.getByIdIncludingDeleted(entityId)?.let {
                marriageDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.PARENT_CHILD -> parentChildDao.getByIdIncludingDeleted(entityId)?.let {
                parentChildDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.EDUCATION -> educationDao.getByIdIncludingDeleted(entityId)?.let {
                educationDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.PHOTO -> photoDao.getByIdIncludingDeleted(entityId)?.let {
                photoDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.EVENT -> eventDao.getByIdIncludingDeleted(entityId)?.let {
                eventDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.FAMILY_MEMBER -> familyMemberDao.getById(entityId)?.let {
                familyMemberDao.upsert(it.copy(version = version, syncState = SyncState.SYNCED, updatedAt = updatedAt))
            }
            SyncEntityType.PHOTO_TAG -> Unit
        }
    }

    private suspend fun markEntityConflicted(entityType: SyncEntityType, entityId: String) {
        when (entityType) {
            SyncEntityType.FAMILY -> familySpaceDao.getById(entityId)?.let {
                familySpaceDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.FAMILY_GROUP -> familyGroupDao.getByIdIncludingDeleted(entityId)?.let {
                familyGroupDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.PERSON -> personDao.getByIdIncludingDeleted(entityId)?.let {
                personDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.MARRIAGE -> marriageDao.getByIdIncludingDeleted(entityId)?.let {
                marriageDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.PARENT_CHILD -> parentChildDao.getByIdIncludingDeleted(entityId)?.let {
                parentChildDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.EDUCATION -> educationDao.getByIdIncludingDeleted(entityId)?.let {
                educationDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.PHOTO -> photoDao.getByIdIncludingDeleted(entityId)?.let {
                photoDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.EVENT -> eventDao.getByIdIncludingDeleted(entityId)?.let {
                eventDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.FAMILY_MEMBER -> familyMemberDao.getById(entityId)?.let {
                familyMemberDao.upsert(it.copy(syncState = SyncState.CONFLICT))
            }
            SyncEntityType.PHOTO_TAG -> Unit
        }
    }

    private fun pushOrder(type: SyncEntityType): Int = when (type) {
        SyncEntityType.FAMILY_GROUP -> 1
        SyncEntityType.PERSON -> 2
        SyncEntityType.MARRIAGE -> 3
        SyncEntityType.PARENT_CHILD -> 4
        SyncEntityType.EDUCATION -> 5
        SyncEntityType.PHOTO -> 6
        SyncEntityType.PHOTO_TAG -> 7
        SyncEntityType.EVENT -> 8
        SyncEntityType.FAMILY -> 9
        SyncEntityType.FAMILY_MEMBER -> 10
    }

    private fun parseFamilySpace(obj: JSONObject): FamilySpaceEntity {
        val now = System.currentTimeMillis()
        return FamilySpaceEntity(
            id = obj.getString("id"),
            name = obj.optString("name", "العائلة"),
            motto = obj.optString("motto", "نوثّق الأصول، ونحفظ امتداد الأجيال"),
            ownerUserId = obj.optNullableString("owner_user_id"),
            rootPersonId = obj.optNullableString("root_person_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            syncState = SyncState.SYNCED,
            createdAt = obj.optLong("created_at", now),
            updatedAt = obj.optLong("updated_at", now),
        )
    }

    private fun parseFamilyGroup(familyId: String, obj: JSONObject): FamilyGroupEntity {
        val now = System.currentTimeMillis()
        return FamilyGroupEntity(
            id = obj.getString("id"),
            groupName = obj.optString("group_name", "فرع عائلي"),
            description = obj.optNullableString("description"),
            rootPersonId = obj.optNullableString("root_person_id"),
            createdAt = obj.optLong("created_at", now),
            familyId = familyId,
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            updatedAt = obj.optLong("updated_at", now),
            syncState = SyncState.SYNCED,
        )
    }

    private fun parsePerson(familyId: String, obj: JSONObject, existingImagePath: String?): PersonEntity {
        val now = System.currentTimeMillis()
        val fullName = obj.optString("full_name", "فرد")
        return PersonEntity(
            id = obj.getString("id"),
            fullName = fullName,
            firstName = obj.optString("first_name", fullName.substringBefore(" ")),
            middleName = obj.optNullableString("middle_name"),
            familyName = obj.optNullableString("family_name"),
            gender = runCatching { Gender.valueOf(obj.optString("gender", Gender.MALE.name)) }.getOrDefault(Gender.MALE),
            birthDate = obj.optNullableString("birth_date"),
            birthDatePrecision = runCatching {
                DatePrecision.valueOf(obj.optString("birth_date_precision", DatePrecision.UNKNOWN.name))
            }.getOrDefault(DatePrecision.UNKNOWN),
            birthPlace = obj.optNullableString("birth_place"),
            deathDate = obj.optNullableString("death_date"),
            deathDatePrecision = runCatching {
                DatePrecision.valueOf(obj.optString("death_date_precision", DatePrecision.UNKNOWN.name))
            }.getOrDefault(DatePrecision.UNKNOWN),
            deathPlace = obj.optNullableString("death_place"),
            biography = obj.optNullableString("biography"),
            occupation = obj.optNullableString("occupation"),
            currentMaritalStatus = runCatching {
                MaritalStatus.valueOf(obj.optString("current_marital_status", MaritalStatus.SINGLE.name))
            }.getOrDefault(MaritalStatus.SINGLE),
            profileImagePath = existingImagePath,
            familyGroupId = obj.optNullableString("family_group_id"),
            notes = obj.optNullableString("notes"),
            createdAt = obj.optLong("created_at", now),
            updatedAt = obj.optLong("updated_at", now),
            familyId = familyId,
            remoteAvatarKey = obj.optNullableString("remote_avatar_key"),
            avatarSha256 = obj.optNullableString("avatar_sha256"),
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            syncState = SyncState.SYNCED,
        )
    }

    private fun parseMarriage(familyId: String, obj: JSONObject): MarriageEntity {
        val now = System.currentTimeMillis()
        return MarriageEntity(
            id = obj.getString("id"),
            partnerOneId = obj.getString("partner_one_id"),
            partnerTwoId = obj.getString("partner_two_id"),
            marriageDate = obj.optNullableString("marriage_date"),
            marriageDatePrecision = runCatching {
                DatePrecision.valueOf(obj.optString("marriage_date_precision", DatePrecision.UNKNOWN.name))
            }.getOrDefault(DatePrecision.UNKNOWN),
            marriagePlace = obj.optNullableString("marriage_place"),
            marriageStatus = runCatching {
                MarriageRecordStatus.valueOf(obj.optString("marriage_status", MarriageRecordStatus.ACTIVE.name))
            }.getOrDefault(MarriageRecordStatus.ACTIVE),
            endDate = obj.optNullableString("end_date"),
            endReason = obj.optNullableString("end_reason"),
            notes = obj.optNullableString("notes"),
            createdAt = obj.optLong("created_at", now),
            updatedAt = obj.optLong("updated_at", now),
            familyId = familyId,
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            syncState = SyncState.SYNCED,
        )
    }

    private fun parseParentChild(familyId: String, obj: JSONObject, validMarriageId: String?): ParentChildEntity {
        val now = System.currentTimeMillis()
        return ParentChildEntity(
            id = obj.getString("id"),
            parentId = obj.getString("parent_id"),
            childId = obj.getString("child_id"),
            relationshipType = runCatching {
                ParentRelationshipType.valueOf(obj.optString("relationship_type", ParentRelationshipType.BIOLOGICAL_FATHER.name))
            }.getOrDefault(ParentRelationshipType.BIOLOGICAL_FATHER),
            marriageId = validMarriageId,
            notes = obj.optNullableString("notes"),
            createdAt = obj.optLong("created_at", now),
            familyId = familyId,
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            updatedAt = obj.optLong("updated_at", now),
            syncState = SyncState.SYNCED,
        )
    }

    private fun parseEducation(familyId: String, obj: JSONObject): EducationEntity {
        val now = System.currentTimeMillis()
        return EducationEntity(
            id = obj.getString("id"),
            personId = obj.getString("person_id"),
            qualification = obj.optString("qualification", "مؤهل"),
            specialization = obj.optNullableString("specialization"),
            institution = obj.optNullableString("institution"),
            graduationYear = if (!obj.has("graduation_year") || obj.isNull("graduation_year")) null else obj.optInt("graduation_year"),
            notes = obj.optNullableString("notes"),
            familyId = familyId,
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            createdAt = obj.optLong("created_at", now),
            updatedAt = obj.optLong("updated_at", now),
            syncState = SyncState.SYNCED,
        )
    }

    private fun parsePhoto(familyId: String, obj: JSONObject, validPersonId: String?, existingPath: String): PhotoEntity {
        val now = System.currentTimeMillis()
        return PhotoEntity(
            id = obj.getString("id"),
            personId = validPersonId,
            filePath = existingPath,
            description = obj.optNullableString("description"),
            photoDate = obj.optNullableString("photo_date"),
            photoDatePrecision = runCatching {
                DatePrecision.valueOf(obj.optString("photo_date_precision", DatePrecision.UNKNOWN.name))
            }.getOrDefault(DatePrecision.UNKNOWN),
            mediaType = runCatching {
                MediaType.valueOf(obj.optString("media_type", MediaType.PERSON_PORTRAIT.name))
            }.getOrDefault(MediaType.PERSON_PORTRAIT),
            createdAt = obj.optLong("created_at", now),
            familyId = familyId,
            remoteStorageKey = obj.optNullableString("remote_storage_key"),
            sha256 = obj.optNullableString("sha256"),
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            updatedAt = obj.optLong("updated_at", now),
            syncState = SyncState.SYNCED,
        )
    }

    private fun parseEvent(familyId: String, obj: JSONObject, validPersonId: String?): EventEntity {
        val now = System.currentTimeMillis()
        return EventEntity(
            id = obj.getString("id"),
            personId = validPersonId,
            eventType = runCatching {
                FamilyEventType.valueOf(obj.optString("event_type", FamilyEventType.OTHER.name))
            }.getOrDefault(FamilyEventType.OTHER),
            eventDate = obj.optNullableString("event_date"),
            eventDatePrecision = runCatching {
                DatePrecision.valueOf(obj.optString("event_date_precision", DatePrecision.UNKNOWN.name))
            }.getOrDefault(DatePrecision.UNKNOWN),
            eventPlace = obj.optNullableString("event_place"),
            description = obj.optNullableString("description"),
            familyId = familyId,
            createdByUserId = obj.optNullableString("created_by_user_id"),
            updatedByUserId = obj.optNullableString("updated_by_user_id"),
            version = obj.optLong("version", 1L),
            isDeleted = obj.optBoolean("is_deleted", false),
            deletedAt = obj.optNullableLong("deleted_at"),
            createdAt = obj.optLong("created_at", now),
            updatedAt = obj.optLong("updated_at", now),
            syncState = SyncState.SYNCED,
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)
}
