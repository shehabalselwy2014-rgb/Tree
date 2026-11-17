package com.example.domain.model

object MultiUserDefaults {
    const val DEFAULT_LOCAL_FAMILY_ID = "local_default_family"
}

data class FamilySpace(
    val id: String,
    val name: String,
    val motto: String = "نوثّق الأصول، ونحفظ امتداد الأجيال",
    val ownerUserId: String? = null,
    val rootPersonId: String? = null,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class UserAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarStorageKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class FamilyMember(
    val id: String,
    val familyId: String,
    val userId: String,
    val linkedPersonId: String? = null,
    val role: FamilyRole = FamilyRole.MEMBER,
    val branchRootPersonId: String? = null,
    val canInvite: Boolean = false,
    val canExport: Boolean = true,
    val canManageMedia: Boolean = false,
    val status: MembershipStatus = MembershipStatus.ACTIVE,
    val invitedByUserId: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class FamilyInvitation(
    val id: String,
    val familyId: String,
    val targetPersonId: String? = null,
    val assignedRole: FamilyRole = FamilyRole.BRANCH_MANAGER,
    val branchRootPersonId: String? = null,
    val inviteCodeHash: String,
    val inviteCodeHint: String,
    val maxUses: Int = 1,
    val usesCount: Int = 0,
    val expiresAt: Long,
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdByUserId: String,
    val acceptedByUserId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SyncOutboxEntry(
    val id: String,
    val familyId: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOutboxOperation,
    val payloadJson: String,
    val baseVersion: Long,
    val status: SyncOutboxStatus = SyncOutboxStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SyncMetadata(
    val familyId: String,
    val entityType: SyncEntityType,
    val lastPulledUpdatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
)

data class SyncConflict(
    val id: String,
    val familyId: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val localVersion: Long,
    val serverVersion: Long,
    val localPayloadJson: String,
    val serverPayloadJson: String,
    val conflictReason: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
)
