package com.example.domain.stats

import com.example.domain.model.FamilyGroup
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.Marriage
import com.example.domain.model.ParentChildLink
import com.example.domain.model.Person
import com.example.domain.util.GenealogyMetricsCalculator
import java.util.Calendar
import java.util.GregorianCalendar

data class GenerationBucket(
    val generation: Int,
    val count: Int,
)

data class DecadeBucket(
    val label: String,
    val startYear: Int,
    val count: Int,
)

data class BranchBucket(
    val groupName: String,
    val count: Int,
)

data class PersonAgeSummary(
    val person: Person,
    val estimatedAgeYears: Int,
)

data class FamilyStatisticsReport(
    val totalPersons: Int = 0,
    val malesCount: Int = 0,
    val femalesCount: Int = 0,
    val livingCount: Int = 0,
    val deceasedCount: Int = 0,
    val marriedCount: Int = 0,
    val singleCount: Int = 0,
    val divorcedCount: Int = 0,
    val widowedCount: Int = 0,
    val totalMarriagesCount: Int = 0,
    val generationsCount: Int = 0,
    val averageAgeYears: Double? = null,
    val averageAgeSampleCount: Int = 0,
    val averageAgeExplanation: String = "",
    val oldestLivingPerson: PersonAgeSummary? = null,
    val oldestRecordedPerson: PersonAgeSummary? = null,
    val generationDistribution: List<GenerationBucket> = emptyList(),
    val birthDecadeDistribution: List<DecadeBucket> = emptyList(),
    val branchDistribution: List<BranchBucket> = emptyList(),
)

object StatisticsEngine {

    fun compute(
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        familyGroups: List<FamilyGroup>,
        scopedRootPersonId: String? = null,
        referenceYear: Int = GregorianCalendar().get(Calendar.YEAR),
    ): FamilyStatisticsReport {
        val scopedPersons = if (scopedRootPersonId == null) {
            persons.distinctBy { it.id }
        } else {
            val includedIds = collectSubtreeIds(scopedRootPersonId, links, marriages)
            persons.filter { it.id in includedIds }.distinctBy { it.id }
        }

        if (scopedPersons.isEmpty()) {
            return FamilyStatisticsReport(
                averageAgeExplanation = "لا توجد سجلات أفراد لحساب متوسط الأعمار.",
            )
        }

        val scopedIds = scopedPersons.map { it.id }.toSet()
        val scopedMarriages = if (scopedRootPersonId == null) {
            marriages.distinctBy { it.id }
        } else {
            marriages.filter { it.partnerOneId in scopedIds || it.partnerTwoId in scopedIds }.distinctBy { it.id }
        }
        val scopedLinks = links.filter { it.parentId in scopedIds && it.childId in scopedIds }

        val generationByPerson = GenealogyMetricsCalculator.computeGenerationByPerson(scopedPersons, scopedLinks)
        val genDistribution = generationByPerson.values
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { GenerationBucket(generation = it.key, count = it.value) }

        val livingAges = mutableListOf<PersonAgeSummary>()
        val allAges = mutableListOf<PersonAgeSummary>()
        val birthYears = mutableListOf<Int>()

        for (person in scopedPersons) {
            val birthYear = GenealogyMetricsCalculator.extractBirthYear(person.birthDate) ?: continue
            birthYears.add(birthYear)
            val deathYear = GenealogyMetricsCalculator.extractBirthYear(person.deathDate)
            val endYear = if (person.isDeceased) (deathYear ?: referenceYear) else referenceYear
            val age = (endYear - birthYear).coerceAtLeast(0)
            if (age <= 130) {
                val entry = PersonAgeSummary(person, age)
                allAges.add(entry)
                if (!person.isDeceased) {
                    livingAges.add(entry)
                }
            }
        }

        val avgAge = if (livingAges.isNotEmpty()) {
            livingAges.map { it.estimatedAgeYears }.average()
        } else if (allAges.isNotEmpty()) {
            allAges.map { it.estimatedAgeYears }.average()
        } else null

        val avgSampleCount = if (livingAges.isNotEmpty()) livingAges.size else allAges.size
        val avgExplanation = when {
            livingAges.isNotEmpty() ->
                "محسوب للأحياء ذوي سنة الميلاد المعروفة فقط (${livingAges.size} من أصل ${scopedPersons.size} فرداً)."
            allAges.isNotEmpty() ->
                "محسوب للأشخاص ذوي سنة الميلاد والوفاة المسجلة (${allAges.size} فرداً)."
            else ->
                "لا تتوفر تواريخ ميلاد معروفة كافية لحساب متوسط العمر."
        }

        val decadeDistribution = birthYears
            .map { (it / 10) * 10 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { (decade, count) ->
                DecadeBucket(label = "${decade}s", startYear = decade, count = count)
            }

        val groupsById = familyGroups.associateBy { it.id }
        val branchDistribution = scopedPersons
            .groupingBy { it.familyGroupId?.let { gid -> groupsById[gid]?.groupName } ?: "غير مصنف بفرع" }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { BranchBucket(groupName = it.key, count = it.value) }

        return FamilyStatisticsReport(
            totalPersons = scopedPersons.size,
            malesCount = scopedPersons.count { it.gender == Gender.MALE },
            femalesCount = scopedPersons.count { it.gender == Gender.FEMALE },
            livingCount = scopedPersons.count { !it.isDeceased },
            deceasedCount = scopedPersons.count { it.isDeceased },
            marriedCount = scopedPersons.count { it.currentMaritalStatus == MaritalStatus.MARRIED },
            singleCount = scopedPersons.count { it.currentMaritalStatus == MaritalStatus.SINGLE },
            divorcedCount = scopedPersons.count { it.currentMaritalStatus == MaritalStatus.DIVORCED },
            widowedCount = scopedPersons.count { it.currentMaritalStatus == MaritalStatus.WIDOWED },
            totalMarriagesCount = scopedMarriages.size,
            generationsCount = genDistribution.maxOfOrNull { it.generation } ?: 1,
            averageAgeYears = avgAge,
            averageAgeSampleCount = avgSampleCount,
            averageAgeExplanation = avgExplanation,
            oldestLivingPerson = livingAges.maxByOrNull { it.estimatedAgeYears },
            oldestRecordedPerson = allAges.maxByOrNull { it.estimatedAgeYears },
            generationDistribution = genDistribution,
            birthDecadeDistribution = decadeDistribution,
            branchDistribution = branchDistribution,
        )
    }

    private fun collectSubtreeIds(
        rootPersonId: String,
        links: List<ParentChildLink>,
        marriages: List<Marriage>,
    ): Set<String> {
        val childrenByParent = links.groupBy { it.parentId }
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootPersonId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            childrenByParent[current].orEmpty().forEach { link ->
                if (link.childId !in visited) {
                    queue.add(link.childId)
                }
            }
        }
        // Include spouses of members in this subtree
        val withSpouses = HashSet<String>(visited)
        for (m in marriages) {
            if (m.partnerOneId in visited) withSpouses.add(m.partnerTwoId)
            if (m.partnerTwoId in visited) withSpouses.add(m.partnerOneId)
        }
        return withSpouses
    }
}
