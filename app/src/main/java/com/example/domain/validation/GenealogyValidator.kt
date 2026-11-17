package com.example.domain.validation

import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MaritalStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person

sealed interface ValidationOutcome {
    data object Valid : ValidationOutcome
    data class Invalid(val reasonArabic: String) : ValidationOutcome
}

class GenealogyValidationException(message: String) : IllegalArgumentException(message)

object GenealogyValidator {

    const val MAX_CONCURRENT_WIVES = 4

    fun validatePersonInput(firstName: String, fullName: String): ValidationOutcome {
        if (firstName.isBlank() && fullName.isBlank()) {
            return ValidationOutcome.Invalid("يجب إدخال الاسم الأول أو الاسم الكامل على الأقل.")
        }
        return ValidationOutcome.Valid
    }

    fun validateParentChildLink(
        parent: Person,
        child: Person,
        relationshipType: ParentRelationshipType,
        existingLinks: List<ParentChildLink>,
        associatedMarriage: Marriage?,
    ): ValidationOutcome {
        if (parent.id == child.id) {
            return ValidationOutcome.Invalid("لا يمكن ربط الشخص بوصفه ابناً أو والداً لنفسه.")
        }

        val duplicateExactLink = existingLinks.any {
            it.parentId == parent.id &&
                it.childId == child.id &&
                it.relationshipType == relationshipType
        }
        if (duplicateExactLink) {
            return ValidationOutcome.Invalid("علاقة النسب هذه مسجلة مسبقاً بين الطرفين.")
        }

        // Enforce single confirmed biological father and single confirmed biological mother
        if (relationshipType == ParentRelationshipType.BIOLOGICAL_FATHER) {
            val hasFather = existingLinks.any {
                it.childId == child.id && it.relationshipType == ParentRelationshipType.BIOLOGICAL_FATHER
            }
            if (hasFather) {
                return ValidationOutcome.Invalid("الطفل مرتبط مسبقاً بأب بيولوجي مؤكد.")
            }
        }
        if (relationshipType == ParentRelationshipType.BIOLOGICAL_MOTHER) {
            val hasMother = existingLinks.any {
                it.childId == child.id && it.relationshipType == ParentRelationshipType.BIOLOGICAL_MOTHER
            }
            if (hasMother) {
                return ValidationOutcome.Invalid("الطفل مرتبط مسبقاً بأم بيولوجية مؤكدة.")
            }
        }

        if (associatedMarriage != null && !associatedMarriage.involves(parent.id)) {
            return ValidationOutcome.Invalid("الزواج المختار لا ينتمي إلى هذا الوالد.")
        }

        // Prevent cycles: if child is already an ancestor of parent, adding parent -> child creates a cycle.
        if (isAncestor(candidateAncestorId = child.id, targetDescendantId = parent.id, links = existingLinks)) {
            return ValidationOutcome.Invalid("علاقة دائرية غير منطقية: الابن مسجل بالفعل كأحد أسلاف الوالد.")
        }

        return ValidationOutcome.Valid
    }

    /**
     * Validates Islamic marriage rules:
     * - Different genders required (Male + Female).
     * - No self-marriage.
     * - No duplicate active marriage between the same two partners.
     * - A husband may have up to 4 concurrent ACTIVE marriages (plus unlimited historical ended marriages).
     * - A wife may have at most 1 concurrent ACTIVE marriage (plus unlimited historical ended marriages).
     */
    fun validateMarriage(
        partnerOne: Person,
        partnerTwo: Person,
        status: MarriageRecordStatus,
        existingMarriages: List<Marriage>,
        editingMarriageId: String? = null,
    ): ValidationOutcome {
        if (partnerOne.id == partnerTwo.id) {
            return ValidationOutcome.Invalid("لا يمكن تسجيل زواج للشخص مع نفسه.")
        }

        if (partnerOne.gender == partnerTwo.gender) {
            return ValidationOutcome.Invalid("الزواج الشرعي يكون بين رجل وامرأة فقط.")
        }

        val activeDuplicate = existingMarriages.any { m ->
            m.id != editingMarriageId &&
                m.marriageStatus == MarriageRecordStatus.ACTIVE &&
                status == MarriageRecordStatus.ACTIVE &&
                ((m.partnerOneId == partnerOne.id && m.partnerTwoId == partnerTwo.id) ||
                    (m.partnerOneId == partnerTwo.id && m.partnerTwoId == partnerOne.id))
        }
        if (activeDuplicate) {
            return ValidationOutcome.Invalid("يوجد زواج قائم مسجل بالفعل بين هذين الشخصين.")
        }

        if (status == MarriageRecordStatus.ACTIVE) {
            val husband = if (partnerOne.gender == Gender.MALE) partnerOne else partnerTwo
            val wife = if (partnerOne.gender == Gender.FEMALE) partnerOne else partnerTwo

            val husbandActiveCount = existingMarriages.count {
                it.id != editingMarriageId &&
                    it.marriageStatus == MarriageRecordStatus.ACTIVE &&
                    it.involves(husband.id)
            }
            if (husbandActiveCount >= MAX_CONCURRENT_WIVES) {
                return ValidationOutcome.Invalid(
                    "لا يمكن تسجيل أكثر من 4 زوجات في عصمة واحدة في الوقت نفسه. يمكنك إنهاء زواج سابق أو تسجيل الزواج بوصفه سابقاً."
                )
            }

            val wifeHasActiveMarriage = existingMarriages.any {
                it.id != editingMarriageId &&
                    it.marriageStatus == MarriageRecordStatus.ACTIVE &&
                    it.involves(wife.id)
            }
            if (wifeHasActiveMarriage) {
                return ValidationOutcome.Invalid(
                    "الزوجة مرتبطة مسبقاً بزواج قائم؛ يمكن تسجيل زواج جديد لها بعد انتهاء الزواج السابق بالطلاق أو الوفاة."
                )
            }
        }

        return ValidationOutcome.Valid
    }

    fun isAncestor(
        candidateAncestorId: String,
        targetDescendantId: String,
        links: List<ParentChildLink>,
    ): Boolean {
        if (candidateAncestorId == targetDescendantId) return true
        val parentsByChild = links.groupBy { it.childId }
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(targetDescendantId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            val parentLinks = parentsByChild[current].orEmpty()
            for (link in parentLinks) {
                if (link.parentId == candidateAncestorId) {
                    return true
                }
                if (link.parentId !in visited) {
                    queue.add(link.parentId)
                }
            }
        }
        return false
    }

    /**
     * Recomputes a person's marital status strictly from all marriage records.
     * Ending one marriage (divorce or death) NEVER clears other active marriages.
     */
    fun recomputeMaritalStatus(
        personId: String,
        marriagesForPerson: List<Marriage>,
        fallbackWhenNoMarriages: MaritalStatus = MaritalStatus.SINGLE,
    ): MaritalStatus {
        if (marriagesForPerson.isEmpty()) return fallbackWhenNoMarriages
        if (marriagesForPerson.any { it.marriageStatus == MarriageRecordStatus.ACTIVE }) {
            return MaritalStatus.MARRIED
        }
        val latestEnded = marriagesForPerson.maxByOrNull { it.updatedAt }
        return when (latestEnded?.marriageStatus) {
            MarriageRecordStatus.DIVORCED -> MaritalStatus.DIVORCED
            MarriageRecordStatus.ENDED_BY_DEATH -> MaritalStatus.WIDOWED
            MarriageRecordStatus.ACTIVE -> MaritalStatus.MARRIED
            null -> fallbackWhenNoMarriages
        }
    }
}
