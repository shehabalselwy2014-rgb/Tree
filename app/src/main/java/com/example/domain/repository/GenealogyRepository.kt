package com.example.domain.repository

import com.example.domain.model.AuditEntry
import com.example.domain.model.DatabaseSummaryCounts
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyGroup
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import kotlinx.coroutines.flow.Flow

interface GenealogyRepository {
    fun observeAllPersons(): Flow<List<Person>>
    fun observePersonById(personId: String): Flow<Person?>
    fun observeAllMarriages(): Flow<List<Marriage>>
    fun observeMarriagesForPerson(personId: String): Flow<List<Marriage>>
    fun observeAllParentChildLinks(): Flow<List<ParentChildLink>>
    fun observeEducationForPerson(personId: String): Flow<List<EducationRecord>>
    fun observeEventsForPerson(personId: String): Flow<List<EventRecord>>
    fun observeAllEvents(): Flow<List<EventRecord>>
    fun observeAllPhotos(): Flow<List<PhotoRecord>>
    fun observeAllFamilyGroups(): Flow<List<FamilyGroup>>
    fun observeRecentAuditLogs(limit: Int = 50): Flow<List<AuditEntry>>
    fun observeSummaryCounts(): Flow<DatabaseSummaryCounts>

    suspend fun getPersonById(personId: String): Person?
    suspend fun getAllPersonsOnce(): List<Person>
    suspend fun getAllMarriagesOnce(): List<Marriage>
    suspend fun getAllParentChildLinksOnce(): List<ParentChildLink>

    suspend fun savePerson(person: Person): Result<Person>
    suspend fun deletePersonSafely(personId: String): Result<Unit>

    suspend fun saveMarriage(marriage: Marriage): Result<Marriage>
    suspend fun endMarriage(
        marriageId: String,
        endStatus: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
    ): Result<Unit>
    suspend fun deleteMarriage(marriageId: String): Result<Unit>

    suspend fun addParentChildLink(
        parentId: String,
        childId: String,
        relationshipType: ParentRelationshipType,
        marriageId: String? = null,
        notes: String? = null,
    ): Result<ParentChildLink>
    suspend fun deleteParentChildLink(linkId: String): Result<Unit>

    suspend fun saveEducation(record: EducationRecord): Result<EducationRecord>
    suspend fun deleteEducation(educationId: String): Result<Unit>

    suspend fun saveEvent(event: EventRecord): Result<EventRecord>
    suspend fun deleteEvent(eventId: String): Result<Unit>

    suspend fun savePhoto(photo: PhotoRecord): Result<PhotoRecord>
    suspend fun deletePhoto(photoId: String): Result<Unit>

    suspend fun saveFamilyGroup(group: FamilyGroup): Result<FamilyGroup>
    suspend fun deleteFamilyGroup(groupId: String): Result<Unit>
}
