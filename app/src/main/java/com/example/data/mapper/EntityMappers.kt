package com.example.data.mapper

import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.EducationEntity
import com.example.data.local.entity.EventEntity
import com.example.data.local.entity.FamilyGroupEntity
import com.example.data.local.entity.FamilyInvitationEntity
import com.example.data.local.entity.FamilyMemberEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.MarriageEntity
import com.example.data.local.entity.ParentChildEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.local.entity.PhotoEntity
import com.example.data.local.entity.SyncConflictEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.domain.model.AuditEntry
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyGroup
import com.example.domain.model.FamilyInvitation
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilySpace
import com.example.domain.model.Marriage
import com.example.domain.model.ParentChildLink
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.model.SyncConflict
import com.example.domain.model.SyncMetadata
import com.example.domain.model.SyncOutboxEntry
import com.example.domain.model.UserAccount

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    fullName = fullName,
    firstName = firstName,
    middleName = middleName,
    familyName = familyName,
    gender = gender,
    birthDate = birthDate,
    birthDatePrecision = birthDatePrecision,
    birthPlace = birthPlace,
    deathDate = deathDate,
    deathDatePrecision = deathDatePrecision,
    deathPlace = deathPlace,
    biography = biography,
    occupation = occupation,
    currentMaritalStatus = currentMaritalStatus,
    profileImagePath = profileImagePath,
    familyGroupId = familyGroupId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    familyId = familyId,
    remoteAvatarKey = remoteAvatarKey,
    avatarSha256 = avatarSha256,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    syncState = syncState,
)

fun Person.toEntity(): PersonEntity = PersonEntity(
    id = id,
    fullName = fullName,
    firstName = firstName,
    middleName = middleName,
    familyName = familyName,
    gender = gender,
    birthDate = birthDate,
    birthDatePrecision = birthDatePrecision,
    birthPlace = birthPlace,
    deathDate = deathDate,
    deathDatePrecision = deathDatePrecision,
    deathPlace = deathPlace,
    biography = biography,
    occupation = occupation,
    currentMaritalStatus = currentMaritalStatus,
    profileImagePath = profileImagePath,
    familyGroupId = familyGroupId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    familyId = familyId,
    remoteAvatarKey = remoteAvatarKey,
    avatarSha256 = avatarSha256,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    syncState = syncState,
)

fun MarriageEntity.toDomain(): Marriage = Marriage(
    id = id,
    partnerOneId = partnerOneId,
    partnerTwoId = partnerTwoId,
    marriageDate = marriageDate,
    marriageDatePrecision = marriageDatePrecision,
    marriagePlace = marriagePlace,
    marriageStatus = marriageStatus,
    endDate = endDate,
    endReason = endReason,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    syncState = syncState,
)

fun Marriage.toEntity(): MarriageEntity = MarriageEntity(
    id = id,
    partnerOneId = partnerOneId,
    partnerTwoId = partnerTwoId,
    marriageDate = marriageDate,
    marriageDatePrecision = marriageDatePrecision,
    marriagePlace = marriagePlace,
    marriageStatus = marriageStatus,
    endDate = endDate,
    endReason = endReason,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    syncState = syncState,
)

fun ParentChildEntity.toDomain(): ParentChildLink = ParentChildLink(
    id = id,
    parentId = parentId,
    childId = childId,
    relationshipType = relationshipType,
    marriageId = marriageId,
    notes = notes,
    createdAt = createdAt,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun ParentChildLink.toEntity(): ParentChildEntity = ParentChildEntity(
    id = id,
    parentId = parentId,
    childId = childId,
    relationshipType = relationshipType,
    marriageId = marriageId,
    notes = notes,
    createdAt = createdAt,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun EducationEntity.toDomain(): EducationRecord = EducationRecord(
    id = id,
    personId = personId,
    qualification = qualification,
    specialization = specialization,
    institution = institution,
    graduationYear = graduationYear,
    notes = notes,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun EducationRecord.toEntity(): EducationEntity = EducationEntity(
    id = id,
    personId = personId,
    qualification = qualification,
    specialization = specialization,
    institution = institution,
    graduationYear = graduationYear,
    notes = notes,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun PhotoEntity.toDomain(taggedPersonIds: List<String>): PhotoRecord = PhotoRecord(
    id = id,
    personId = personId,
    taggedPersonIds = taggedPersonIds,
    filePath = filePath,
    description = description,
    photoDate = photoDate,
    photoDatePrecision = photoDatePrecision,
    mediaType = mediaType,
    createdAt = createdAt,
    familyId = familyId,
    remoteStorageKey = remoteStorageKey,
    sha256 = sha256,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun PhotoRecord.toEntity(): PhotoEntity = PhotoEntity(
    id = id,
    personId = personId,
    filePath = filePath,
    description = description,
    photoDate = photoDate,
    photoDatePrecision = photoDatePrecision,
    mediaType = mediaType,
    createdAt = createdAt,
    familyId = familyId,
    remoteStorageKey = remoteStorageKey,
    sha256 = sha256,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun EventEntity.toDomain(): EventRecord = EventRecord(
    id = id,
    personId = personId,
    eventType = eventType,
    eventDate = eventDate,
    eventDatePrecision = eventDatePrecision,
    eventPlace = eventPlace,
    description = description,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun EventRecord.toEntity(): EventEntity = EventEntity(
    id = id,
    personId = personId,
    eventType = eventType,
    eventDate = eventDate,
    eventDatePrecision = eventDatePrecision,
    eventPlace = eventPlace,
    description = description,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun FamilyGroupEntity.toDomain(): FamilyGroup = FamilyGroup(
    id = id,
    groupName = groupName,
    description = description,
    rootPersonId = rootPersonId,
    createdAt = createdAt,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun FamilyGroup.toEntity(): FamilyGroupEntity = FamilyGroupEntity(
    id = id,
    groupName = groupName,
    description = description,
    rootPersonId = rootPersonId,
    createdAt = createdAt,
    familyId = familyId,
    createdByUserId = createdByUserId,
    updatedByUserId = updatedByUserId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun AuditLogEntity.toDomain(): AuditEntry = AuditEntry(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    oldValue = oldValue,
    newValue = newValue,
    operationDate = operationDate,
    familyId = familyId,
    actorUserId = actorUserId,
    actorMemberRole = actorMemberRole,
    syncState = syncState,
)

fun FamilySpaceEntity.toDomain(): FamilySpace = FamilySpace(
    id = id,
    name = name,
    motto = motto,
    ownerUserId = ownerUserId,
    rootPersonId = rootPersonId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    syncState = syncState,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilySpace.toEntity(): FamilySpaceEntity = FamilySpaceEntity(
    id = id,
    name = name,
    motto = motto,
    ownerUserId = ownerUserId,
    rootPersonId = rootPersonId,
    version = version,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    syncState = syncState,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UserAccountEntity.toDomain(): UserAccount = UserAccount(
    id = id,
    email = email,
    displayName = displayName,
    avatarStorageKey = avatarStorageKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UserAccount.toEntity(): UserAccountEntity = UserAccountEntity(
    id = id,
    email = email,
    displayName = displayName,
    avatarStorageKey = avatarStorageKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyMemberEntity.toDomain(): FamilyMember = FamilyMember(
    id = id,
    familyId = familyId,
    userId = userId,
    linkedPersonId = linkedPersonId,
    role = role,
    branchRootPersonId = branchRootPersonId,
    canInvite = canInvite,
    canExport = canExport,
    canManageMedia = canManageMedia,
    status = status,
    invitedByUserId = invitedByUserId,
    joinedAt = joinedAt,
    updatedAt = updatedAt,
    version = version,
    syncState = syncState,
)

fun FamilyMember.toEntity(): FamilyMemberEntity = FamilyMemberEntity(
    id = id,
    familyId = familyId,
    userId = userId,
    linkedPersonId = linkedPersonId,
    role = role,
    branchRootPersonId = branchRootPersonId,
    canInvite = canInvite,
    canExport = canExport,
    canManageMedia = canManageMedia,
    status = status,
    invitedByUserId = invitedByUserId,
    joinedAt = joinedAt,
    updatedAt = updatedAt,
    version = version,
    syncState = syncState,
)

fun FamilyInvitationEntity.toDomain(): FamilyInvitation = FamilyInvitation(
    id = id,
    familyId = familyId,
    targetPersonId = targetPersonId,
    assignedRole = assignedRole,
    branchRootPersonId = branchRootPersonId,
    inviteCodeHash = inviteCodeHash,
    inviteCodeHint = inviteCodeHint,
    maxUses = maxUses,
    usesCount = usesCount,
    expiresAt = expiresAt,
    status = status,
    createdByUserId = createdByUserId,
    acceptedByUserId = acceptedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyInvitation.toEntity(): FamilyInvitationEntity = FamilyInvitationEntity(
    id = id,
    familyId = familyId,
    targetPersonId = targetPersonId,
    assignedRole = assignedRole,
    branchRootPersonId = branchRootPersonId,
    inviteCodeHash = inviteCodeHash,
    inviteCodeHint = inviteCodeHint,
    maxUses = maxUses,
    usesCount = usesCount,
    expiresAt = expiresAt,
    status = status,
    createdByUserId = createdByUserId,
    acceptedByUserId = acceptedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SyncOutboxEntity.toDomain(): SyncOutboxEntry = SyncOutboxEntry(
    id = id,
    familyId = familyId,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    payloadJson = payloadJson,
    baseVersion = baseVersion,
    status = status,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SyncOutboxEntry.toEntity(): SyncOutboxEntity = SyncOutboxEntity(
    id = id,
    familyId = familyId,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    payloadJson = payloadJson,
    baseVersion = baseVersion,
    status = status,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SyncMetadataEntity.toDomain(): SyncMetadata = SyncMetadata(
    familyId = familyId,
    entityType = entityType,
    lastPulledUpdatedAt = lastPulledUpdatedAt,
    lastSyncedAt = lastSyncedAt,
)

fun SyncMetadata.toEntity(): SyncMetadataEntity = SyncMetadataEntity(
    familyId = familyId,
    entityType = entityType,
    lastPulledUpdatedAt = lastPulledUpdatedAt,
    lastSyncedAt = lastSyncedAt,
)

fun SyncConflictEntity.toDomain(): SyncConflict = SyncConflict(
    id = id,
    familyId = familyId,
    entityType = entityType,
    entityId = entityId,
    localVersion = localVersion,
    serverVersion = serverVersion,
    localPayloadJson = localPayloadJson,
    serverPayloadJson = serverPayloadJson,
    conflictReason = conflictReason,
    detectedAt = detectedAt,
    resolved = resolved,
)

fun SyncConflict.toEntity(): SyncConflictEntity = SyncConflictEntity(
    id = id,
    familyId = familyId,
    entityType = entityType,
    entityId = entityId,
    localVersion = localVersion,
    serverVersion = serverVersion,
    localPayloadJson = localPayloadJson,
    serverPayloadJson = serverPayloadJson,
    conflictReason = conflictReason,
    detectedAt = detectedAt,
    resolved = resolved,
)
