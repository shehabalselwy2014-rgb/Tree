package com.example

import com.example.domain.model.DatePrecision
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyGroup
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.stats.StatisticsEngine
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.domain.util.OccasionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class PhaseSixStatisticsAndOccasionsTest {

    @Test
    fun `statistics engine deduplicates persons across multiple marriages and computes exact transparent averages`() {
        val father = Person(
            id = "f",
            fullName = "سالم ناصر",
            firstName = "سالم",
            gender = Gender.MALE,
            birthDate = "1960-03-10",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.MARRIED,
            familyGroupId = "g1",
        )
        val wife1 = Person(
            id = "w1",
            fullName = "حصة صالح",
            firstName = "حصة",
            gender = Gender.FEMALE,
            birthDate = "1966-01-01",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.DIVORCED,
            familyGroupId = "g1",
        )
        val wife2 = Person(
            id = "w2",
            fullName = "منيرة راشد",
            firstName = "منيرة",
            gender = Gender.FEMALE,
            birthDate = null, // Unknown birth date must be excluded from average age denominator
            birthDatePrecision = DatePrecision.UNKNOWN,
            currentMaritalStatus = MaritalStatus.MARRIED,
            familyGroupId = "g2",
        )
        val child = Person(
            id = "c",
            fullName = "عمر سالم ناصر",
            firstName = "عمر",
            gender = Gender.MALE,
            birthDate = "1996-05-01",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.SINGLE,
            familyGroupId = "g1",
        )

        val marriages = listOf(
            Marriage("m1", father.id, wife1.id, marriageStatus = MarriageRecordStatus.DIVORCED),
            Marriage("m2", father.id, wife2.id, marriageStatus = MarriageRecordStatus.ACTIVE),
        )
        val links = listOf(
            ParentChildLink("l1", father.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m1"),
        )
        val groups = listOf(
            FamilyGroup("g1", "فرع الشمال"),
            FamilyGroup("g2", "فرع الجنوب"),
        )

        val report = StatisticsEngine.compute(
            persons = listOf(father, wife1, wife2, child),
            marriages = marriages,
            links = links,
            familyGroups = groups,
            referenceYear = 2026,
        )

        // Total persons = 4 (father NOT double-counted across 2 marriages; marriages counted separately as 2)
        assertEquals(4, report.totalPersons)
        assertEquals(2, report.totalMarriagesCount)
        assertEquals(2, report.malesCount)
        assertEquals(2, report.femalesCount)
        assertEquals(4, report.livingCount)
        assertEquals(0, report.deceasedCount)
        assertEquals(2, report.generationsCount)

        // Known living birth years: 1960 -> 66, 1966 -> 60, 1996 -> 30 => avg = (66+60+30)/3 = 52.0
        assertEquals(3, report.averageAgeSampleCount)
        assertEquals(52.0, report.averageAgeYears!!, 0.01)
        assertEquals(father.id, report.oldestLivingPerson?.person?.id)
        assertEquals(66, report.oldestLivingPerson?.estimatedAgeYears)
        assertEquals(2, report.branchDistribution.size)
    }

    @Test
    fun `occasions calculator includes birthdays death memorials anniversaries and custom events`() {
        val today = GregorianCalendar(2026, Calendar.APRIL, 1)
        val living = Person(
            id = "p1",
            fullName = "خالد أحمد",
            firstName = "خالد",
            gender = Gender.MALE,
            birthDate = "1990-04-15",
            birthDatePrecision = DatePrecision.EXACT,
        )
        val deceased = Person(
            id = "p2",
            fullName = "أحمد علي",
            firstName = "أحمد",
            gender = Gender.MALE,
            birthDate = "1940-01-01",
            birthDatePrecision = DatePrecision.EXACT,
            deathDate = "2020-05-01",
            deathDatePrecision = DatePrecision.EXACT,
        )
        val wife = Person(
            id = "p3",
            fullName = "ليلى سعيد",
            firstName = "ليلى",
            gender = Gender.FEMALE,
        )
        val marriage = Marriage(
            id = "m1",
            partnerOneId = living.id,
            partnerTwoId = wife.id,
            marriageDate = "2018-04-20",
            marriageDatePrecision = DatePrecision.EXACT,
            marriageStatus = MarriageRecordStatus.ACTIVE,
        )
        val event = EventRecord(
            id = "ev1",
            personId = null,
            eventType = FamilyEventType.FAMILY_GATHERING,
            eventDate = "2026-06-10",
            eventDatePrecision = DatePrecision.EXACT,
            description = "ملتقى العائلة السنوي",
        )

        val occasions = GenealogyMetricsCalculator.computeUpcomingOccasions(
            persons = listOf(living, deceased, wife),
            marriages = listOf(marriage),
            events = listOf(event),
            today = today,
            horizonDays = 365,
            limit = 50,
        )

        assertNotNull(occasions.firstOrNull { it.category == OccasionCategory.BIRTHDAY && it.daysRemaining == 14 })
        assertNotNull(occasions.firstOrNull { it.category == OccasionCategory.MARRIAGE_ANNIVERSARY && it.daysRemaining == 19 })
        assertNotNull(occasions.firstOrNull { it.category == OccasionCategory.DEATH_MEMORIAL && it.daysRemaining == 30 })
        assertTrue(occasions.any { it.category == OccasionCategory.RECORDED_EVENT && it.title == "ملتقى العائلة السنوي" })
    }
}
