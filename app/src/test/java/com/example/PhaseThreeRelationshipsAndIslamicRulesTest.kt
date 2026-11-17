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
import com.example.domain.util.PatronymicNameHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseThreeRelationshipsAndIslamicRulesTest {

    private lateinit var database: JuthoorDatabase
    private lateinit var repository: RoomGenealogyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomGenealogyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun person(id: String, first: String, full: String, gender: Gender) = Person(
        id = id,
        fullName = full,
        firstName = first,
        gender = gender,
    )

    @Test
    fun `patronymic helper auto completes full name from father across generations`() {
        val gen1Father = Person(
            id = "f1",
            fullName = "عبدالرحيم عثمان سيف السامعي",
            firstName = "عبدالرحيم",
            middleName = "عثمان سيف",
            familyName = "السامعي",
            gender = Gender.MALE,
        )
        val gen2Auto = PatronymicNameHelper.buildFromFather("شهاب", gen1Father)
        assertEquals("شهاب عبدالرحيم عثمان سيف السامعي", gen2Auto.fullName)
        assertEquals("السامعي", gen2Auto.familyName)

        val gen2Father = Person(
            id = "f2",
            fullName = gen2Auto.fullName,
            firstName = gen2Auto.firstName,
            middleName = gen2Auto.middleName,
            familyName = gen2Auto.familyName,
            gender = Gender.MALE,
        )
        val gen3Auto = PatronymicNameHelper.buildFromFather("عمر", gen2Father)
        assertEquals("عمر شهاب عبدالرحيم عثمان سيف السامعي", gen3Auto.fullName)
    }

    @Test
    fun `islamic marriage rules allow max 4 active wives allow remarriage after divorce and block same gender`() = runTest {
        val husband = repository.savePerson(person("h1", "أحمد", "أحمد سالم", Gender.MALE)).getOrThrow()
        val otherMan = repository.savePerson(person("m_other", "خالد", "خالد سالم", Gender.MALE)).getOrThrow()
        val w1 = repository.savePerson(person("w1", "فاطمة", "فاطمة علي", Gender.FEMALE)).getOrThrow()
        val w2 = repository.savePerson(person("w2", "عائشة", "عائشة حسن", Gender.FEMALE)).getOrThrow()
        val w3 = repository.savePerson(person("w3", "مريم", "مريم سعيد", Gender.FEMALE)).getOrThrow()
        val w4 = repository.savePerson(person("w4", "زينب", "زينب صالح", Gender.FEMALE)).getOrThrow()
        val w5 = repository.savePerson(person("w5", "سمية", "سمية ناصر", Gender.FEMALE)).getOrThrow()

        // Same gender rejected
        assertTrue(repository.saveMarriage(Marriage("bad_gender", husband.id, otherMan.id)).isFailure)

        // 4 concurrent active wives allowed
        val m1 = repository.saveMarriage(Marriage("m1", husband.id, w1.id, "1990", DatePrecision.YEAR_ONLY)).getOrThrow()
        assertTrue(repository.saveMarriage(Marriage("m2", husband.id, w2.id, "1992", DatePrecision.YEAR_ONLY)).isSuccess)
        assertTrue(repository.saveMarriage(Marriage("m3", husband.id, w3.id, "1994", DatePrecision.YEAR_ONLY)).isSuccess)
        assertTrue(repository.saveMarriage(Marriage("m4", husband.id, w4.id, "1996", DatePrecision.YEAR_ONLY)).isSuccess)

        // 5th active wife blocked while 4 are active
        assertTrue(repository.saveMarriage(Marriage("m5_blocked", husband.id, w5.id)).isFailure)

        // Wife w1 cannot simultaneously marry another active husband
        assertTrue(repository.saveMarriage(Marriage("w1_poly_blocked", otherMan.id, w1.id)).isFailure)

        // After ending m1 by divorce, husband can marry w5 AND w1 can remarry otherMan; all 6 records retained
        assertTrue(repository.endMarriage(m1.id, MarriageRecordStatus.DIVORCED, "1997", null).isSuccess)
        assertTrue(repository.saveMarriage(Marriage("m5_ok", husband.id, w5.id)).isSuccess)
        assertTrue(repository.saveMarriage(Marriage("w1_remarry", otherMan.id, w1.id)).isSuccess)
        assertEquals(6, repository.getAllMarriagesOnce().size)
    }

    @Test
    fun `widower and widow status plus marriage date edits preserve children and kafala distinction`() = runTest {
        val husband = repository.savePerson(person("h", "طارق", "طارق منصور", Gender.MALE)).getOrThrow()
        val wife = repository.savePerson(person("w", "سلمى", "سلمى راشد", Gender.FEMALE)).getOrThrow()
        val child = repository.savePerson(person("c", "زيد", "زيد طارق منصور", Gender.MALE)).getOrThrow()
        val orphan = repository.savePerson(person("o", "بلال", "بلال كريم", Gender.MALE)).getOrThrow()

        val marriage = repository.saveMarriage(Marriage("m", husband.id, wife.id, "2001", DatePrecision.YEAR_ONLY)).getOrThrow()
        repository.addParentChildLink(husband.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriage.id).getOrThrow()
        repository.addParentChildLink(husband.id, orphan.id, ParentRelationshipType.ADOPTIVE_GUARDIAN, null).getOrThrow()

        // Edit marriage date directly
        repository.saveMarriage(marriage.copy(marriageDate = "2002-06-15", marriageDatePrecision = DatePrecision.EXACT)).getOrThrow()
        val links = repository.getAllParentChildLinksOnce()
        assertEquals(marriage.id, links.first { it.childId == child.id }.marriageId)
        assertEquals(ParentRelationshipType.ADOPTIVE_GUARDIAN, links.first { it.childId == orphan.id }.relationshipType)

        // End marriage by death -> both marked widowed if no other active marriage
        repository.endMarriage(marriage.id, MarriageRecordStatus.ENDED_BY_DEATH, "2020", null).getOrThrow()
        assertEquals(MaritalStatus.WIDOWED, repository.getPersonById(husband.id)?.currentMaritalStatus)
        assertEquals(MaritalStatus.WIDOWED, repository.getPersonById(wife.id)?.currentMaritalStatus)
    }
}
