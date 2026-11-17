package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.JuthoorDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.AuditEntry
import com.example.domain.model.AuditOperation
import com.example.domain.model.DatabaseSummaryCounts
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyGroup
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState
import com.example.domain.repository.GenealogyRepository
import com.example.domain.validation.GenealogyValidationException
import com.example.domain.validation.GenealogyValidator
import com.example.domain.validation.ValidationOutcome
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.preferences.AuthSessionStore
import com.example.domain.model.MultiUserDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

class RoomGenealogyRepository(
    private val database: JuthoorDatabase,
    private val authSessionStore: AuthSessionStore? = null,
) : GenealogyRepository {

    private val personDao = database.personDao()
    private val marriageDao = database.marriageDao()
    private val parentChildDao = database.parentChildDao()
    private val educationDao = database.educationDao()
    private val photoDao = database.photoDao()
    private val eventDao = database.eventDao()
    private val familyGroupDao = database.familyGroupDao()
    private val auditLogDao = database.auditLogDao()
    private val syncOutboxDao = database.syncOutboxDao()

    override fun observeAllPersons(): Flow<List<Person>> =
        personDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePersonById(personId: String): Flow<Person?> =
        personDao.observeById(personId).map { it?.toDomain() }

    override fun observeAllMarriages(): Flow<List<Marriage>> =
        marriageDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeMarriagesForPerson(personId: String): Flow<List<Marriage>> =
        marriageDao.observeForPerson(personId).map { list -> list.map { it.toDomain() } }

    override fun observeAllParentChildLinks(): Flow<List<ParentChildLink>> =
        parentChildDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEducationForPerson(personId: String): Flow<List<EducationRecord>> =
        educationDao.observeForPerson(personId).map { list -> list.map { it.toDomain() } }

    override fun observeEventsForPerson(personId: String): Flow<List<EventRecord>> =
        eventDao.observeForPerson(personId).map { list -> list.map { it.toDomain() } }

    override fun observeAllEvents(): Flow<List<EventRecord>> =
        eventDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAllPhotos(): Flow<List<PhotoRecord>> =
        combine(photoDao.observeAll(), photoDao.observeAllCrossRefs()) { photos, crossRefs ->
            val tagsByPhoto = crossRefs.groupBy({ it.photoId }, { it.personId })
            photos.map { it.toDomain(tagsByPhoto[it.id].orEmpty()) }
        }

    override fun observeAllFamilyGroups(): Flow<List<FamilyGroup>> =
        familyGroupDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecentAuditLogs(limit: Int): Flow<List<AuditEntry>> =
        auditLogDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeSummaryCounts(): Flow<DatabaseSummaryCounts> =
        combine(
            personDao.observeAll(),
            marriageDao.observeAll(),
            parentChildDao.observeAll(),
            familyGroupDao.observeAll(),
        ) { persons, marriages, links, groups ->
            DatabaseSummaryCounts(
                personsCount = persons.size,
                malesCount = persons.count { it.gender == Gender.MALE },
                femalesCount = persons.count { it.gender == Gender.FEMALE },
                marriagesCount = marriages.size,
                parentChildLinksCount = links.size,
                familyGroupsCount = groups.size,
            )
        }

    override suspend fun getPersonById(personId: String): Person? =
        personDao.getById(personId)?.toDomain()

    override suspend fun getAllPersonsOnce(): List<Person> =
        personDao.getAllOnce().map { it.toDomain() }

    override suspend fun getAllMarriagesOnce(): List<Marriage> =
        marriageDao.getAllOnce().map { it.toDomain() }

    override suspend fun getAllParentChildLinksOnce(): List<ParentChildLink> =
        parentChildDao.getAllOnce().map { it.toDomain() }

    override suspend fun savePerson(person: Person): Result<Person> = runCatching {
        val validation = GenealogyValidator.validatePersonInput(person.firstName, person.fullName)
        if (validation is ValidationOutcome.Invalid) {
            throw GenealogyValidationException(validation.reasonArabic)
        }
        val now = System.currentTimeMillis()
        val resolvedId = person.id.ifBlank { UUID.randomUUID().toString() }
        val resolvedFullName = person.fullName.ifBlank {
            listOfNotNull(person.firstName.takeIf { it.isNotBlank() }, person.middleName?.takeIf { it.isNotBlank() }, person.familyName?.takeIf { it.isNotBlank() })
                .joinToString(" ")
        }.trim()
        val existing = personDao.getByIdIncludingDeleted(resolvedId)
        val context = resolveMutationContext(existing?.familyId ?: person.familyId)
        val nextSyncState = nextUpsertSyncState(existing?.syncState, person.syncState, context.familyId)
        val normalized = person.copy(
            id = resolvedId,
            fullName = resolvedFullName,
            firstName = person.firstName.ifBlank { resolvedFullName.substringBefore(" ") },
            familyId = context.familyId,
            createdByUserId = existing?.createdByUserId ?: person.createdByUserId ?: context.actorUserId,
            updatedByUserId = context.actorUserId ?: person.updatedByUserId ?: existing?.updatedByUserId,
            createdAt = existing?.createdAt ?: person.createdAt,
            updatedAt = now,
            version = if (existing == null) person.version else existing.version + 1L,
            isDeleted = false,
            deletedAt = null,
            syncState = nextSyncState,
        )
        database.withTransaction {
            personDao.upsert(normalized.toEntity())
            enqueueOutboxIfCloud(
                familyId = normalized.familyId,
                entityType = SyncEntityType.PERSON,
                entityId = normalized.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = normalized.version,
                syncState = nextSyncState,
                payload = JSONObject().put("id", normalized.id).put("fullName", normalized.fullName),
                timestamp = now,
            )
            logAudit(
                entityType = "Persons",
                entityId = normalized.id,
                operation = if (existing == null) AuditOperation.CREATE else AuditOperation.UPDATE,
                oldValue = existing?.fullName,
                newValue = normalized.fullName,
                familyId = normalized.familyId,
                actorUserId = normalized.updatedByUserId ?: normalized.createdByUserId,
            )
        }
        normalized
    }

    override suspend fun deletePersonSafely(personId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = personDao.getById(personId)
                ?: throw GenealogyValidationException("الفرد المطلوب حذفه غير موجود.")
            val affectedMarriages = marriageDao.getForPersonOnce(personId)
            val partnerIds = affectedMarriages
                .mapNotNull { it.toDomain().otherPartnerId(personId) }
                .distinct()

            val now = System.currentTimeMillis()
            if (existing.syncState == SyncState.LOCAL_ONLY) {
                personDao.deleteById(personId)
            } else {
                personDao.softDeleteById(personId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                affectedMarriages.forEach { m ->
                    marriageDao.softDeleteById(m.id, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                    enqueueOutboxIfCloud(
                        familyId = m.familyId,
                        entityType = SyncEntityType.MARRIAGE,
                        entityId = m.id,
                        operation = SyncOutboxOperation.SOFT_DELETE,
                        baseVersion = m.version + 1L,
                        syncState = SyncState.PENDING_DELETE,
                        payload = JSONObject().put("id", m.id).put("deletedAt", now),
                        timestamp = now,
                    )
                }
                parentChildDao.getAllOnce()
                    .filter { it.parentId == personId || it.childId == personId }
                    .forEach { link ->
                        parentChildDao.softDeleteById(link.id, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                        enqueueOutboxIfCloud(
                            familyId = link.familyId,
                            entityType = SyncEntityType.PARENT_CHILD,
                            entityId = link.id,
                            operation = SyncOutboxOperation.SOFT_DELETE,
                            baseVersion = link.version + 1L,
                            syncState = SyncState.PENDING_DELETE,
                            payload = JSONObject().put("id", link.id).put("deletedAt", now),
                            timestamp = now,
                        )
                    }
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.PERSON,
                    entityId = personId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", personId).put("deletedAt", now),
                    timestamp = now,
                )
            }

            for (partnerId in partnerIds) {
                val remainingMarriages = marriageDao.getForPersonOnce(partnerId).map { it.toDomain() }
                val newStatus = GenealogyValidator.recomputeMaritalStatus(partnerId, remainingMarriages)
                personDao.updateMaritalStatus(partnerId, newStatus, now)
            }

            logAudit(
                entityType = "Persons",
                entityId = personId,
                operation = AuditOperation.DELETE,
                oldValue = existing.fullName,
                newValue = null,
                familyId = existing.familyId,
                actorUserId = existing.updatedByUserId,
            )
        }
    }

    override suspend fun saveMarriage(marriage: Marriage): Result<Marriage> = runCatching {
        database.withTransaction {
            val partnerOne = personDao.getById(marriage.partnerOneId)?.toDomain()
                ?: throw GenealogyValidationException("الشريك الأول غير موجود في قاعدة البيانات.")
            val partnerTwo = personDao.getById(marriage.partnerTwoId)?.toDomain()
                ?: throw GenealogyValidationException("الشريك الثاني غير موجود في قاعدة البيانات.")

            val existingId = marriage.id.ifBlank { UUID.randomUUID().toString() }
            val existingRecord = marriageDao.getByIdIncludingDeleted(existingId)
            val allMarriages = marriageDao.getAllOnce().map { it.toDomain() }

            val validation = GenealogyValidator.validateMarriage(
                partnerOne = partnerOne,
                partnerTwo = partnerTwo,
                status = marriage.marriageStatus,
                existingMarriages = allMarriages,
                editingMarriageId = existingRecord?.id,
            )
            if (validation is ValidationOutcome.Invalid) {
                throw GenealogyValidationException(validation.reasonArabic)
            }

            val now = System.currentTimeMillis()
            val resolvedFamilyId = partnerOne.familyId
            val context = resolveMutationContext(resolvedFamilyId)
            val nextSyncState = nextUpsertSyncState(existingRecord?.syncState, marriage.syncState, resolvedFamilyId)
            val normalized = marriage.copy(
                id = existingId,
                familyId = resolvedFamilyId,
                createdByUserId = existingRecord?.createdByUserId ?: marriage.createdByUserId ?: context.actorUserId,
                updatedByUserId = context.actorUserId ?: marriage.updatedByUserId ?: existingRecord?.updatedByUserId,
                createdAt = existingRecord?.createdAt ?: marriage.createdAt,
                updatedAt = now,
                version = if (existingRecord == null) marriage.version else existingRecord.version + 1L,
                isDeleted = false,
                deletedAt = null,
                syncState = nextSyncState,
            )
            marriageDao.upsert(normalized.toEntity())
            enqueueOutboxIfCloud(
                familyId = normalized.familyId,
                entityType = SyncEntityType.MARRIAGE,
                entityId = normalized.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = normalized.version,
                syncState = nextSyncState,
                payload = JSONObject().put("id", normalized.id).put("status", normalized.marriageStatus.name),
                timestamp = now,
            )

            recomputeAndPersistMaritalStatus(partnerOne.id, now)
            recomputeAndPersistMaritalStatus(partnerTwo.id, now)

            logAudit(
                entityType = "Marriages",
                entityId = normalized.id,
                operation = if (existingRecord == null) AuditOperation.CREATE else AuditOperation.UPDATE,
                oldValue = existingRecord?.marriageStatus?.name,
                newValue = "${partnerOne.fullName} & ${partnerTwo.fullName} (${normalized.marriageStatus.name})",
                familyId = normalized.familyId,
                actorUserId = normalized.updatedByUserId ?: normalized.createdByUserId,
            )
            normalized
        }
    }

    override suspend fun endMarriage(
        marriageId: String,
        endStatus: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
    ): Result<Unit> = runCatching {
        if (endStatus == MarriageRecordStatus.ACTIVE) {
            throw GenealogyValidationException("حالة إنهاء الزواج يجب أن تكون طلاقاً أو وفاة.")
        }
        database.withTransaction {
            val existing = marriageDao.getById(marriageId)?.toDomain()
                ?: throw GenealogyValidationException("سجل الزواج غير موجود.")
            val now = System.currentTimeMillis()
            val nextSyncState = nextUpsertSyncState(existing.syncState, existing.syncState, existing.familyId)
            val updated = existing.copy(
                marriageStatus = endStatus,
                endDate = endDate,
                endReason = endReason,
                updatedAt = now,
                version = existing.version + 1L,
                syncState = nextSyncState,
            )
            marriageDao.upsert(updated.toEntity())
            enqueueOutboxIfCloud(
                familyId = updated.familyId,
                entityType = SyncEntityType.MARRIAGE,
                entityId = updated.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = updated.version,
                syncState = nextSyncState,
                payload = JSONObject().put("id", updated.id).put("status", endStatus.name),
                timestamp = now,
            )
            recomputeAndPersistMaritalStatus(existing.partnerOneId, now)
            recomputeAndPersistMaritalStatus(existing.partnerTwoId, now)
            logAudit(
                entityType = "Marriages",
                entityId = marriageId,
                operation = AuditOperation.UPDATE,
                oldValue = existing.marriageStatus.name,
                newValue = endStatus.name,
                familyId = existing.familyId,
                actorUserId = existing.updatedByUserId,
            )
        }
    }

    override suspend fun deleteMarriage(marriageId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = marriageDao.getById(marriageId)?.toDomain()
                ?: throw GenealogyValidationException("سجل الزواج غير موجود.")
            val now = System.currentTimeMillis()
            if (existing.syncState == SyncState.LOCAL_ONLY) {
                marriageDao.deleteById(marriageId)
            } else {
                marriageDao.softDeleteById(marriageId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.MARRIAGE,
                    entityId = marriageId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", marriageId).put("deletedAt", now),
                    timestamp = now,
                )
            }
            recomputeAndPersistMaritalStatus(existing.partnerOneId, now)
            recomputeAndPersistMaritalStatus(existing.partnerTwoId, now)
            logAudit(
                entityType = "Marriages",
                entityId = marriageId,
                operation = AuditOperation.DELETE,
                oldValue = existing.marriageStatus.name,
                newValue = null,
                familyId = existing.familyId,
                actorUserId = existing.updatedByUserId,
            )
        }
    }

    override suspend fun addParentChildLink(
        parentId: String,
        childId: String,
        relationshipType: ParentRelationshipType,
        marriageId: String?,
        notes: String?,
    ): Result<ParentChildLink> = runCatching {
        database.withTransaction {
            val parent = personDao.getById(parentId)?.toDomain()
                ?: throw GenealogyValidationException("بيانات الوالد غير موجودة.")
            val child = personDao.getById(childId)?.toDomain()
                ?: throw GenealogyValidationException("بيانات الابن غير موجودة.")
            val marriage = if (marriageId != null) {
                marriageDao.getById(marriageId)?.toDomain()
                    ?: throw GenealogyValidationException("سجل الزواج المحدد غير موجود.")
            } else {
                null
            }

            val allLinks = parentChildDao.getAllOnce().map { it.toDomain() }
            val validation = GenealogyValidator.validateParentChildLink(
                parent = parent,
                child = child,
                relationshipType = relationshipType,
                existingLinks = allLinks,
                associatedMarriage = marriage,
            )
            if (validation is ValidationOutcome.Invalid) {
                throw GenealogyValidationException(validation.reasonArabic)
            }

            val softDeletedMatch = parentChildDao.findExactIncludingDeleted(parent.id, child.id, relationshipType)
            val now = System.currentTimeMillis()
            val resolvedFamilyId = parent.familyId
            val context = resolveMutationContext(resolvedFamilyId)
            val link = if (softDeletedMatch != null && softDeletedMatch.isDeleted) {
                val revivedSync = nextUpsertSyncState(softDeletedMatch.syncState, parent.syncState, resolvedFamilyId)
                val revived = softDeletedMatch.toDomain().copy(
                    marriageId = marriage?.id,
                    notes = notes,
                    familyId = resolvedFamilyId,
                    updatedByUserId = context.actorUserId ?: softDeletedMatch.updatedByUserId,
                    version = softDeletedMatch.version + 1L,
                    isDeleted = false,
                    deletedAt = null,
                    updatedAt = now,
                    syncState = revivedSync,
                )
                parentChildDao.upsert(revived.toEntity())
                revived
            } else {
                val initialSync = if (resolvedFamilyId == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID && parent.syncState == SyncState.LOCAL_ONLY) {
                    SyncState.LOCAL_ONLY
                } else {
                    SyncState.PENDING_UPSERT
                }
                val created = ParentChildLink(
                    id = UUID.randomUUID().toString(),
                    parentId = parent.id,
                    childId = child.id,
                    relationshipType = relationshipType,
                    marriageId = marriage?.id,
                    notes = notes,
                    createdAt = now,
                    familyId = resolvedFamilyId,
                    createdByUserId = context.actorUserId,
                    updatedByUserId = context.actorUserId,
                    updatedAt = now,
                    syncState = initialSync,
                )
                parentChildDao.insert(created.toEntity())
                created
            }
            enqueueOutboxIfCloud(
                familyId = link.familyId,
                entityType = SyncEntityType.PARENT_CHILD,
                entityId = link.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = link.version,
                syncState = link.syncState,
                payload = JSONObject().put("id", link.id).put("parentId", parent.id).put("childId", child.id),
                timestamp = now,
            )
            logAudit(
                entityType = "ParentChild",
                entityId = link.id,
                operation = AuditOperation.CREATE,
                oldValue = null,
                newValue = "${parent.fullName} -> ${child.fullName} (${relationshipType.name})",
                familyId = link.familyId,
            )
            link
        }
    }

    override suspend fun deleteParentChildLink(linkId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = parentChildDao.getById(linkId)
                ?: throw GenealogyValidationException("علاقة النسب غير موجودة.")
            val now = System.currentTimeMillis()
            if (existing.syncState == SyncState.LOCAL_ONLY) {
                parentChildDao.deleteById(linkId)
            } else {
                parentChildDao.softDeleteById(linkId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.PARENT_CHILD,
                    entityId = linkId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", linkId).put("deletedAt", now),
                    timestamp = now,
                )
            }
            logAudit(
                entityType = "ParentChild",
                entityId = linkId,
                operation = AuditOperation.DELETE,
                oldValue = "${existing.parentId} -> ${existing.childId}",
                newValue = null,
                familyId = existing.familyId,
            )
        }
    }

    override suspend fun saveEducation(record: EducationRecord): Result<EducationRecord> = runCatching {
        val resolvedId = record.id.ifBlank { UUID.randomUUID().toString() }
        val existing = educationDao.getByIdIncludingDeleted(resolvedId)
        val ownerPersonFamilyId = personDao.getByIdIncludingDeleted(record.personId)?.familyId
        val context = resolveMutationContext(existing?.familyId ?: ownerPersonFamilyId ?: record.familyId)
        val now = System.currentTimeMillis()
        val nextSync = nextUpsertSyncState(existing?.syncState, record.syncState, context.familyId)
        val normalized = record.copy(
            id = resolvedId,
            familyId = context.familyId,
            createdByUserId = existing?.createdByUserId ?: record.createdByUserId ?: context.actorUserId,
            updatedByUserId = context.actorUserId ?: record.updatedByUserId ?: existing?.updatedByUserId,
            version = if (existing == null) record.version else existing.version + 1L,
            isDeleted = false,
            deletedAt = null,
            createdAt = existing?.createdAt ?: record.createdAt,
            updatedAt = now,
            syncState = nextSync,
        )
        database.withTransaction {
            educationDao.upsert(normalized.toEntity())
            enqueueOutboxIfCloud(
                familyId = normalized.familyId,
                entityType = SyncEntityType.EDUCATION,
                entityId = normalized.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = normalized.version,
                syncState = nextSync,
                payload = JSONObject().put("id", normalized.id).put("qualification", normalized.qualification),
                timestamp = now,
            )
            logAudit("Education", normalized.id, if (existing == null) AuditOperation.CREATE else AuditOperation.UPDATE, existing?.qualification, normalized.qualification, normalized.familyId)
        }
        normalized
    }

    override suspend fun deleteEducation(educationId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = educationDao.getByIdIncludingDeleted(educationId)
            val now = System.currentTimeMillis()
            if (existing == null || existing.syncState == SyncState.LOCAL_ONLY) {
                educationDao.deleteById(educationId)
            } else {
                educationDao.softDeleteById(educationId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.EDUCATION,
                    entityId = educationId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", educationId).put("deletedAt", now),
                    timestamp = now,
                )
            }
            logAudit("Education", educationId, AuditOperation.DELETE, educationId, null, existing?.familyId ?: MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID)
        }
    }

    override suspend fun saveEvent(event: EventRecord): Result<EventRecord> = runCatching {
        val resolvedId = event.id.ifBlank { UUID.randomUUID().toString() }
        val existing = eventDao.getByIdIncludingDeleted(resolvedId)
        val ownerPersonFamilyId = event.personId?.let { personDao.getByIdIncludingDeleted(it)?.familyId }
        val context = resolveMutationContext(existing?.familyId ?: ownerPersonFamilyId ?: event.familyId)
        val now = System.currentTimeMillis()
        val nextSync = nextUpsertSyncState(existing?.syncState, event.syncState, context.familyId)
        val normalized = event.copy(
            id = resolvedId,
            familyId = context.familyId,
            createdByUserId = existing?.createdByUserId ?: event.createdByUserId ?: context.actorUserId,
            updatedByUserId = context.actorUserId ?: event.updatedByUserId ?: existing?.updatedByUserId,
            version = if (existing == null) event.version else existing.version + 1L,
            isDeleted = false,
            deletedAt = null,
            createdAt = existing?.createdAt ?: event.createdAt,
            updatedAt = now,
            syncState = nextSync,
        )
        database.withTransaction {
            eventDao.upsert(normalized.toEntity())
            enqueueOutboxIfCloud(
                familyId = normalized.familyId,
                entityType = SyncEntityType.EVENT,
                entityId = normalized.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = normalized.version,
                syncState = nextSync,
                payload = JSONObject().put("id", normalized.id).put("eventType", normalized.eventType.name),
                timestamp = now,
            )
            logAudit("Events", normalized.id, if (existing == null) AuditOperation.CREATE else AuditOperation.UPDATE, existing?.eventType?.name, normalized.eventType.name, normalized.familyId)
        }
        normalized
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = eventDao.getByIdIncludingDeleted(eventId)
            val now = System.currentTimeMillis()
            if (existing == null || existing.syncState == SyncState.LOCAL_ONLY) {
                eventDao.deleteById(eventId)
            } else {
                eventDao.softDeleteById(eventId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.EVENT,
                    entityId = eventId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", eventId).put("deletedAt", now),
                    timestamp = now,
                )
            }
            logAudit("Events", eventId, AuditOperation.DELETE, eventId, null, existing?.familyId ?: MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID)
        }
    }

    override suspend fun savePhoto(photo: PhotoRecord): Result<PhotoRecord> = runCatching {
        val resolvedId = photo.id.ifBlank { UUID.randomUUID().toString() }
        val existing = photoDao.getByIdIncludingDeleted(resolvedId)
        val ownerPersonFamilyId = photo.personId?.let { personDao.getByIdIncludingDeleted(it)?.familyId }
        val context = resolveMutationContext(existing?.familyId ?: ownerPersonFamilyId ?: photo.familyId)
        val now = System.currentTimeMillis()
        val nextSync = nextUpsertSyncState(existing?.syncState, photo.syncState, context.familyId)
        val normalized = photo.copy(
            id = resolvedId,
            familyId = context.familyId,
            createdByUserId = existing?.createdByUserId ?: photo.createdByUserId ?: context.actorUserId,
            updatedByUserId = context.actorUserId ?: photo.updatedByUserId ?: existing?.updatedByUserId,
            version = if (existing == null) photo.version else existing.version + 1L,
            isDeleted = false,
            deletedAt = null,
            updatedAt = now,
            syncState = nextSync,
        )
        database.withTransaction {
            photoDao.upsertPhotoWithTags(normalized.toEntity(), normalized.taggedPersonIds)
            enqueueOutboxIfCloud(
                familyId = normalized.familyId,
                entityType = SyncEntityType.PHOTO,
                entityId = normalized.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = normalized.version,
                syncState = nextSync,
                payload = JSONObject().put("id", normalized.id).put("mediaType", normalized.mediaType.name),
                timestamp = now,
            )
            logAudit("Photos", normalized.id, if (existing == null) AuditOperation.CREATE else AuditOperation.UPDATE, existing?.mediaType?.name, normalized.mediaType.name, normalized.familyId)
        }
        normalized
    }

    override suspend fun deletePhoto(photoId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = photoDao.getByIdIncludingDeleted(photoId)
            val now = System.currentTimeMillis()
            if (existing == null || existing.syncState == SyncState.LOCAL_ONLY) {
                photoDao.deleteById(photoId)
            } else {
                photoDao.softDeleteById(photoId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.PHOTO,
                    entityId = photoId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", photoId).put("deletedAt", now),
                    timestamp = now,
                )
            }
            logAudit("Photos", photoId, AuditOperation.DELETE, photoId, null, existing?.familyId ?: MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID)
        }
    }

    override suspend fun saveFamilyGroup(group: FamilyGroup): Result<FamilyGroup> = runCatching {
        if (group.groupName.isBlank()) {
            throw GenealogyValidationException("يجب إدخال اسم الفرع العائلي.")
        }
        val resolvedId = group.id.ifBlank { UUID.randomUUID().toString() }
        val existing = familyGroupDao.getByIdIncludingDeleted(resolvedId)
        val context = resolveMutationContext(existing?.familyId ?: group.familyId)
        val now = System.currentTimeMillis()
        val nextSync = nextUpsertSyncState(existing?.syncState, group.syncState, context.familyId)
        val normalized = group.copy(
            id = resolvedId,
            familyId = context.familyId,
            createdByUserId = existing?.createdByUserId ?: group.createdByUserId ?: context.actorUserId,
            updatedByUserId = context.actorUserId ?: group.updatedByUserId ?: existing?.updatedByUserId,
            version = if (existing == null) group.version else existing.version + 1L,
            isDeleted = false,
            deletedAt = null,
            updatedAt = now,
            syncState = nextSync,
        )
        database.withTransaction {
            familyGroupDao.upsert(normalized.toEntity())
            enqueueOutboxIfCloud(
                familyId = normalized.familyId,
                entityType = SyncEntityType.FAMILY_GROUP,
                entityId = normalized.id,
                operation = SyncOutboxOperation.UPSERT,
                baseVersion = normalized.version,
                syncState = nextSync,
                payload = JSONObject().put("id", normalized.id).put("groupName", normalized.groupName),
                timestamp = now,
            )
            logAudit("FamilyGroups", normalized.id, if (existing == null) AuditOperation.CREATE else AuditOperation.UPDATE, existing?.groupName, normalized.groupName, normalized.familyId)
        }
        normalized
    }

    override suspend fun deleteFamilyGroup(groupId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = familyGroupDao.getByIdIncludingDeleted(groupId)
            val now = System.currentTimeMillis()
            if (existing == null || existing.syncState == SyncState.LOCAL_ONLY) {
                familyGroupDao.deleteById(groupId)
            } else {
                familyGroupDao.softDeleteById(groupId, now, SyncState.PENDING_DELETE, existing.updatedByUserId)
                enqueueOutboxIfCloud(
                    familyId = existing.familyId,
                    entityType = SyncEntityType.FAMILY_GROUP,
                    entityId = groupId,
                    operation = SyncOutboxOperation.SOFT_DELETE,
                    baseVersion = existing.version + 1L,
                    syncState = SyncState.PENDING_DELETE,
                    payload = JSONObject().put("id", groupId).put("deletedAt", now),
                    timestamp = now,
                )
            }
            logAudit("FamilyGroups", groupId, AuditOperation.DELETE, groupId, null, existing?.familyId ?: MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID)
        }
    }

    private data class MutationContext(
        val familyId: String,
        val actorUserId: String?,
    )

    private suspend fun resolveMutationContext(requestedFamilyId: String): MutationContext {
        val session = authSessionStore?.getSessionOnce()
        val activeFamilyId = session?.activeFamilyId
            ?.takeIf { it.isNotBlank() && it != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID }
        val resolvedFamilyId = if (
            requestedFamilyId == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID &&
            activeFamilyId != null
        ) {
            activeFamilyId
        } else {
            requestedFamilyId
        }
        return MutationContext(
            familyId = resolvedFamilyId,
            actorUserId = session?.userId,
        )
    }

    private fun nextUpsertSyncState(
        existingState: SyncState?,
        incomingState: SyncState,
        resolvedFamilyId: String,
    ): SyncState {
        if (resolvedFamilyId != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID && incomingState != SyncState.SYNCED) {
            return SyncState.PENDING_UPSERT
        }
        return when {
            existingState == SyncState.SYNCED || existingState == SyncState.PENDING_UPSERT || existingState == SyncState.CONFLICT ->
                SyncState.PENDING_UPSERT
            incomingState == SyncState.PENDING_UPSERT -> SyncState.PENDING_UPSERT
            incomingState == SyncState.SYNCED -> SyncState.SYNCED
            else -> SyncState.LOCAL_ONLY
        }
    }

    private suspend fun enqueueOutboxIfCloud(
        familyId: String,
        entityType: SyncEntityType,
        entityId: String,
        operation: SyncOutboxOperation,
        baseVersion: Long,
        syncState: SyncState,
        payload: JSONObject,
        timestamp: Long,
    ) {
        if (syncState == SyncState.LOCAL_ONLY || familyId == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID) return
        val existingActive = syncOutboxDao.findActiveForEntity(familyId, entityType, entityId)
        syncOutboxDao.upsert(
            SyncOutboxEntity(
                id = existingActive?.id ?: UUID.randomUUID().toString(),
                familyId = familyId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadJson = payload.toString(),
                baseVersion = baseVersion,
                status = SyncOutboxStatus.PENDING,
                retryCount = existingActive?.retryCount ?: 0,
                lastError = null,
                createdAt = existingActive?.createdAt ?: timestamp,
                updatedAt = timestamp,
            )
        )
    }

    private suspend fun recomputeAndPersistMaritalStatus(personId: String, timestamp: Long) {
        val marriages = marriageDao.getForPersonOnce(personId).map { it.toDomain() }
        val newStatus = GenealogyValidator.recomputeMaritalStatus(personId, marriages)
        personDao.updateMaritalStatus(personId, newStatus, timestamp)
    }

    private suspend fun logAudit(
        entityType: String,
        entityId: String,
        operation: AuditOperation,
        oldValue: String?,
        newValue: String?,
        familyId: String = com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
        actorUserId: String? = null,
    ) {
        auditLogDao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                oldValue = oldValue,
                newValue = newValue,
                operationDate = System.currentTimeMillis(),
                familyId = familyId,
                actorUserId = actorUserId,
            )
        )
    }
}
