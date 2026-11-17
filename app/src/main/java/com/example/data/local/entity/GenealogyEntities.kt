package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AuditOperation
import com.example.domain.model.DatePrecision
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MultiUserDefaults
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.SyncState

@Entity(
    tableName = "persons",
    foreignKeys = [
        ForeignKey(
            entity = FamilyGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyGroupId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true,
        ),
    ],
    indices = [
        Index("fullName"),
        Index("familyName"),
        Index("gender"),
        Index("currentMaritalStatus"),
        Index("familyGroupId"),
        Index("familyId"),
        Index("isDeleted"),
        Index("syncState"),
    ],
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val firstName: String,
    val middleName: String?,
    val familyName: String?,
    val gender: Gender,
    val birthDate: String?,
    val birthDatePrecision: DatePrecision,
    val birthPlace: String?,
    val deathDate: String?,
    val deathDatePrecision: DatePrecision,
    val deathPlace: String?,
    val biography: String?,
    val occupation: String?,
    val currentMaritalStatus: MaritalStatus,
    val profileImagePath: String?,
    val familyGroupId: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val remoteAvatarKey: String? = null,
    val avatarSha256: String? = null,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "marriages",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["partnerOneId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["partnerTwoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("partnerOneId"),
        Index("partnerTwoId"),
        Index("marriageStatus"),
        Index("familyId"),
        Index("isDeleted"),
        Index("syncState"),
    ],
)
data class MarriageEntity(
    @PrimaryKey val id: String,
    val partnerOneId: String,
    val partnerTwoId: String,
    val marriageDate: String?,
    val marriageDatePrecision: DatePrecision,
    val marriagePlace: String?,
    val marriageStatus: MarriageRecordStatus,
    val endDate: String?,
    val endReason: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "parent_child",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarriageEntity::class,
            parentColumns = ["id"],
            childColumns = ["marriageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("parentId"),
        Index("childId"),
        Index("marriageId"),
        Index(value = ["parentId", "childId", "relationshipType"], unique = true),
        Index("familyId"),
        Index("isDeleted"),
        Index("syncState"),
    ],
)
data class ParentChildEntity(
    @PrimaryKey val id: String,
    val parentId: String,
    val childId: String,
    val relationshipType: ParentRelationshipType,
    val marriageId: String?,
    val notes: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "education",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("personId"),
        Index("familyId"),
        Index("isDeleted"),
    ],
)
data class EducationEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val qualification: String,
    val specialization: String?,
    val institution: String?,
    val graduationYear: Int?,
    val notes: String?,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("personId"),
        Index("mediaType"),
        Index("familyId"),
        Index("isDeleted"),
        Index("syncState"),
    ],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val personId: String?,
    val filePath: String,
    val description: String?,
    val photoDate: String?,
    val photoDatePrecision: DatePrecision,
    val mediaType: MediaType,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val remoteStorageKey: String? = null,
    val sha256: String? = null,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "photo_person_cross_ref",
    primaryKeys = ["photoId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("photoId"),
        Index("personId"),
        Index("familyId"),
    ],
)
data class PhotoPersonCrossRef(
    val photoId: String,
    val personId: String,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("personId"),
        Index("eventType"),
        Index("familyId"),
        Index("isDeleted"),
    ],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val personId: String?,
    val eventType: FamilyEventType,
    val eventDate: String?,
    val eventDatePrecision: DatePrecision,
    val eventPlace: String?,
    val description: String?,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "family_groups",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["rootPersonId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true,
        ),
    ],
    indices = [
        Index("rootPersonId"),
        Index("groupName"),
        Index("familyId"),
        Index("isDeleted"),
    ],
)
data class FamilyGroupEntity(
    @PrimaryKey val id: String,
    val groupName: String,
    val description: String?,
    val rootPersonId: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "audit_log",
    indices = [
        Index("entityType"),
        Index("entityId"),
        Index("operationDate"),
        Index("familyId"),
        Index("actorUserId"),
    ],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: AuditOperation,
    val oldValue: String?,
    val newValue: String?,
    val operationDate: Long,
    @ColumnInfo(defaultValue = "local_default_family")
    val familyId: String = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
    val actorUserId: String? = null,
    val actorMemberRole: FamilyRole? = null,
    @ColumnInfo(defaultValue = "LOCAL_ONLY")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)
