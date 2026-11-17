package com.example.domain.util

import com.example.domain.model.DatePrecision
import com.example.domain.model.EventRecord
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.Person
import java.util.Calendar
import java.util.GregorianCalendar

data class HomeOverviewMetrics(
    val totalPersons: Int = 0,
    val malesCount: Int = 0,
    val femalesCount: Int = 0,
    val livingCount: Int = 0,
    val deceasedCount: Int = 0,
    val generationsCount: Int = 0,
    val marriagesCount: Int = 0,
)

enum class OccasionCategory(val arabicLabel: String) {
    BIRTHDAY("عيد ميلاد"),
    MARRIAGE_ANNIVERSARY("ذكرى زواج"),
    DEATH_MEMORIAL("ذكرى وفاة"),
    RECORDED_EVENT("مناسبة مسجلة"),
}

data class UpcomingOccasion(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateLabel: String,
    val daysRemaining: Int,
    val category: OccasionCategory = OccasionCategory.RECORDED_EVENT,
    val relatedPersonId: String? = null,
)

object GenealogyMetricsCalculator {

    fun computeHomeOverview(
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
    ): HomeOverviewMetrics {
        if (persons.isEmpty()) return HomeOverviewMetrics()
        val generationMap = computeGenerationByPerson(persons, links)
        val maxGen = generationMap.values.maxOrNull() ?: 1
        return HomeOverviewMetrics(
            totalPersons = persons.size,
            malesCount = persons.count { it.gender == Gender.MALE },
            femalesCount = persons.count { it.gender == Gender.FEMALE },
            livingCount = persons.count { !it.isDeceased },
            deceasedCount = persons.count { it.isDeceased },
            generationsCount = maxGen,
            marriagesCount = marriages.size,
        )
    }

    /**
     * Computes 1-based generation depth for each person in the DAG.
     * Roots (persons with no recorded parents) start at generation 1; children = max(parentGen) + 1.
     */
    fun computeGenerationByPerson(
        persons: List<Person>,
        links: List<ParentChildLink>,
    ): Map<String, Int> {
        if (persons.isEmpty()) return emptyMap()
        val validIds = persons.map { it.id }.toSet()
        val parentsByChild = HashMap<String, MutableList<String>>()
        val childrenByParent = HashMap<String, MutableList<String>>()

        for (link in links) {
            if (link.parentId in validIds && link.childId in validIds && link.parentId != link.childId) {
                parentsByChild.getOrPut(link.childId) { mutableListOf() }.add(link.parentId)
                childrenByParent.getOrPut(link.parentId) { mutableListOf() }.add(link.childId)
            }
        }

        val depth = HashMap<String, Int>()
        val inDegree = HashMap<String, Int>()
        val queue = ArrayDeque<String>()

        for (person in persons) {
            val deg = parentsByChild[person.id]?.distinct()?.size ?: 0
            inDegree[person.id] = deg
            if (deg == 0) {
                depth[person.id] = 1
                queue.add(person.id)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentDepth = depth[current] ?: 1
            for (childId in childrenByParent[current].orEmpty()) {
                val candidate = currentDepth + 1
                if ((depth[childId] ?: 0) < candidate) {
                    depth[childId] = candidate
                }
                val nextDeg = (inDegree[childId] ?: 1) - 1
                inDegree[childId] = nextDeg
                if (nextDeg <= 0) {
                    queue.add(childId)
                }
            }
        }

        // Safety fallback for any disconnected nodes
        for (person in persons) {
            depth.putIfAbsent(person.id, 1)
        }
        return depth
    }

    fun extractBirthYear(birthDate: String?): Int? {
        if (birthDate.isNullOrBlank()) return null
        val match = Regex("(\\d{4})").find(birthDate) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun findSiblings(
        personId: String,
        personsById: Map<String, Person>,
        links: List<ParentChildLink>,
    ): List<Person> {
        val parentIds = links.filter { it.childId == personId }.map { it.parentId }.toSet()
        if (parentIds.isEmpty()) return emptyList()
        val siblingIds = links
            .filter { it.parentId in parentIds && it.childId != personId }
            .map { it.childId }
            .distinct()
        return siblingIds.mapNotNull { personsById[it] }
    }

    fun computeUpcomingOccasions(
        persons: List<Person>,
        marriages: List<Marriage>,
        events: List<EventRecord>,
        today: Calendar = GregorianCalendar(),
        horizonDays: Int = 90,
        limit: Int = 5,
    ): List<UpcomingOccasion> {
        val personsById = persons.associateBy { it.id }
        val results = mutableListOf<UpcomingOccasion>()

        // 1. Birthdays for living persons with exact MM-DD birth dates
        for (person in persons) {
            if (!person.isDeceased && person.birthDatePrecision == DatePrecision.EXACT) {
                val days = daysUntilAnnualMonthDay(person.birthDate, today) ?: continue
                if (days in 0..horizonDays) {
                    results.add(
                        UpcomingOccasion(
                            id = "bday_${person.id}",
                            title = "ميلاد ${person.fullName}",
                            subtitle = if (days == 0) "اليوم" else "بعد $days يوم",
                            dateLabel = person.birthDate.orEmpty(),
                            daysRemaining = days,
                            category = OccasionCategory.BIRTHDAY,
                            relatedPersonId = person.id,
                        )
                    )
                }
            }
        }

        // 2. Death memorials for deceased persons with exact MM-DD death dates
        for (person in persons) {
            if (person.isDeceased && person.deathDatePrecision == DatePrecision.EXACT) {
                val days = daysUntilAnnualMonthDay(person.deathDate, today) ?: continue
                if (days in 0..horizonDays) {
                    results.add(
                        UpcomingOccasion(
                            id = "death_${person.id}",
                            title = "ذكرى وفاة ${person.fullName}",
                            subtitle = if (days == 0) "اليوم" else "بعد $days يوم",
                            dateLabel = person.deathDate.orEmpty(),
                            daysRemaining = days,
                            category = OccasionCategory.DEATH_MEMORIAL,
                            relatedPersonId = person.id,
                        )
                    )
                }
            }
        }

        // 3. Wedding anniversaries for active marriages
        for (marriage in marriages) {
            if (marriage.marriageStatus == MarriageRecordStatus.ACTIVE &&
                marriage.marriageDatePrecision == DatePrecision.EXACT
            ) {
                val days = daysUntilAnnualMonthDay(marriage.marriageDate, today) ?: continue
                if (days in 0..horizonDays) {
                    val p1 = personsById[marriage.partnerOneId]?.firstName ?: ""
                    val p2 = personsById[marriage.partnerTwoId]?.firstName ?: ""
                    results.add(
                        UpcomingOccasion(
                            id = "anniv_${marriage.id}",
                            title = "ذكرى زواج $p1 و$p2",
                            subtitle = if (days == 0) "اليوم" else "بعد $days يوم",
                            dateLabel = marriage.marriageDate.orEmpty(),
                            daysRemaining = days,
                            category = OccasionCategory.MARRIAGE_ANNIVERSARY,
                            relatedPersonId = marriage.partnerOneId,
                        )
                    )
                }
            }
        }

        // 4. Recorded events with exact dates in the next horizonDays
        for (event in events) {
            if (event.eventDatePrecision == DatePrecision.EXACT) {
                val days = daysUntilExactDate(event.eventDate, today) ?: continue
                if (days in 0..horizonDays) {
                    val personName = event.personId?.let { personsById[it]?.fullName }
                    results.add(
                        UpcomingOccasion(
                            id = "evt_${event.id}",
                            title = event.description?.takeIf { it.isNotBlank() }
                                ?: "${event.eventType.arabicLabel}${personName?.let { " - $it" } ?: ""}",
                            subtitle = if (days == 0) "اليوم" else "بعد $days يوم",
                            dateLabel = event.eventDate.orEmpty(),
                            daysRemaining = days,
                            category = OccasionCategory.RECORDED_EVENT,
                            relatedPersonId = event.personId,
                        )
                    )
                }
            }
        }

        return results.sortedBy { it.daysRemaining }.take(limit)
    }

    private fun daysUntilAnnualMonthDay(isoDate: String?, today: Calendar): Int? {
        if (isoDate.isNullOrBlank()) return null
        val parts = isoDate.trim().split("-")
        if (parts.size < 3) return null
        val month = parts[1].toIntOrNull()?.minus(1) ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 0..11 || day !in 1..31) return null

        val currentDay = GregorianCalendar(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH),
        )
        val nextOccurrence = GregorianCalendar(today.get(Calendar.YEAR), month, day)
        if (nextOccurrence.before(currentDay)) {
            nextOccurrence.add(Calendar.YEAR, 1)
        }
        val diffMillis = nextOccurrence.timeInMillis - currentDay.timeInMillis
        return (diffMillis / (24L * 60 * 60 * 1000)).toInt()
    }

    private fun daysUntilExactDate(isoDate: String?, today: Calendar): Int? {
        if (isoDate.isNullOrBlank()) return null
        val parts = isoDate.trim().split("-")
        if (parts.size < 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull()?.minus(1) ?: return null
        val day = parts[2].toIntOrNull() ?: return null

        val currentDay = GregorianCalendar(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH),
        )
        val target = GregorianCalendar(year, month, day)
        val diffMillis = target.timeInMillis - currentDay.timeInMillis
        val days = (diffMillis / (24L * 60 * 60 * 1000)).toInt()
        return if (days >= 0) days else null
    }
}
