package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.FamilyRole
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MembershipStatus
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState

@Entity(
    tableName = "families",
    indices = [
        Index("ownerUserId"),
        Index("isDeleted"),
        Index("syncState"),
    ],
)
data class FamilySpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val motto: String,
    val ownerUserId: String?,
    val rootPersonId: String?,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "user_accounts",
    indices = [
        Index(value = ["email"], unique = true),
    ],
)
data class UserAccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val avatarStorageKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "family_members",
    foreignKeys = [
        ForeignKey(
            entity = FamilySpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedPersonId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("userId"),
        Index("linkedPersonId"),
        Index(value = ["familyId", "userId"], unique = true),
        Index(value = ["familyId", "linkedPersonId"], unique = true),
    ],
)
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val userId: String,
    val linkedPersonId: String?,
    val role: FamilyRole,
    val branchRootPersonId: String?,
    val canInvite: Boolean,
    val canExport: Boolean,
    val canManageMedia: Boolean,
    val status: MembershipStatus,
    val invitedByUserId: String?,
    val joinedAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "family_invitations",
    foreignKeys = [
        ForeignKey(
            entity = FamilySpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetPersonId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("targetPersonId"),
        Index("status"),
        Index(value = ["inviteCodeHash"], unique = true),
    ],
)
data class FamilyInvitationEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val targetPersonId: String?,
    val assignedRole: FamilyRole,
    val branchRootPersonId: String?,
    val inviteCodeHash: String,
    val inviteCodeHint: String,
    val maxUses: Int,
    val usesCount: Int,
    val expiresAt: Long,
    val status: InvitationStatus,
    val createdByUserId: String,
    val acceptedByUserId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index("familyId"),
        Index("status"),
        Index("createdAt"),
        Index("entityId"),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOutboxOperation,
    val payloadJson: String,
    val baseVersion: Long,
    val status: SyncOutboxStatus,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["familyId", "entityType"],
)
data class SyncMetadataEntity(
    val familyId: String,
    val entityType: SyncEntityType,
    val lastPulledUpdatedAt: Long,
    val lastSyncedAt: Long,
)

@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index("familyId"),
        Index("resolved"),
        Index("entityId"),
    ],
)
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val localVersion: Long,
    val serverVersion: Long,
    val localPayloadJson: String,
    val serverPayloadJson: String,
    val conflictReason: String,
    val detectedAt: Long,
    val resolved: Boolean,
)
