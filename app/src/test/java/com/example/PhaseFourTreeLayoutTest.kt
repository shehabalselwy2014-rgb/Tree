package com.example

import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.tree.TreeLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseFourTreeLayoutTest {

    private fun makePerson(id: String, first: String, gender: Gender, birthYear: String? = null) = Person(
        id = id,
        fullName = "$first آل جِذور",
        firstName = first,
        gender = gender,
        birthDate = birthYear,
    )

    @Test
    fun `tree layout handles 7 generations top down without vertical regression`() {
        val persons = (1..7).map { gen ->
            makePerson("p$gen", "الجد$gen", Gender.MALE, (1860 + gen * 22).toString())
        }
        val links = (1..6).map { gen ->
            ParentChildLink(
                id = "l$gen",
                parentId = "p$gen",
                childId = "p${gen + 1}",
                relationshipType = ParentRelationshipType.BIOLOGICAL_FATHER,
            )
        }

        val layout = TreeLayoutEngine.buildLayout(persons, emptyList(), links)
        assertEquals(7, layout.maxGeneration)
        assertEquals(7, layout.personNodes.size)

        for (gen in 1..6) {
            val parentNode = layout.personNodes.first { it.person.id == "p$gen" }
            val childNode = layout.personNodes.first { it.person.id == "p${gen + 1}" }
            assertEquals(gen, parentNode.generation)
            assertEquals(gen + 1, childNode.generation)
            assertTrue(childNode.y > parentNode.y)
        }
    }

    @Test
    fun `polygamy places primary parent once with 4 distinct union hubs separating children`() {
        val husband = makePerson("h", "خالد", Gender.MALE, "1960")
        val w1 = makePerson("w1", "فاطمة", Gender.FEMALE, "1965")
        val w2 = makePerson("w2", "عائشة", Gender.FEMALE, "1970")
        val w3 = makePerson("w3", "مريم", Gender.FEMALE, "1975")
        val w4 = makePerson("w4", "زينب", Gender.FEMALE, "1980")

        val c1 = makePerson("c1", "أحمد", Gender.MALE, "1990")
        val c2 = makePerson("c2", "سعد", Gender.MALE, "1995")

        val marriages = listOf(
            Marriage("m1", husband.id, w1.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 1L),
            Marriage("m2", husband.id, w2.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 2L),
            Marriage("m3", husband.id, w3.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 3L),
            Marriage("m4", husband.id, w4.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 4L),
        )
        val links = listOf(
            ParentChildLink("lc1", husband.id, c1.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m1"),
            ParentChildLink("lc2", husband.id, c2.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m3"),
        )

        val layout = TreeLayoutEngine.buildLayout(
            persons = listOf(husband, w1, w2, w3, w4, c1, c2),
            marriages = marriages,
            links = links,
        )

        // Husband is placed exactly once
        assertEquals(1, layout.personNodes.count { it.person.id == husband.id })
        // 4 distinct marriage union hubs exist
        assertEquals(4, layout.unionNodes.size)
        val union1 = layout.unionNodes.first { it.marriage.id == "m1" }
        val union3 = layout.unionNodes.first { it.marriage.id == "m3" }
        assertEquals(1, union1.childrenCount)
        assertEquals(1, union3.childrenCount)
        assertTrue(union1.x != union3.x)
    }

    @Test
    fun `collapse hides subtree and ancestor path search identifies nodes to expand`() {
        val root = makePerson("r", "إبراهيم", Gender.MALE)
        val child = makePerson("c", "سليمان", Gender.MALE)
        val grandChild = makePerson("gc", "يوسف", Gender.MALE)
        val links = listOf(
            ParentChildLink("l1", root.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER),
            ParentChildLink("l2", child.id, grandChild.id, ParentRelationshipType.BIOLOGICAL_FATHER),
        )

        val collapsed = TreeLayoutEngine.buildLayout(
            persons = listOf(root, child, grandChild),
            marriages = emptyList(),
            links = links,
            collapsedPersonIds = setOf(child.id),
        )
        assertTrue(collapsed.personNodes.any { it.person.id == child.id })
        assertFalse(collapsed.personNodes.any { it.person.id == grandChild.id })

        val path = TreeLayoutEngine.findAncestorPathToRoot(grandChild.id, links)
        assertTrue(root.id in path)
        assertTrue(child.id in path)
        assertTrue(grandChild.id in path)
    }

    @Test
    fun `tree layout scales cleanly to 1050 synthetic records`() {
        val persons = ArrayList<Person>(1050)
        val links = ArrayList<ParentChildLink>(1049)
        persons.add(makePerson("p0", "الأصل", Gender.MALE, "1800"))
        for (i in 1 until 1050) {
            val parentIdx = (i - 1) / 3
            val id = "p$i"
            persons.add(makePerson(id, "فرد$i", if (i % 2 == 0) Gender.MALE else Gender.FEMALE))
            links.add(
                ParentChildLink(
                    id = "l$i",
                    parentId = "p$parentIdx",
                    childId = id,
                    relationshipType = ParentRelationshipType.BIOLOGICAL_FATHER,
                )
            )
        }

        val startMs = System.currentTimeMillis()
        val result = TreeLayoutEngine.buildLayout(persons, emptyList(), links)
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals(1050, result.personNodes.size)
        assertTrue("Expected layout under 2000ms, took ${elapsedMs}ms", elapsedMs < 2000L)
    }
}
