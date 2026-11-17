package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.DatePrecision
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.UserPreferences
import com.example.domain.stats.StatisticsEngine
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.presentation.JuthoorUiState
import com.example.presentation.JuthoorViewModel
import com.example.presentation.navigation.JuthoorAppRoot
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseFourFiveSixUiTest {

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
    fun `bottom navigation switches across tree stats album and occasions with real records`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
        val repo = RoomGenealogyRepository(db)
        val prefs = PreferencesRepository(context)
        val media = LocalMediaStorage(context)

        val root = Person(
            id = "u_root",
            fullName = "عبدالرحيم عثمان",
            firstName = "عبدالرحيم",
            gender = Gender.MALE,
            birthDate = "1956-04-12",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.MARRIED,
        )
        val child = Person(
            id = "u_child",
            fullName = "شهاب عبدالرحيم عثمان",
            firstName = "شهاب",
            gender = Gender.MALE,
            birthDate = "1994-06-10",
            birthDatePrecision = DatePrecision.EXACT,
            currentMaritalStatus = MaritalStatus.SINGLE,
        )
        repo.savePerson(root).getOrThrow()
        repo.savePerson(child).getOrThrow()
        repo.addParentChildLink(root.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER).getOrThrow()

        val persons = listOf(root, child)
        val links = listOf(ParentChildLink("l1", root.id, child.id, ParentRelationshipType.BIOLOGICAL_FATHER))
        val viewModel = JuthoorViewModel(repo, prefs, media)
        val uiState = JuthoorUiState(
            isLoading = false,
            preferences = UserPreferences(familyTitle = "عائلة السامعي", onboardingCompleted = true),
            shouldShowOnboarding = false,
            persons = persons,
            parentChildLinks = links,
            overviewMetrics = GenealogyMetricsCalculator.computeHomeOverview(persons, emptyList(), links),
            statisticsReport = StatisticsEngine.compute(persons, emptyList(), links, emptyList()),
            generationByPerson = GenealogyMetricsCalculator.computeGenerationByPerson(persons, links),
            upcomingOccasions = GenealogyMetricsCalculator.computeUpcomingOccasions(persons, emptyList(), emptyList()),
            yearOccasions = GenealogyMetricsCalculator.computeUpcomingOccasions(persons, emptyList(), emptyList(), horizonDays = 365, limit = 50),
            recentPersons = persons,
        )

        composeTestRule.setContent {
            JuthoorAppRoot(uiState = uiState, viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // 1. Open Interactive Tree tab
        composeTestRule.onNodeWithTag("nav_tree").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithTag("interactive_tree_canvas").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithTag("tree_node_u_root").assertIsDisplayed()

        // 2. Open Statistics tab
        composeTestRule.onNodeWithTag("nav_stats").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stats_total_persons").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_average_age"))
        composeTestRule.onNodeWithTag("stats_average_age").assertIsDisplayed()

        // 3. Open Family Album tab
        composeTestRule.onNodeWithTag("nav_album").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_add_photo_fab").assertIsDisplayed()

        // 4. Open Home -> Occasions
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_occasions_button"))
        composeTestRule.onNodeWithTag("home_occasions_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("المناسبات والأحداث العائلية").assertIsDisplayed()
        composeTestRule.onNodeWithTag("occasions_notifications_switch").assertIsDisplayed()
    }
}
