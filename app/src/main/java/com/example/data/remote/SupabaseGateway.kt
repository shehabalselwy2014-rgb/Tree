package com.example.data.remote

import com.example.domain.model.FamilyRole
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MembershipStatus

data class RemoteSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

sealed interface RemoteAuthResult {
    data class Authenticated(val session: RemoteSession) : RemoteAuthResult
    data class ConfirmationRequired(val email: String) : RemoteAuthResult
}

data class RemoteFamilyMembership(
    val familyId: String,
    val familyName: String,
    val familyMotto: String,
    val ownerUserId: String,
    val rootPersonId: String?,
    val familyVersion: Long,
    val familyCreatedAt: Long,
    val familyUpdatedAt: Long,
    val memberId: String,
    val linkedPersonId: String?,
    val role: FamilyRole,
    val branchRootPersonId: String?,
    val canInvite: Boolean,
    val canExport: Boolean,
    val canManageMedia: Boolean,
    val status: MembershipStatus,
    val joinedAt: Long,
    val memberUpdatedAt: Long,
    val memberVersion: Long,
)

data class RemoteInvitation(
    val id: String,
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

data class RemoteMemberProfile(
    val memberId: String,
    val familyId: String,
    val userId: String,
    val email: String,
    val displayName: String,
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
    val version: Long,
)

interface SupabaseGateway {
    val isConfigured: Boolean

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
    ): Result<RemoteAuthResult>

    suspend fun signInWithPassword(
        email: String,
        password: String,
    ): Result<RemoteSession>

    suspend fun refreshToken(refreshToken: String): Result<RemoteSession>

    suspend fun signOut(accessToken: String): Result<Unit>

    suspend fun upsertUserProfile(
        accessToken: String,
        userId: String,
        email: String,
        displayName: String,
    ): Result<Unit>

    suspend fun createFamilySpace(
        accessToken: String,
        familyId: String,
        name: String,
        motto: String,
        rootPersonId: String?,
    ): Result<RemoteFamilyMembership>

    suspend fun fetchMyFamilyMemberships(
        accessToken: String,
    ): Result<List<RemoteFamilyMembership>>

    suspend fun createFamilyInvitation(
        accessToken: String,
        familyId: String,
        targetPersonId: String?,
        assignedRole: FamilyRole,
        branchRootPersonId: String?,
        inviteCodeHash: String,
        inviteCodeHint: String,
        maxUses: Int,
        expiresAt: Long,
    ): Result<RemoteInvitation>

    suspend fun revokeFamilyInvitation(
        accessToken: String,
        invitationId: String,
    ): Result<Unit>

    suspend fun fetchFamilyInvitations(
        accessToken: String,
        familyId: String,
    ): Result<List<RemoteInvitation>>

    suspend fun acceptFamilyInvitation(
        accessToken: String,
        inviteCode: String,
    ): Result<RemoteFamilyMembership>

    suspend fun fetchFamilyMembersWithProfiles(
        accessToken: String,
        familyId: String,
    ): Result<List<RemoteMemberProfile>>

    suspend fun updateFamilyMemberLinkAndRole(
        accessToken: String,
        familyId: String,
        memberId: String,
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ): Result<RemoteMemberProfile>

    suspend fun pushGenealogyMutation(
        accessToken: String,
        familyId: String,
        entityType: String,
        entityId: String,
        operation: String,
        clientVersion: Long,
        forceOverwrite: Boolean,
        payloadJson: String,
    ): Result<RemotePushOutcome>

    suspend fun pullFamilyDelta(
        accessToken: String,
        familyId: String,
        sinceUpdatedAt: Long,
    ): Result<RemoteSyncDelta>

    suspend fun uploadFamilyMedia(
        accessToken: String,
        objectKey: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String>

    suspend fun downloadFamilyMedia(
        accessToken: String,
        objectKey: String,
    ): Result<ByteArray>
}

sealed interface RemotePushOutcome {
    data class Applied(
        val version: Long,
        val updatedAtMillis: Long,
    ) : RemotePushOutcome

    data class Conflict(
        val serverVersion: Long,
        val serverRecordJson: String,
    ) : RemotePushOutcome
}

data class RemoteSyncDelta(
    val serverTimeMillis: Long,
    val rawJson: String,
)

