package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.EducationEntity
import com.example.data.local.entity.EventEntity
import com.example.data.local.entity.FamilyGroupEntity
import com.example.data.local.entity.MarriageEntity
import com.example.data.local.entity.ParentChildEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.local.entity.PhotoEntity
import com.example.data.local.entity.PhotoPersonCrossRef
import com.example.domain.model.MaritalStatus
import com.example.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE familyId = :familyId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAllForFamily(familyId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id AND isDeleted = 0")
    fun observeById(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE isDeleted = 0 ORDER BY fullName ASC")
    suspend fun getAllOnce(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE familyId = :familyId AND isDeleted = 0 ORDER BY fullName ASC")
    suspend fun getAllForFamilyOnce(familyId: String): List<PersonEntity>

    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(persons: List<PersonEntity>)

    @Query("UPDATE persons SET currentMaritalStatus = :status, updatedAt = :updatedAt, version = version + 1 WHERE id = :personId AND isDeleted = 0")
    suspend fun updateMaritalStatus(personId: String, status: MaritalStatus, updatedAt: Long)

    @Query("UPDATE persons SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()

    @Query("UPDATE persons SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("SELECT COUNT(*) FROM persons WHERE isDeleted = 0")
    fun observeCount(): Flow<Int>
}

@Dao
interface MarriageDao {
    @Query("SELECT * FROM marriages WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MarriageEntity>>

    @Query("SELECT * FROM marriages WHERE familyId = :familyId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAllForFamily(familyId: String): Flow<List<MarriageEntity>>

    @Query("SELECT * FROM marriages WHERE isDeleted = 0 AND (partnerOneId = :personId OR partnerTwoId = :personId) ORDER BY createdAt DESC")
    fun observeForPerson(personId: String): Flow<List<MarriageEntity>>

    @Query("SELECT * FROM marriages WHERE isDeleted = 0 AND (partnerOneId = :personId OR partnerTwoId = :personId)")
    suspend fun getForPersonOnce(personId: String): List<MarriageEntity>

    @Query("SELECT * FROM marriages WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): MarriageEntity?

    @Query("SELECT * FROM marriages WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): MarriageEntity?

    @Query("SELECT * FROM marriages WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<MarriageEntity>

    @Upsert
    suspend fun upsert(marriage: MarriageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(marriages: List<MarriageEntity>)

    @Query("UPDATE marriages SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM marriages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM marriages WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllForFamilyOnce(familyId: String): List<MarriageEntity>

    @Query("UPDATE marriages SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("DELETE FROM marriages")
    suspend fun deleteAll()
}

@Dao
interface ParentChildDao {
    @Query("SELECT * FROM parent_child WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ParentChildEntity>>

    @Query("SELECT * FROM parent_child WHERE familyId = :familyId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeAllForFamily(familyId: String): Flow<List<ParentChildEntity>>

    @Query("SELECT * FROM parent_child WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<ParentChildEntity>

    @Query("SELECT * FROM parent_child WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): ParentChildEntity?

    @Query("SELECT * FROM parent_child WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): ParentChildEntity?

    @Query("SELECT * FROM parent_child WHERE parentId = :parentId AND childId = :childId AND relationshipType = :relationshipType LIMIT 1")
    suspend fun findExactIncludingDeleted(parentId: String, childId: String, relationshipType: com.example.domain.model.ParentRelationshipType): ParentChildEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(link: ParentChildEntity)

    @Upsert
    suspend fun upsert(link: ParentChildEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<ParentChildEntity>)

    @Query("UPDATE parent_child SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM parent_child WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM parent_child WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllForFamilyOnce(familyId: String): List<ParentChildEntity>

    @Query("UPDATE parent_child SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("DELETE FROM parent_child")
    suspend fun deleteAll()
}

@Dao
interface EducationDao {
    @Query("SELECT * FROM education WHERE personId = :personId AND isDeleted = 0 ORDER BY graduationYear DESC")
    fun observeForPerson(personId: String): Flow<List<EducationEntity>>

    @Query("SELECT * FROM education WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<EducationEntity>

    @Query("SELECT * FROM education WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): EducationEntity?

    @Upsert
    suspend fun upsert(record: EducationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<EducationEntity>)

    @Query("UPDATE education SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM education WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM education WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllForFamilyOnce(familyId: String): List<EducationEntity>

    @Query("UPDATE education SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("DELETE FROM education")
    suspend fun deleteAll()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE familyId = :familyId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAllForFamily(familyId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): PhotoEntity?

    @Query("SELECT * FROM photo_person_cross_ref WHERE photoId = :photoId AND isDeleted = 0")
    suspend fun getTaggedPersons(photoId: String): List<PhotoPersonCrossRef>

    @Query("SELECT * FROM photo_person_cross_ref WHERE isDeleted = 0")
    fun observeAllCrossRefs(): Flow<List<PhotoPersonCrossRef>>

    @Query("SELECT * FROM photo_person_cross_ref WHERE isDeleted = 0")
    suspend fun getAllCrossRefsOnce(): List<PhotoPersonCrossRef>

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PhotoPersonCrossRef>)

    @Query("DELETE FROM photo_person_cross_ref WHERE photoId = :photoId")
    suspend fun clearCrossRefsForPhoto(photoId: String)

    @Transaction
    suspend fun upsertPhotoWithTags(photo: PhotoEntity, taggedPersonIds: List<String>) {
        upsert(photo)
        clearCrossRefsForPhoto(photo.id)
        val distinctIds = (taggedPersonIds + listOfNotNull(photo.personId)).distinct()
        if (distinctIds.isNotEmpty()) {
            insertCrossRefs(
                distinctIds.map { pid ->
                    PhotoPersonCrossRef(
                        photoId = photo.id,
                        personId = pid,
                        familyId = photo.familyId,
                        isDeleted = photo.isDeleted,
                        updatedAt = photo.updatedAt,
                        syncState = photo.syncState,
                    )
                }
            )
        }
    }

    @Query("UPDATE photos SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM photo_person_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Query("SELECT * FROM photos WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllForFamilyOnce(familyId: String): List<PhotoEntity>

    @Query("UPDATE photos SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("UPDATE photo_person_cross_ref SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now WHERE familyId = :fromFamilyId")
    suspend fun reassignCrossRefsFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE isDeleted = 0")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<EventEntity>

    @Query("SELECT * FROM events WHERE personId = :personId AND isDeleted = 0")
    fun observeForPerson(personId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): EventEntity?

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("UPDATE events SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM events WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllForFamilyOnce(familyId: String): List<EventEntity>

    @Query("UPDATE events SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("DELETE FROM events")
    suspend fun deleteAll()
}

@Dao
interface FamilyGroupDao {
    @Query("SELECT * FROM family_groups WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FamilyGroupEntity>>

    @Query("SELECT * FROM family_groups WHERE isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<FamilyGroupEntity>

    @Query("SELECT * FROM family_groups WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): FamilyGroupEntity?

    @Upsert
    suspend fun upsert(group: FamilyGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<FamilyGroupEntity>)

    @Query("UPDATE family_groups SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1, syncState = :syncState, updatedByUserId = :actorUserId WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAt: Long, syncState: SyncState, actorUserId: String?)

    @Query("DELETE FROM family_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM family_groups WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllForFamilyOnce(familyId: String): List<FamilyGroupEntity>

    @Query("UPDATE family_groups SET familyId = :toFamilyId, syncState = :syncState, updatedAt = :now, version = version + 1, updatedByUserId = COALESCE(updatedByUserId, :actorUserId), createdByUserId = COALESCE(createdByUserId, :actorUserId) WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String, syncState: SyncState, now: Long, actorUserId: String)

    @Query("DELETE FROM family_groups")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log ORDER BY operationDate DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE familyId = :familyId ORDER BY operationDate DESC LIMIT :limit")
    fun observeRecentForFamily(familyId: String, limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY operationDate DESC")
    suspend fun getAllOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AuditLogEntity>)

    @Query("UPDATE audit_log SET familyId = :toFamilyId WHERE familyId = :fromFamilyId")
    suspend fun reassignFamilyId(fromFamilyId: String, toFamilyId: String)

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()
}
