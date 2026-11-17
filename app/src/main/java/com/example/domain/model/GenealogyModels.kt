package com.example.domain.model

data class Person(
    val id: String,
    val fullName: String,
    val firstName: String,
    val middleName: String? = null,
    val familyName: String? = null,
    val gender: Gender,
    val birthDate: String? = null,
    val birthDatePrecision: DatePrecision = DatePrecision.UNKNOWN,
    val birthPlace: String? = null,
    val deathDate: String? = null,
    val deathDatePrecision: DatePrecision = DatePrecision.UNKNOWN,
    val deathPlace: String? = null,
    val biography: String? = null,
    val occupation: String? = null,
    val currentMaritalStatus: MaritalStatus = MaritalStatus.SINGLE,
    val profileImagePath: String? = null,
    val familyGroupId: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val remoteAvatarKey: String? = null,
    val avatarSha256: String? = null,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
) {
    val isDeceased: Boolean
        get() = !deathDate.isNullOrBlank() || deathDatePrecision != DatePrecision.UNKNOWN
}

data class Marriage(
    val id: String,
    val partnerOneId: String,
    val partnerTwoId: String,
    val marriageDate: String? = null,
    val marriageDatePrecision: DatePrecision = DatePrecision.UNKNOWN,
    val marriagePlace: String? = null,
    val marriageStatus: MarriageRecordStatus = MarriageRecordStatus.ACTIVE,
    val endDate: String? = null,
    val endReason: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
) {
    fun involves(personId: String): Boolean = partnerOneId == personId || partnerTwoId == personId

    fun otherPartnerId(personId: String): String? = when (personId) {
        partnerOneId -> partnerTwoId
        partnerTwoId -> partnerOneId
        else -> null
    }
}

data class ParentChildLink(
    val id: String,
    val parentId: String,
    val childId: String,
    val relationshipType: ParentRelationshipType,
    val marriageId: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long = createdAt,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class EducationRecord(
    val id: String,
    val personId: String,
    val qualification: String,
    val specialization: String? = null,
    val institution: String? = null,
    val graduationYear: Int? = null,
    val notes: String? = null,
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class PhotoRecord(
    val id: String,
    val personId: String? = null,
    val taggedPersonIds: List<String> = emptyList(),
    val filePath: String,
    val description: String? = null,
    val photoDate: String? = null,
    val photoDatePrecision: DatePrecision = DatePrecision.UNKNOWN,
    val mediaType: MediaType = MediaType.PERSON_PORTRAIT,
    val createdAt: Long = System.currentTimeMillis(),
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val remoteStorageKey: String? = null,
    val sha256: String? = null,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long = createdAt,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class EventRecord(
    val id: String,
    val personId: String? = null,
    val eventType: FamilyEventType,
    val eventDate: String? = null,
    val eventDatePrecision: DatePrecision = DatePrecision.UNKNOWN,
    val eventPlace: String? = null,
    val description: String? = null,
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class FamilyGroup(
    val id: String,
    val groupName: String,
    val description: String? = null,
    val rootPersonId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long = createdAt,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class AuditEntry(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: AuditOperation,
    val oldValue: String? = null,
    val newValue: String? = null,
    val operationDate: Long = System.currentTimeMillis(),
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val actorUserId: String? = null,
    val actorMemberRole: FamilyRole? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class DatabaseSummaryCounts(
    val personsCount: Int = 0,
    val malesCount: Int = 0,
    val femalesCount: Int = 0,
    val marriagesCount: Int = 0,
    val parentChildLinksCount: Int = 0,
    val familyGroupsCount: Int = 0,
)
