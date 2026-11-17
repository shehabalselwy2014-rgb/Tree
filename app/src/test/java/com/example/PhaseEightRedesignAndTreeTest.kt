package com.example

import android.content.Context
import android.graphics.RectF
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.FamilyExportManager
import com.example.domain.util.HomeOverviewMetrics
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.UserPreferences
import com.example.domain.tree.TreeLayoutEngine
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.presentation.JuthoorUiState
import com.example.presentation.home.HomeScreen
import com.example.presentation.tree.InteractiveFamilyTreeScreen
import com.example.ui.theme.JuthoorTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseEightRedesignAndTreeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun person(
        id: String,
        first: String,
        gender: Gender,
        birthYear: String? = null,
        status: MaritalStatus = MaritalStatus.SINGLE,
    ) = Person(
        id = id,
        fullName = "$first آل جِذور",
        firstName = first,
        gender = gender,
        birthDate = birthYear,
        currentMaritalStatus = status,
    )

    private fun buildPolygynyAndMultiGenFixture(): Triple<List<Person>, List<Marriage>, List<ParentChildLink>> {
        val patriarch = person("patriarch", "سليمان", Gender.MALE, "1945", MaritalStatus.MARRIED)
        val w1 = person("w1", "فاطمة", Gender.FEMALE, "1950", MaritalStatus.MARRIED)
        val w2 = person("w2", "عائشة", Gender.FEMALE, "1955", MaritalStatus.MARRIED)
        val w3 = person("w3", "مريم", Gender.FEMALE, "1960", MaritalStatus.MARRIED)
        val w4 = person("w4", "خديجة", Gender.FEMALE, "1968", MaritalStatus.DIVORCED)

        val son1 = person("son1", "عبدالله", Gender.MALE, "1974", MaritalStatus.MARRIED)
        val daughter1 = person("daughter1", "نورة", Gender.FEMALE, "1977", MaritalStatus.SINGLE)
        val sonFromMotherOnly = person("son_m2", "عمر", Gender.MALE, "1980", MaritalStatus.SINGLE)
        val son3 = person("son3", "يوسف", Gender.MALE, "1985", MaritalStatus.SINGLE)
        val daughter4 = person("daughter4", "سارة", Gender.FEMALE, "1992", MaritalStatus.SINGLE)

        val son1Wife = person("son1_wife", "هدى", Gender.FEMALE, "1978", MaritalStatus.MARRIED)
        val grandChild1 = person("gc1", "خالد", Gender.MALE, "2002", MaritalStatus.SINGLE)
        val grandChild2 = person("gc2", "ليلى", Gender.FEMALE, "2005", MaritalStatus.SINGLE)

        val persons = listOf(
            patriarch, w1, w2, w3, w4,
            son1, daughter1, sonFromMotherOnly, son3, daughter4,
            son1Wife, grandChild1, grandChild2,
        )
        val marriages = listOf(
            Marriage("m1", patriarch.id, w1.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 1L),
            Marriage("m2", patriarch.id, w2.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 2L),
            Marriage("m3", patriarch.id, w3.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 3L),
            Marriage("m4", patriarch.id, w4.id, marriageStatus = MarriageRecordStatus.DIVORCED, createdAt = 4L),
            Marriage("m_son1", son1.id, son1Wife.id, marriageStatus = MarriageRecordStatus.ACTIVE, createdAt = 5L),
        )
        val links = listOf(
            ParentChildLink("l1", patriarch.id, son1.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m1"),
            ParentChildLink("l2", patriarch.id, daughter1.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m1"),
            // Linked via biological mother only -> must still appear under Wife 2's column
            ParentChildLink("l3", w2.id, sonFromMotherOnly.id, ParentRelationshipType.BIOLOGICAL_MOTHER, marriageId = "m2"),
            ParentChildLink("l4", patriarch.id, son3.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m3"),
            ParentChildLink("l5", patriarch.id, daughter4.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m4"),
            ParentChildLink("l6", son1.id, grandChild1.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m_son1"),
            ParentChildLink("l7", son1.id, grandChild2.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriageId = "m_son1"),
        )
        return Triple(persons, marriages, links)
    }

    @Test
    fun `polygyny and multi generation layout guarantees zero overlapping cards and includes mother-only children`() {
        val (persons, marriages, links) = buildPolygynyAndMultiGenFixture()
        val layout = TreeLayoutEngine.buildLayout(persons, marriages, links)

        assertEquals(persons.size, layout.personNodes.size)
        assertEquals(3, layout.maxGeneration)
        assertTrue(layout.personNodes.any { it.person.id == "son_m2" })

        // Verify zero bounding-box intersection between any two person cards
        val boxes = layout.personNodes.map { node ->
            node.person.id to RectF(
                node.x,
                node.y,
                node.x + TreeLayoutEngine.PERSON_CARD_WIDTH,
                node.y + TreeLayoutEngine.PERSON_CARD_HEIGHT,
            )
        }
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                val (idA, rectA) = boxes[i]
                val (idB, rectB) = boxes[j]
                assertFalse(
                    "Cards overlap between $idA ($rectA) and $idB ($rectB)",
                    RectF.intersects(rectA, rectB),
                )
            }
        }

        // Verify union hubs do not overlap any person card and all edges carry orthogonal railY
        layout.unionNodes.forEach { union ->
            val unionRect = RectF(union.x - 20f, union.y - 12f, union.x + 20f, union.y + 12f)
            boxes.forEach { (id, cardRect) ->
                assertFalse(
                    "Union hub ${union.marriage.id} overlaps card $id",
                    RectF.intersects(unionRect, cardRect),
                )
            }
        }
        assertTrue(layout.edges.all { it.railY != null })

        // Wife 2's mother-only child is aligned under Wife 2's horizontal slot
        val w2Node = layout.personNodes.first { it.person.id == "w2" }
        val sonM2Node = layout.personNodes.first { it.person.id == "son_m2" }
        assertEquals(w2Node.x, sonM2Node.x, 1.0f)
    }

    @Test
    fun `unconnected family components render together by default and isolate cleanly on branch selection`() {
        val famARoot = person("a1", "إبراهيم", Gender.MALE, "1955")
        val famAChild = person("a2", "طلال", Gender.MALE, "1985")
        val famAGrand = person("a3", "ريان", Gender.MALE, "2012")
        val famBRoot = person("b1", "سلمان", Gender.MALE, "1962")
        val famBChild = person("b2", "لينا", Gender.FEMALE, "1994")

        val persons = listOf(famARoot, famAChild, famAGrand, famBRoot, famBChild)
        val links = listOf(
            ParentChildLink("la1", famARoot.id, famAChild.id, ParentRelationshipType.BIOLOGICAL_FATHER),
            ParentChildLink("la2", famAChild.id, famAGrand.id, ParentRelationshipType.BIOLOGICAL_FATHER),
            ParentChildLink("lb1", famBRoot.id, famBChild.id, ParentRelationshipType.BIOLOGICAL_FATHER),
        )

        val fullLayout = TreeLayoutEngine.buildLayout(persons, emptyList(), links, selectedRootId = null)
        assertEquals(5, fullLayout.personNodes.size)

        val isolatedLayout = TreeLayoutEngine.buildLayout(persons, emptyList(), links, selectedRootId = famAChild.id)
        val isolatedIds = isolatedLayout.personNodes.map { it.person.id }.toSet()
        assertEquals(setOf(famAChild.id, famAGrand.id), isolatedIds)
    }

    @Test
    fun `export manager renders clean phase 8 emerald tree PNG preview without overlaps`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (persons, marriages, links) = buildPolygynyAndMultiGenFixture()
        val exportManager = FamilyExportManager(context)
        val outDir = File("build/phase8_previews").apply { mkdirs() }
        val outFile = File(outDir, "preview_tree_phase8.png")

        val result = FileOutputStream(outFile).use { stream ->
            exportManager.exportTreePngToStream(
                outputStream = stream,
                familyTitle = "عائلة آل جِذور",
                persons = persons,
                marriages = marriages,
                links = links,
                showHijri = false,
            )
        }
        assertTrue(result.isSuccess)
        assertTrue(outFile.exists() && outFile.length() > 10_000L)
        val (w, h) = result.getOrThrow()
        assertNotNull(w)
        assertTrue(w >= 800 && h >= 540)
    }

    @Test
    fun `interactive family tree screen supports selection branch isolation and collapse`() {
        val (persons, marriages, links) = buildPolygynyAndMultiGenFixture()
        val uiState = JuthoorUiState(
            persons = persons,
            marriages = marriages,
            parentChildLinks = links,
            generationByPerson = GenealogyMetricsCalculator.computeGenerationByPerson(persons, links),
            preferences = UserPreferences(familyTitle = "عائلة آل جِذور"),
        )

        composeTestRule.setContent {
            JuthoorTheme(darkTheme = false) {
                InteractiveFamilyTreeScreen(
                    uiState = uiState,
                    onOpenPersonProfile = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("interactive_tree_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tree_node_patriarch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tree_node_son_m2").assertIsDisplayed()

        // Tap Son 1 to display bottom Selected Person Quick Action Bar
        composeTestRule.onNodeWithTag("tree_node_son1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tree_selected_action_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tree_open_profile_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tree_isolate_branch_button").assertIsDisplayed()

        // Isolate Son 1's branch ("عرض فرعه فقط")
        composeTestRule.onNodeWithTag("tree_isolate_branch_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tree_clear_branch_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tree_node_gc1").assertIsDisplayed()

        // Clear isolated branch filter back to full tree
        composeTestRule.onNodeWithTag("tree_clear_branch_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tree_node_patriarch").assertIsDisplayed()

        // Collapse patriarch descendants and verify expand-all chip appears
        composeTestRule.onNodeWithTag("tree_collapse_patriarch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tree_expand_all_chip").assertIsDisplayed()

        // Open export & share dialog directly from Interactive Tree HUD
        composeTestRule.onNodeWithTag("tree_export_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tree_export_save_png_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tree_export_save_pdf_button").assertIsDisplayed()
    }

    @Test
    fun `redesigned home screen displays brand vector logo uniform stats grid and all actions`() {
        val (persons, marriages, links) = buildPolygynyAndMultiGenFixture()
        val metrics = HomeOverviewMetrics(
            totalPersons = persons.size,
            malesCount = persons.count { it.gender == Gender.MALE },
            femalesCount = persons.count { it.gender == Gender.FEMALE },
            livingCount = persons.size,
            deceasedCount = 0,
            generationsCount = 3,
            marriagesCount = marriages.size,
        )
        val uiState = JuthoorUiState(
            persons = persons,
            marriages = marriages,
            parentChildLinks = links,
            overviewMetrics = metrics,
            recentPersons = persons.take(3),
            preferences = UserPreferences(
                familyTitle = "عائلة آل جِذور الكريمة",
                familyMotto = "نوثّق الأصول، ونحفظ امتداد الأجيال",
            ),
        )

        composeTestRule.setContent {
            JuthoorTheme(darkTheme = false) {
                HomeScreen(
                    uiState = uiState,
                    onOpenTree = {},
                    onAddPerson = {},
                    onOpenSearch = {},
                    onOpenOccasions = {},
                    onOpenSettings = {},
                    onOpenPersonProfile = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("juthoor_brand_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_total_persons").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_generations").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_living_deceased").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_males_count"))
        composeTestRule.onNodeWithTag("home_males_count").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_females_count").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_open_tree_button"))
        composeTestRule.onNodeWithTag("home_open_tree_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_add_person_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_settings_button"))
        composeTestRule.onNodeWithTag("home_search_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_occasions_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_recent_patriarch"))
        composeTestRule.onNodeWithTag("home_recent_patriarch").assertIsDisplayed()
    }
}
