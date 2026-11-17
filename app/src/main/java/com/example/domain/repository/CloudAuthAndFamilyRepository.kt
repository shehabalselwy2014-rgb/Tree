package com.example.domain.repository

import com.example.domain.model.AuthActionResult
import com.example.domain.model.AuthSession
import com.example.domain.model.CloudFamilySummary
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.CreatedInvitationResult
import com.example.domain.model.FamilyInvitation
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemberWithProfile
import com.example.domain.model.FamilyRole
import com.example.domain.model.FamilySpace
import com.example.domain.model.MembershipStatus
import com.example.domain.model.SyncConflict
import com.example.domain.model.SyncCycleReport
import kotlinx.coroutines.flow.Flow

interface CloudAuthAndFamilyRepository {
    val currentSession: Flow<AuthSession?>
    val allFamilySpaces: Flow<List<FamilySpace>>
    val activeFamilySpace: Flow<FamilySpace?>
    val activeMembership: Flow<FamilyMember?>
    val activeFamilyMembers: Flow<List<FamilyMemberWithProfile>>
    val activeFamilyInvitations: Flow<List<FamilyInvitation>>
    val pendingSyncCount: Flow<Int>
    val unresolvedConflicts: Flow<List<SyncConflict>>
    val lastSyncedAtMillis: Flow<Long?>
    val isBackendConfigured: Boolean

    suspend fun signUp(email: String, password: String, displayName: String): Result<AuthActionResult>
    suspend fun signIn(email: String, password: String): Result<AuthSession>
    suspend fun refreshSessionIfNeeded(): Result<AuthSession?>
    suspend fun signOut(): Result<Unit>

    suspend fun createCloudFamilySpace(
        name: String,
        motto: String,
        founderPersonId: String?,
        adoptExistingLocalTree: Boolean,
    ): Result<CloudFamilySummary>

    suspend fun refreshMyFamilies(): Result<List<CloudFamilySummary>>
    suspend fun setActiveFamily(familyId: String): Result<Unit>

    suspend fun createInvitation(
        familyId: String,
        targetPersonId: String?,
        assignedRole: FamilyRole,
        branchRootPersonId: String?,
        maxUses: Int = 1,
        validHours: Int = 72,
    ): Result<CreatedInvitationResult>

    suspend fun revokeInvitation(invitationId: String): Result<Unit>
    suspend fun refreshFamilyInvitations(familyId: String): Result<List<FamilyInvitation>>
    suspend fun acceptInvitation(inviteCode: String): Result<CloudFamilySummary>

    suspend fun refreshFamilyMembers(familyId: String): Result<List<FamilyMemberWithProfile>>
    suspend fun updateMemberLinkAndPermissions(
        familyId: String,
        memberId: String,
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ): Result<FamilyMemberWithProfile>

    suspend fun syncNow(familyId: String? = null): Result<SyncCycleReport>
    suspend fun resolveSyncConflict(conflictId: String, choice: ConflictResolutionChoice): Result<Unit>
}
