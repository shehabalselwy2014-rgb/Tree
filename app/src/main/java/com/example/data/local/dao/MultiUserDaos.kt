package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.local.entity.FamilyInvitationEntity
import com.example.data.local.entity.FamilyMemberEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.SyncConflictEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilySpaceDao {
    @Query("SELECT * FROM families WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeAllActive(): Flow<List<FamilySpaceEntity>>

    @Query("SELECT * FROM families WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): FamilySpaceEntity?

    @Query("SELECT * FROM families WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<FamilySpaceEntity>

    @Upsert
    suspend fun upsert(family: FamilySpaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(families: List<FamilySpaceEntity>)

    @Query("DELETE FROM families WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts")
    fun observeAll(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE id = :id")
    fun observeById(id: String): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE id = :id")
    suspend fun getById(id: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): UserAccountEntity?

    @Upsert
    suspend fun upsert(user: UserAccountEntity)

    @Query("DELETE FROM user_accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members WHERE familyId = :familyId ORDER BY joinedAt ASC")
    fun observeForFamily(familyId: String): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE familyId = :familyId AND userId = :userId LIMIT 1")
    suspend fun getMembership(familyId: String, userId: String): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE familyId = :familyId AND linkedPersonId = :personId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveByLinkedPerson(familyId: String, personId: String): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE familyId = :familyId")
    suspend fun getAllForFamilyOnce(familyId: String): List<FamilyMemberEntity>

    @Upsert
    suspend fun upsert(member: FamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(member: FamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(members: List<FamilyMemberEntity>)
}

@Dao
interface FamilyInvitationDao {
    @Query("SELECT * FROM family_invitations WHERE familyId = :familyId ORDER BY createdAt DESC")
    fun observeForFamily(familyId: String): Flow<List<FamilyInvitationEntity>>

    @Query("SELECT * FROM family_invitations WHERE inviteCodeHash = :codeHash LIMIT 1")
    suspend fun getByCodeHash(codeHash: String): FamilyInvitationEntity?

    @Query("SELECT * FROM family_invitations WHERE id = :id")
    suspend fun getById(id: String): FamilyInvitationEntity?

    @Upsert
    suspend fun upsert(invitation: FamilyInvitationEntity)
}

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE familyId = :familyId AND status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingForFamily(familyId: String): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE familyId = :familyId AND status IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT')")
    fun observePendingCount(familyId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT')")
    fun observeAllPendingCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entry: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SyncOutboxEntity>)

    @Query("UPDATE sync_outbox SET status = :status, retryCount = :retryCount, lastError = :lastError, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: SyncOutboxStatus, retryCount: Int, lastError: String?, updatedAt: Long)

    @Query("SELECT * FROM sync_outbox WHERE familyId = :familyId AND entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'FAILED', 'CONFLICT') LIMIT 1")
    suspend fun findActiveForEntity(familyId: String, entityType: SyncEntityType, entityId: String): SyncOutboxEntity?

    @Query("DELETE FROM sync_outbox WHERE familyId = :familyId AND entityType = :entityType AND entityId = :entityId")
    suspend fun deleteForEntity(familyId: String, entityType: SyncEntityType, entityId: String)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE familyId = :familyId AND entityType = :entityType")
    suspend fun get(familyId: String, entityType: SyncEntityType): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE familyId = :familyId")
    fun observeForFamily(familyId: String): Flow<List<SyncMetadataEntity>>

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts WHERE familyId = :familyId AND resolved = 0 ORDER BY detectedAt DESC")
    fun observeUnresolved(familyId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE familyId = :familyId AND entityType = :entityType AND entityId = :entityId AND resolved = 0 LIMIT 1")
    suspend fun findUnresolvedForEntity(familyId: String, entityType: SyncEntityType, entityId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE id = :id")
    suspend fun getById(id: String): SyncConflictEntity?

    @Upsert
    suspend fun upsert(conflict: SyncConflictEntity)

    @Query("UPDATE sync_conflicts SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: String)
}
