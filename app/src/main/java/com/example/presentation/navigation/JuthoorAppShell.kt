package com.example.presentation.navigation

import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.ThemeMode
import com.example.presentation.JuthoorUiState
import com.example.presentation.JuthoorViewModel
import com.example.presentation.album.FamilyAlbumScreen
import com.example.presentation.cloud.FamilyAccountScreen
import com.example.presentation.cloud.FamilyMembersAndInvitationsScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.occasions.OccasionsScreen
import com.example.presentation.onboarding.OnboardingScreen
import com.example.presentation.persons.AddEditPersonScreen
import com.example.presentation.persons.PersonsListScreen
import com.example.presentation.profile.PersonProfileScreen
import com.example.presentation.security.AppLockScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.stats.StatisticsScreen
import com.example.presentation.tree.InteractiveFamilyTreeScreen
import com.example.ui.theme.JuthoorTheme

object JuthoorRoutes {
    const val HOME = "home"
    const val TREE = "tree?focusPersonId={focusPersonId}"
    const val PERSONS = "persons"
    const val STATS = "stats"
    const val ALBUM = "album"
    const val OCCASIONS = "occasions"
    const val SETTINGS = "settings"
    const val FAMILY_ACCOUNT = "family_account"
    const val FAMILY_MEMBERS = "family_members"
    const val PERSON_PROFILE = "person_profile/{personId}?promptNext={promptNext}"
    const val ADD_EDIT_PERSON = "add_edit_person?personId={personId}&parentId={parentId}"

    fun tree(focusPersonId: String? = null) =
        if (focusPersonId != null) "tree?focusPersonId=$focusPersonId" else "tree"

    fun personProfile(personId: String, promptNext: Boolean = false) =
        "person_profile/$personId?promptNext=$promptNext"

    fun addPerson(parentId: String? = null) =
        if (parentId != null) "add_edit_person?parentId=$parentId" else "add_edit_person"

    fun editPerson(personId: String) = "add_edit_person?personId=$personId"
}

private data class TopLevelDestination(
    val routePattern: String,
    val navTarget: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val bottomDestinations = listOf(
    TopLevelDestination(JuthoorRoutes.HOME, JuthoorRoutes.HOME, "الرئيسية", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopLevelDestination(JuthoorRoutes.TREE, JuthoorRoutes.tree(), "الشجرة", Icons.Filled.AccountTree, Icons.Outlined.AccountTree, "nav_tree"),
    TopLevelDestination(JuthoorRoutes.PERSONS, JuthoorRoutes.PERSONS, "الأفراد", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_persons"),
    TopLevelDestination(JuthoorRoutes.STATS, JuthoorRoutes.STATS, "الإحصائيات", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_stats"),
    TopLevelDestination(JuthoorRoutes.ALBUM, JuthoorRoutes.ALBUM, "الألبوم", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_album"),
)

private val railDestinations = bottomDestinations + listOf(
    TopLevelDestination(JuthoorRoutes.OCCASIONS, JuthoorRoutes.OCCASIONS, "المناسبات", Icons.Filled.Celebration, Icons.Outlined.Celebration, "nav_occasions"),
    TopLevelDestination(JuthoorRoutes.SETTINGS, JuthoorRoutes.SETTINGS, "الإعدادات", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"),
)

@Composable
fun JuthoorAppRoot(
    uiState: JuthoorUiState,
    viewModel: JuthoorViewModel,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (uiState.preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    var pendingFounderId by remember { mutableStateOf<String?>(null) }

    JuthoorTheme(darkTheme = useDarkTheme) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.shouldRequireUnlock) {
            AppLockScreen(
                familyTitle = uiState.preferences.familyTitle,
                biometricEnabled = uiState.preferences.biometricEnabled,
                onVerifyPin = viewModel::verifyAndUnlockWithPin,
                onBiometricUnlocked = viewModel::unlockWithBiometric,
            )
        } else if (uiState.shouldShowOnboarding) {
            OnboardingScreen(
                initialFamilyTitle = uiState.preferences.familyTitle,
                initialFamilyMotto = uiState.preferences.familyMotto,
                onComplete = { title, motto, first, middle, family, gender, birth ->
                    viewModel.completeOnboarding(
                        familyTitle = title,
                        familyMotto = motto,
                        founderFirstName = first,
                        founderMiddleName = middle,
                        founderFamilyName = family,
                        founderGender = gender,
                        founderBirthDate = birth,
                        onFounderCreated = { founderId ->
                            pendingFounderId = founderId
                        },
                    )
                },
                onSkip = viewModel::skipOnboarding,
            )
        } else {
            JuthoorMainShell(
                uiState = uiState,
                viewModel = viewModel,
                initialPersonIdToPrompt = pendingFounderId,
                onConsumedInitialPrompt = { pendingFounderId = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JuthoorMainShell(
    uiState: JuthoorUiState,
    viewModel: JuthoorViewModel,
    initialPersonIdToPrompt: String?,
    onConsumedInitialPrompt: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopLevelChrome = railDestinations.any { it.routePattern == currentRoute }

    val snackbarHostState = remember { SnackbarHostState() }
    val activeMessage = uiState.errorMessage ?: uiState.feedbackMessage
    LaunchedEffect(activeMessage) {
        if (activeMessage != null) {
            snackbarHostState.showSnackbar(activeMessage)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(initialPersonIdToPrompt) {
        if (!initialPersonIdToPrompt.isNullOrBlank()) {
            navController.navigate(JuthoorRoutes.personProfile(initialPersonIdToPrompt, promptNext = true))
            onConsumedInitialPrompt()
        }
    }

    val navigateToTab: (String) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = !target.contains("focusPersonId=")
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 640.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (showTopLevelChrome && !useNavigationRail && currentRoute != JuthoorRoutes.TREE) {
                    val activeTitle = railDestinations.firstOrNull { it.routePattern == currentRoute }?.label
                        ?: uiState.preferences.familyTitle
                    TopAppBar(
                        title = { Text(activeTitle) },
                        actions = {
                            IconButton(
                                onClick = { navigateToTab(JuthoorRoutes.OCCASIONS) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_occasions"),
                            ) {
                                Icon(Icons.Filled.Celebration, contentDescription = "المناسبات")
                            }
                            IconButton(
                                onClick = { navigateToTab(JuthoorRoutes.SETTINGS) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_settings"),
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (showTopLevelChrome && !useNavigationRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        bottomDestinations.forEach { dest ->
                            val selected = currentRoute == dest.routePattern
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.navTarget) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (showTopLevelChrome && useNavigationRail) {
                    NavigationRail {
                        railDestinations.forEach { dest ->
                            val selected = currentRoute == dest.routePattern
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.navTarget) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = JuthoorRoutes.HOME,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    composable(JuthoorRoutes.HOME) {
                        HomeScreen(
                            uiState = uiState,
                            onOpenTree = { navigateToTab(JuthoorRoutes.tree()) },
                            onAddPerson = { navController.navigate(JuthoorRoutes.addPerson()) },
                            onOpenSearch = { navigateToTab(JuthoorRoutes.PERSONS) },
                            onOpenOccasions = { navigateToTab(JuthoorRoutes.OCCASIONS) },
                            onOpenSettings = { navigateToTab(JuthoorRoutes.SETTINGS) },
                            onOpenPersonProfile = { personId ->
                                navController.navigate(JuthoorRoutes.personProfile(personId))
                            },
                        )
                    }

                    composable(
                        route = JuthoorRoutes.TREE,
                        arguments = listOf(
                            navArgument("focusPersonId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val context = LocalContext.current
                        val focusPersonId = entry.arguments?.getString("focusPersonId")
                        val launchShare: (Boolean) -> Unit = { asPdf ->
                            viewModel.shareTreeAsFile(asPdf = asPdf) { shareUri, mimeType ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "شجرة ${uiState.preferences.familyTitle}")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة شجرة العائلة"))
                                }
                            }
                        }
                        InteractiveFamilyTreeScreen(
                            uiState = uiState,
                            focusPersonId = focusPersonId,
                            onOpenPersonProfile = { personId ->
                                navController.navigate(JuthoorRoutes.personProfile(personId))
                            },
                            onExportTreePngUri = viewModel::exportTreePng,
                            onExportPdfUri = viewModel::exportPdf,
                            onShareTreePng = { launchShare(false) },
                            onShareTreePdf = { launchShare(true) },
                        )
                    }

                    composable(JuthoorRoutes.PERSONS) {
                        PersonsListScreen(
                            uiState = uiState,
                            onAddPerson = { navController.navigate(JuthoorRoutes.addPerson()) },
                            onOpenPersonProfile = { personId ->
                                navController.navigate(JuthoorRoutes.personProfile(personId))
                            },
                        )
                    }

                    composable(JuthoorRoutes.STATS) {
                        StatisticsScreen(
                            uiState = uiState,
                            onSelectScopeRoot = viewModel::setStatsScopeRoot,
                            onOpenPersonProfile = { personId ->
                                navController.navigate(JuthoorRoutes.personProfile(personId))
                            },
                        )
                    }

                    composable(JuthoorRoutes.ALBUM) {
                        FamilyAlbumScreen(
                            uiState = uiState,
                            onSavePhoto = viewModel::savePhotoFromUri,
                            onDeletePhoto = viewModel::deletePhoto,
                            onOpenPersonProfile = { personId ->
                                navController.navigate(JuthoorRoutes.personProfile(personId))
                            },
                        )
                    }

                    composable(JuthoorRoutes.OCCASIONS) {
                        OccasionsScreen(
                            uiState = uiState,
                            onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                            onSaveEvent = viewModel::saveEvent,
                            onOpenPersonProfile = { personId ->
                                navController.navigate(JuthoorRoutes.personProfile(personId))
                            },
                        )
                    }

                    composable(JuthoorRoutes.SETTINGS) {
                        SettingsScreen(
                            uiState = uiState,
                            onUpdateFamilyIdentity = viewModel::updateFamilyIdentity,
                            onSetThemeMode = viewModel::setThemeMode,
                            onSetShowHijri = viewModel::setShowHijriDates,
                            onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                            onAddFamilyGroup = { name, desc -> viewModel.saveFamilyGroup(name, desc) },
                            onDeleteFamilyGroup = viewModel::deleteFamilyGroup,
                            onConfigurePin = viewModel::configurePin,
                            onDisablePin = viewModel::disablePin,
                            onSetBiometricEnabled = viewModel::setBiometricEnabled,
                            onLockNow = viewModel::lockAppNow,
                            onExportBackupUri = viewModel::exportBackup,
                            onInspectBackupUri = viewModel::inspectBackupForRestore,
                            onConfirmRestore = viewModel::confirmRestoreFromPreview,
                            onDismissBackupPreview = viewModel::dismissBackupPreview,
                            onExportPdfUri = viewModel::exportPdf,
                            onExportTreePngUri = viewModel::exportTreePng,
                            onExportCsvUri = viewModel::exportCsv,
                            onOpenFamilyAccount = { navController.navigate(JuthoorRoutes.FAMILY_ACCOUNT) },
                            onOpenFamilyMembers = {
                                viewModel.refreshCollaborationData(silent = true)
                                navController.navigate(JuthoorRoutes.FAMILY_MEMBERS)
                            },
                            onSyncNow = viewModel::syncNow,
                        )
                    }

                    composable(JuthoorRoutes.FAMILY_ACCOUNT) {
                        FamilyAccountScreen(
                            uiState = uiState,
                            onNavigateBack = { navController.popBackStack() },
                            onSignIn = viewModel::signInWithEmail,
                            onSignUp = viewModel::signUpWithEmail,
                            onSignOut = viewModel::signOutFromCloud,
                            onCreateCloudFamilySpace = viewModel::createCloudFamilySpace,
                            onRefreshFamilies = viewModel::refreshCloudFamilies,
                            onSwitchActiveFamily = viewModel::switchActiveFamily,
                            onAcceptInvitation = { code, onDone ->
                                viewModel.acceptFamilyInvitation(code, onDone)
                            },
                            onOpenFamilyMembers = {
                                viewModel.refreshCollaborationData(silent = true)
                                navController.navigate(JuthoorRoutes.FAMILY_MEMBERS)
                            },
                            onSyncNow = viewModel::syncNow,
                            onResolveConflict = viewModel::resolveSyncConflict,
                            onSaveCloudConfig = viewModel::saveCustomCloudConfig,
                            onClearCloudConfig = viewModel::clearCustomCloudConfig,
                        )
                    }

                    composable(JuthoorRoutes.FAMILY_MEMBERS) {
                        FamilyMembersAndInvitationsScreen(
                            uiState = uiState,
                            onNavigateBack = { navController.popBackStack() },
                            onRefresh = { viewModel.refreshCollaborationData(silent = false) },
                            onCreateInvitation = viewModel::createFamilyInvitation,
                            onDismissGeneratedInvite = viewModel::dismissGeneratedInvite,
                            onRevokeInvitation = viewModel::revokeFamilyInvitation,
                            onUpdateMemberLinkAndPermissions = viewModel::updateMemberLinkAndPermissions,
                        )
                    }

                    composable(
                        route = JuthoorRoutes.PERSON_PROFILE,
                        arguments = listOf(
                            navArgument("personId") { type = NavType.StringType },
                            navArgument("promptNext") {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                    ) { entry ->
                        val personId = entry.arguments?.getString("personId").orEmpty()
                        val promptNext = entry.arguments?.getBoolean("promptNext") ?: false
                        PersonProfileScreen(
                            personId = personId,
                            promptNextOnEntry = promptNext,
                            uiState = uiState,
                            onNavigateBack = { navController.popBackStack() },
                            onEditPerson = { id ->
                                navController.navigate(JuthoorRoutes.editPerson(id))
                            },
                            onQuickAddChild = { father, mother, marriageId, first, customFull, gender, birth, isKafala, isUnconfirmed ->
                                viewModel.addQuickChild(
                                    father = father,
                                    mother = mother,
                                    marriageId = marriageId,
                                    childFirstName = first,
                                    customFullName = customFull,
                                    gender = gender,
                                    birthDate = birth,
                                    isKafala = isKafala,
                                    isUnconfirmed = isUnconfirmed,
                                )
                            },
                            onSaveOrUpdateMarriage = { existingId, p1, p2, date, place, status, endD, endR, notes, onSaved ->
                                viewModel.saveOrUpdateMarriage(
                                    existingMarriageId = existingId,
                                    partnerOneId = p1,
                                    partnerTwoId = p2,
                                    marriageDate = date,
                                    marriagePlace = place,
                                    status = status,
                                    endDate = endD,
                                    endReason = endR,
                                    notes = notes,
                                    onSaved = onSaved,
                                )
                            },
                            onCreatePartnerAndMarry = { anchor, pFirst, pFull, pBirth, date, place, status, endD, endR, notes, onSaved ->
                                viewModel.createPartnerAndMarry(
                                    anchorPerson = anchor,
                                    partnerFirstName = pFirst,
                                    partnerFullName = pFull,
                                    partnerBirthDate = pBirth,
                                    marriageDate = date,
                                    marriagePlace = place,
                                    status = status,
                                    endDate = endD,
                                    endReason = endR,
                                    notes = notes,
                                    onSaved = onSaved,
                                )
                            },
                            onEndMarriage = viewModel::endMarriage,
                            onDeleteMarriage = viewModel::deleteMarriage,
                            onLinkParentChild = viewModel::addParentChildLink,
                            onUnlinkParentChild = viewModel::deleteParentChildLink,
                            onOpenPersonProfile = { targetId ->
                                navController.navigate(JuthoorRoutes.personProfile(targetId))
                            },
                            onOpenTreeForPerson = { targetId ->
                                navigateToTab(JuthoorRoutes.tree(focusPersonId = targetId))
                            },
                            onDeletePerson = { id ->
                                viewModel.deletePerson(id) {
                                    navController.popBackStack()
                                }
                            },
                            educationFlow = viewModel.observeEducationForPerson(personId),
                            eventsFlow = viewModel.observeEventsForPerson(personId),
                            onSaveEducation = { qual, spec, inst, yr, notes ->
                                viewModel.saveEducation(personId, qual, spec, inst, yr, notes)
                            },
                            onDeleteEducation = viewModel::deleteEducation,
                            onSaveEvent = { evtType, date, place, desc ->
                                viewModel.saveEvent(personId, evtType, date, place, desc)
                            },
                            onDeleteEvent = viewModel::deleteEvent,
                            onSavePhoto = viewModel::savePhotoFromUri,
                            onDeletePhoto = viewModel::deletePhoto,
                        )
                    }

                    composable(
                        route = JuthoorRoutes.ADD_EDIT_PERSON,
                        arguments = listOf(
                            navArgument("personId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("parentId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val editingId = entry.arguments?.getString("personId")
                        val parentId = entry.arguments?.getString("parentId")
                        AddEditPersonScreen(
                            uiState = uiState,
                            editingPersonId = editingId,
                            prefilledParentId = parentId,
                            onCopyImage = viewModel::copyPickedImageToInternal,
                            onSave = { person, fatherId, motherId, marriageId ->
                                viewModel.savePerson(
                                    person = person,
                                    optionalFatherId = fatherId,
                                    optionalMotherId = motherId,
                                    optionalMarriageId = marriageId,
                                ) { saved ->
                                    if (editingId == null) {
                                        navController.navigate(
                                            JuthoorRoutes.personProfile(saved.id, promptNext = true)
                                        ) {
                                            popUpTo(JuthoorRoutes.ADD_EDIT_PERSON) { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            },
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
