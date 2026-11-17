package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.DatePrecision
import com.example.domain.model.Gender
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.UserPreferences
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.domain.util.HijriDateConverter
import com.example.presentation.JuthoorViewModel
import com.example.presentation.navigation.JuthoorAppRoot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseTwoMetricsAndUiTest {

    private val openDatabases = mutableListOf<JuthoorDatabase>()
    val composeTestRule = createComposeRule()

    @get:Rule
    val testRuleChain: org.junit.rules.RuleChain = org.junit.rules.RuleChain
        .outerRule(object : org.junit.rules.ExternalResource() {
            override fun after() {
                openDatabases.forEach { runCatching { it.close() } }
                openDatabases.clear()
            }
        })
        .around(composeTestRule)

    @Test
    fun `home metrics calculate generations living deceased and siblings accurately`() {
        val g1 = Person("g1", "جد العائلة", "جد", gender = Gender.MALE, birthDate = "1930", birthDatePrecision = DatePrecision.YEAR_ONLY, deathDate = "1995", deathDatePrecision = DatePrecision.YEAR_ONLY)
        val g2 = Person("g2", "أم العائلة", "أم", gender = Gender.FEMALE)
        val father = Person("f1", "الوالد", "الوالد", gender = Gender.MALE, birthDate = "1960", birthDatePrecision = DatePrecision.YEAR_ONLY)
        val child1 = Person("c1", "الابن الأول", "الأول", gender = Gender.MALE, birthDate = "1990", birthDatePrecision = DatePrecision.YEAR_ONLY)
        val child2 = Person("c2", "الابنة الثانية", "الثانية", gender = Gender.FEMALE)

        val persons = listOf(g1, g2, father, child1, child2)
        val links = listOf(
            com.example.domain.model.ParentChildLink("l1", g1.id, father.id, ParentRelationshipType.BIOLOGICAL_FATHER),
            com.example.domain.model.ParentChildLink("l2", g2.id, father.id, ParentRelationshipType.BIOLOGICAL_MOTHER),
            com.example.domain.model.ParentChildLink("l3", father.id, child1.id, ParentRelationshipType.BIOLOGICAL_FATHER),
            com.example.domain.model.ParentChildLink("l4", father.id, child2.id, ParentRelationshipType.BIOLOGICAL_FATHER),
        )

        val overview = GenealogyMetricsCalculator.computeHomeOverview(persons, emptyList(), links)
        assertEquals(5, overview.totalPersons)
        assertEquals(3, overview.malesCount)
        assertEquals(2, overview.femalesCount)
        assertEquals(4, overview.livingCount)
        assertEquals(1, overview.deceasedCount)
        assertEquals(3, overview.generationsCount)

        val siblings = GenealogyMetricsCalculator.findSiblings(child1.id, persons.associateBy { it.id }, links)
        assertEquals(1, siblings.size)
        assertEquals(child2.id, siblings.first().id)
    }

    @Test
    fun `hijri converter formats exact and year-only dates cleanly`() {
        val formattedYearOnly = HijriDateConverter.formatDateForDisplay("1994", DatePrecision.YEAR_ONLY, includeHijri = true)
        assertTrue(formattedYearOnly.contains("1994"))
        assertTrue(formattedYearOnly.contains("هـ"))

        val formattedGregorianOnly = HijriDateConverter.formatDateForDisplay("1994-05-12", DatePrecision.EXACT, includeHijri = false)
        assertEquals("1994-05-12", formattedGregorianOnly)
    }

    @Test
    fun `navigation shell renders real database records on home persons and profile`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
        val repo = RoomGenealogyRepository(db)
        val prefs = PreferencesRepository(context)
        val media = LocalMediaStorage(context)

        repo.savePerson(
            Person(
                id = "test_founder",
                fullName = "سالم الاختباري",
                firstName = "سالم",
                familyName = "الاختباري",
                gender = Gender.MALE,
                birthDate = "1950",
                birthDatePrecision = DatePrecision.YEAR_ONLY,
            )
        ).getOrThrow()

        val viewModel = JuthoorViewModel(repo, prefs, media)
        val state = com.example.presentation.JuthoorUiState(
            isLoading = false,
            preferences = UserPreferences(familyTitle = "عائلة الاختبار", onboardingCompleted = true),
            shouldShowOnboarding = false,
            persons = listOf(repo.getPersonById("test_founder")!!),
            overviewMetrics = GenealogyMetricsCalculator.computeHomeOverview(
                listOf(repo.getPersonById("test_founder")!!),
                emptyList(),
                emptyList(),
            ),
            generationByPerson = mapOf("test_founder" to 1),
            recentPersons = listOf(repo.getPersonById("test_founder")!!),
        )

        composeTestRule.setContent {
            JuthoorAppRoot(uiState = state, viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("عائلة الاختبار").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_total_persons").assertIsDisplayed()

        // Navigate to Persons tab and open profile
        composeTestRule.onNodeWithTag("nav_persons").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("person_list_card_test_founder").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("سالم الاختباري").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_add_child").assertIsDisplayed()
    }
}
