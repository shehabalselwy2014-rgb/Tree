package com.example.domain.util

import com.example.domain.model.Person

data class AutoCompletedChildName(
    val firstName: String,
    val middleName: String?,
    val familyName: String?,
    val fullName: String,
)

object PatronymicNameHelper {

    /**
     * Auto-completes a child's patronymic Arabic full name from the father's full name.
     * Example: father = "عبدالرحيم عثمان سيف السامعي", childFirstName = "شهاب"
     * -> fullName = "شهاب عبدالرحيم عثمان سيف السامعي"
     */
    fun buildFromFather(childFirstName: String, father: Person?): AutoCompletedChildName {
        val cleanFirst = childFirstName.trim()
        if (father == null) {
            return AutoCompletedChildName(
                firstName = cleanFirst,
                middleName = null,
                familyName = null,
                fullName = cleanFirst,
            )
        }

        val fatherFull = father.fullName.trim()
        val fullName = if (cleanFirst.isEmpty()) {
            fatherFull
        } else if (fatherFull.isEmpty()) {
            cleanFirst
        } else {
            "$cleanFirst $fatherFull"
        }

        val middleChain = listOfNotNull(
            father.firstName.trim().takeIf { it.isNotEmpty() },
            father.middleName?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").takeIf { it.isNotEmpty() }

        return AutoCompletedChildName(
            firstName = cleanFirst,
            middleName = middleChain,
            familyName = father.familyName?.trim()?.takeIf { it.isNotEmpty() },
            fullName = fullName,
        )
    }
}
