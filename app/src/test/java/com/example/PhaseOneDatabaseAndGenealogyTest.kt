package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JuthoorDatabase
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.DatePrecision
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseOneDatabaseAndGenealogyTest {

    private lateinit var context: Context
    private lateinit var database: JuthoorDatabase
    private lateinit var repository: RoomGenealogyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomGenealogyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun testPerson(id: String, firstName: String, gender: Gender = Gender.MALE, birthDate: String? = null) =
        Person(
            id = id,
            fullName = "$firstName الاختباري",
            firstName = firstName,
            familyName = "الاختباري",
            gender = gender,
            birthDate = birthDate,
            birthDatePrecision = if (birthDate == null) DatePrecision.UNKNOWN else DatePrecision.YEAR_ONLY,
        )

    @Test
    fun `add edit and safely delete person updates audit log`() = runTest {
        val grandfather = testPerson("p1", "سالم", Gender.MALE, "1945")
        val saveResult = repository.savePerson(grandfather)
        assertTrue(saveResult.isSuccess)

        val edited = grandfather.copy(occupation = "تاجر")
        assertTrue(repository.savePerson(edited).isSuccess)

        val loaded = repository.getPersonById("p1")
        assertEquals("تاجر", loaded?.occupation)

        assertTrue(repository.deletePersonSafely("p1").isSuccess)
        assertEquals(0, repository.getAllPersonsOnce().size)

        val logs = repository.observeRecentAuditLogs(10).first()
        assertEquals(3, logs.size)
    }

    @Test
    fun `parent child prevents self link duplicates and cycles`() = runTest {
        val grandfather = repository.savePerson(testPerson("g1", "جد")).getOrThrow()
        val father = repository.savePerson(testPerson("f1", "أب")).getOrThrow()
        val child = repository.savePerson(testPerson("c1", "ابن")).getOrThrow()

        // Valid chain: grandfather -> father -> child
        assertTrue(
            repository.addParentChildLink(grandfather.id, father.id, ParentRelationshipType.BIOLOGICAL_FATHER).isSuccess
        )
        assertTrue(
            repository.addParentChildLink(father.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER).isSuccess
        )

        // Prevent self parenting
        val selfResult = repository.addParentChildLink(child.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER)
        assertTrue(selfResult.isFailure)

        // Prevent duplicate exact link
        val dupResult = repository.addParentChildLink(father.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER)
        assertTrue(dupResult.isFailure)

        // Prevent cycle: child cannot be parent of grandfather
        val cycleResult = repository.addParentChildLink(child.id, grandfather.id, ParentRelationshipType.BIOLOGICAL_FATHER)
        assertTrue(cycleResult.isFailure)
    }

    @Test
    fun `polygamy and ended previous marriage keep children separated and marital status accurate`() = runTest {
        val husband = repository.savePerson(testPerson("h1", "إبراهيم", Gender.MALE)).getOrThrow()
        val wife1 = repository.savePerson(testPerson("w1", "ليلى", Gender.FEMALE)).getOrThrow()
        val wife2 = repository.savePerson(testPerson("w2", "هند", Gender.FEMALE)).getOrThrow()
        val wife3 = repository.savePerson(testPerson("w3", "نورة", Gender.FEMALE)).getOrThrow()
        val wife4 = repository.savePerson(testPerson("w4", "سعاد", Gender.FEMALE)).getOrThrow()

        val m1 = repository.saveMarriage(Marriage("m1", husband.id, wife1.id, "1990", DatePrecision.YEAR_ONLY)).getOrThrow()
        val m2 = repository.saveMarriage(Marriage("m2", husband.id, wife2.id, "1995", DatePrecision.YEAR_ONLY)).getOrThrow()
        val m3 = repository.saveMarriage(Marriage("m3", husband.id, wife3.id, "2000", DatePrecision.YEAR_ONLY)).getOrThrow()
        val m4 = repository.saveMarriage(Marriage("m4", husband.id, wife4.id, "2005", DatePrecision.YEAR_ONLY)).getOrThrow()

        val childM1 = repository.savePerson(testPerson("c_m1", "خالد", Gender.MALE)).getOrThrow()
        val childM2 = repository.savePerson(testPerson("c_m2", "علي", Gender.MALE)).getOrThrow()

        assertTrue(repository.addParentChildLink(husband.id, childM1.id, ParentRelationshipType.BIOLOGICAL_FATHER, m1.id).isSuccess)
        assertTrue(repository.addParentChildLink(wife1.id, childM1.id, ParentRelationshipType.BIOLOGICAL_MOTHER, m1.id).isSuccess)
        assertTrue(repository.addParentChildLink(husband.id, childM2.id, ParentRelationshipType.BIOLOGICAL_FATHER, m2.id).isSuccess)
        assertTrue(repository.addParentChildLink(wife2.id, childM2.id, ParentRelationshipType.BIOLOGICAL_MOTHER, m2.id).isSuccess)

        // End m1 by divorce; husband still has active marriages m2, m3, m4
        assertTrue(repository.endMarriage(m1.id, MarriageRecordStatus.DIVORCED, "1998", "انفصال").isSuccess)

        // Updating husband profile must NOT cascade-delete marriages or children
        val latestHusband = repository.getPersonById(husband.id)!!
        assertTrue(repository.savePerson(latestHusband.copy(occupation = "مهندس")).isSuccess)

        val updatedHusband = repository.getPersonById(husband.id)
        val updatedWife1 = repository.getPersonById(wife1.id)
        assertEquals(MaritalStatus.MARRIED, updatedHusband?.currentMaritalStatus)
        assertEquals("مهندس", updatedHusband?.occupation)
        assertEquals(MaritalStatus.DIVORCED, updatedWife1?.currentMaritalStatus)

        // Previous marriage record must still exist and children stay bound to their exact marriage
        val allMarriages = repository.getAllMarriagesOnce()
        assertEquals(4, allMarriages.size)
        val links = repository.getAllParentChildLinksOnce()
        assertEquals(4, links.size)
        assertEquals(m1.id, links.first { it.childId == childM1.id && it.parentId == husband.id }.marriageId)
        assertEquals(m2.id, links.first { it.childId == childM2.id && it.parentId == husband.id }.marriageId)
    }

    @Test
    fun `records persist across database close and reopen`() = runTest {
        val dbFile = File(context.filesDir, "test_persist_phase1.db")
        if (dbFile.exists()) dbFile.delete()

        val db1 = Room.databaseBuilder(context, JuthoorDatabase::class.java, dbFile.name)
            .allowMainThreadQueries()
            .build()
        val repo1 = RoomGenealogyRepository(db1)
        repo1.savePerson(testPerson("persist_1", "يوسف", Gender.MALE, "1980")).getOrThrow()
        db1.close()

        val db2 = Room.databaseBuilder(context, JuthoorDatabase::class.java, dbFile.name)
            .allowMainThreadQueries()
            .build()
        val repo2 = RoomGenealogyRepository(db2)
        val loaded = repo2.getPersonById("persist_1")
        assertNotNull(loaded)
        assertEquals("يوسف الاختباري", loaded?.fullName)
        db2.close()
        dbFile.delete()
    }
}
