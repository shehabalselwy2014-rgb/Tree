package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.AuthSessionStore
import com.example.data.preferences.PreferencesRepository
import com.example.data.remote.HttpSupabaseGateway
import com.example.data.remote.RemoteAuthResult
import com.example.data.remote.RemoteFamilyMembership
import com.example.data.remote.RemoteInvitation
import com.example.data.remote.RemoteMemberProfile
import com.example.data.remote.RemotePushOutcome
import com.example.data.remote.RemoteSession
import com.example.data.remote.RemoteSyncDelta
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseGateway
import com.example.data.repository.RoomAndSupabaseAuthRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.AuthActionResult
import com.example.domain.model.DatePrecision
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyGroup
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.MultiUserDefaults
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState
import com.example.presentation.JuthoorViewModel
import com.example.presentation.navigation.JuthoorAppRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseNineThreeAuthAndFamilySpaceTest {

    private val openDatabases = mutableListOf<JuthoorDatabase>()
    val composeTestRule = createComposeRule()

    @get:Rule
    val testRuleChain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun after() {
                openDatabases.forEach { runCatching { it.close() } }
                openDatabases.clear()
            }
        })
        .around(composeTestRule)

    @Test
    fun `supabase config safely distinguishes placeholder from real configuration`() {
        val placeholderConfig = SupabaseConfig(
            url = "https://YOUR_PROJECT_REF.supabase.co",
            anonKey = "YOUR_SUPABASE_ANON_KEY",
        )
        assertFalse(placeholderConfig.isConfigured)

        val blankConfig = SupabaseConfig(url = "", anonKey = "")
        assertFalse(blankConfig.isConfigured)

        val validConfig = SupabaseConfig(
            url = "https://abcdefg.supabase.co/",
            anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.real_key",
        )
        assertTrue(validConfig.isConfigured)
    }

    @Test
    fun `http supabase gateway executes auth and rpc calls and translates errors cleanly`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val capturedBodies = mutableListOf<String>()
        val capturedAuthHeaders = mutableListOf<String?>()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedUrls.add(req.url.toString())
                capturedAuthHeaders.add(req.header("Authorization"))
                val buf = Buffer()
                req.body?.writeTo(buf)
                capturedBodies.add(buf.readUtf8())

                val (code, body) = when {
                    req.url.encodedPath.endsWith("/auth/v1/signup") -> {
                        val payload = JSONObject(capturedBodies.last())
                        if (payload.getString("email").startsWith("confirm")) {
                            200 to JSONObject().put("id", "usr_pending").toString()
                        } else {
                            200 to JSONObject().apply {
                                put("access_token", "jwt_signup_token")
                                put("refresh_token", "ref_signup")
                                put("expires_in", 3600)
                                put(
                                    "user",
                                    JSONObject()
                                        .put("id", "usr_founder_1")
                                        .put("email", payload.getString("email"))
                                        .put("user_metadata", JSONObject().put("display_name", "عبدالرحيم عثمان"))
                                )
                            }.toString()
                        }
                    }

                    req.url.encodedPath.endsWith("/auth/v1/token") -> {
                        val payload = JSONObject(capturedBodies.last())
                        if (payload.optString("password") == "wrongpass") {
                            400 to JSONObject().put("error_description", "Invalid login credentials").toString()
                        } else {
                            200 to JSONObject().apply {
                                put("access_token", "jwt_signin_token")
                                put("refresh_token", "ref_signin")
                                put("expires_in", 3600)
                                put(
                                    "user",
                                    JSONObject()
                                        .put("id", "usr_founder_1")
                                        .put("email", "founder@juthoor.test")
                                        .put("user_metadata", JSONObject().put("display_name", "عبدالرحيم عثمان"))
                                )
                            }.toString()
                        }
                    }

                    req.url.encodedPath.endsWith("/rest/v1/rpc/create_family_space") -> {
                        val reqJson = JSONObject(capturedBodies.last())
                        200 to JSONObject().apply {
                            put("family_id", reqJson.getString("p_family_id"))
                            put("name", reqJson.getString("p_name"))
                            put("motto", reqJson.getString("p_motto"))
                            put("owner_user_id", "usr_founder_1")
                            put("root_person_id", JSONObject.NULL)
                            put("member_id", "mem_owner_1")
                            put("role", "OWNER")
                            put("created_at", 1700000100000L)
                            put("updated_at", 1700000100000L)
                        }.toString()
                    }

                    req.url.encodedPath.endsWith("/rest/v1/rpc/get_my_family_memberships") -> {
                        200 to JSONArray().put(
                            JSONObject().apply {
                                put("family_id", "fam_1")
                                put("family_name", "عائلة السامعي")
                                put("family_motto", "نوثّق الأصول")
                                put("owner_user_id", "usr_founder_1")
                                put("root_person_id", JSONObject.NULL)
                                put("family_version", 1)
                                put("family_created_at", 1700000100000L)
                                put("family_updated_at", 1700000100000L)
                                put("member_id", "mem_owner_1")
                                put("linked_person_id", JSONObject.NULL)
                                put("role", "OWNER")
                                put("branch_root_person_id", JSONObject.NULL)
                                put("can_invite", true)
                                put("can_export", true)
                                put("can_manage_media", true)
                                put("status", "ACTIVE")
                                put("joined_at", 1700000100000L)
                                put("member_updated_at", 1700000100000L)
                                put("member_version", 1)
                            }
                        ).toString()
                    }

                    else -> 200 to "{}"
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val gateway = HttpSupabaseGateway(
            config = SupabaseConfig(
                url = "https://demo.supabase.co",
                anonKey = "anon_test_key",
            ),
            client = client,
        )

        val confirmResult = gateway.signUp("confirm@juthoor.test", "secret123", "مستخدم").getOrThrow()
        assertTrue(confirmResult is RemoteAuthResult.ConfirmationRequired)

        val authResult = gateway.signUp("founder@juthoor.test", "secret123", "عبدالرحيم عثمان").getOrThrow()
        assertTrue(authResult is RemoteAuthResult.Authenticated)
        assertEquals("usr_founder_1", (authResult as RemoteAuthResult.Authenticated).session.userId)

        val invalidCreds = gateway.signInWithPassword("founder@juthoor.test", "wrongpass")
        assertTrue(invalidCreds.isFailure)
        assertTrue(invalidCreds.exceptionOrNull()?.message?.contains("البريد الإلكتروني أو كلمة المرور غير صحيحة") == true)

        val session = gateway.signInWithPassword("founder@juthoor.test", "secret123").getOrThrow()
        assertEquals("jwt_signin_token", session.accessToken)

        val famId = UUID.randomUUID().toString()
        val created = gateway.createFamilySpace(
            accessToken = session.accessToken,
            familyId = famId,
            name = "عائلة السامعي",
            motto = "نوثّق الأصول",
            rootPersonId = null,
        ).getOrThrow()
        assertEquals(famId, created.familyId)
        assertEquals(FamilyRole.OWNER, created.role)
        assertEquals("Bearer jwt_signin_token", capturedAuthHeaders.last())

        val fetched = gateway.fetchMyFamilyMemberships(session.accessToken).getOrThrow()
        assertEquals(1, fetched.size)
        assertEquals("عائلة السامعي", fetched.first().familyName)
    }

    @Test
    fun `creating cloud family space adopts existing local tree non-destructively and signOut preserves local data`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openDatabases.add(it) }
            val genealogyRepo = RoomGenealogyRepository(db)
            val prefs = PreferencesRepository(context)
            val authStore = AuthSessionStore(context)
            authStore.clearSession()
            authStore.updateActiveFamilyId(null)

            // Pre-populate rich local offline tree under local_default_family
            val group = genealogyRepo.saveFamilyGroup(
                FamilyGroup(id = UUID.randomUUID().toString(), groupName = "فرع السامعي")
            ).getOrThrow()
            val father = genealogyRepo.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "عبدالرحيم عثمان سيف",
                    firstName = "عبدالرحيم",
                    gender = Gender.MALE,
                    birthDate = "1956-04-12",
                    birthDatePrecision = DatePrecision.EXACT,
                    familyGroupId = group.id,
                )
            ).getOrThrow()
            val mother = genealogyRepo.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "نعمة أحمد ناجي",
                    firstName = "نعمة",
                    gender = Gender.FEMALE,
                    familyGroupId = group.id,
                )
            ).getOrThrow()
            val son = genealogyRepo.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "شهاب عبدالرحيم عثمان",
                    firstName = "شهاب",
                    gender = Gender.MALE,
                    familyGroupId = group.id,
                )
            ).getOrThrow()
            val marriage = genealogyRepo.saveMarriage(
                Marriage(
                    id = UUID.randomUUID().toString(),
                    partnerOneId = father.id,
                    partnerTwoId = mother.id,
                    marriageStatus = MarriageRecordStatus.ACTIVE,
                )
            ).getOrThrow()
            genealogyRepo.addParentChildLink(father.id, son.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriage.id)
                .getOrThrow()
            genealogyRepo.saveEducation(
                EducationRecord(
                    id = UUID.randomUUID().toString(),
                    personId = son.id,
                    qualification = "بكالوريوس",
                )
            ).getOrThrow()
            genealogyRepo.savePhoto(
                PhotoRecord(
                    id = UUID.randomUUID().toString(),
                    personId = father.id,
                    taggedPersonIds = listOf(father.id, son.id),
                    filePath = "/tmp/test.jpg",
                    mediaType = MediaType.PERSON_PORTRAIT,
                )
            ).getOrThrow()
            genealogyRepo.saveEvent(
                EventRecord(
                    id = UUID.randomUUID().toString(),
                    personId = son.id,
                    eventType = FamilyEventType.GRADUATION,
                )
            ).getOrThrow()

            val fakeGateway = FakeSupabaseGateway()
            val cloudRepo = RoomAndSupabaseAuthRepository(db, authStore, prefs, fakeGateway)

            val signInSession = cloudRepo.signIn("founder@juthoor.example", "123456").getOrThrow()
            assertEquals("usr_founder_fake", signInSession.userId)
            assertNotNull(db.userAccountDao().getById("usr_founder_fake"))

            val summary = cloudRepo.createCloudFamilySpace(
                name = "عائلة السامعي المركزية",
                motto = "توثيق النسب",
                founderPersonId = father.id,
                adoptExistingLocalTree = true,
            ).getOrThrow()

            val cloudFamilyId = summary.family.id
            assertEquals(FamilyRole.OWNER, summary.membership.role)
            assertEquals(father.id, summary.family.rootPersonId)
            assertEquals(father.id, summary.membership.linkedPersonId)
            // Bootstrap RPC receives null root_person_id until Phase 9.5 pushes persons rows
            assertNull(fakeGateway.lastCreateRootPersonId)

            // Verify all local records promoted non-destructively to the new cloud familyId & queued for sync
            val migratedPersons = db.personDao().getAllForFamilyOnce(cloudFamilyId)
            val migratedMarriages = db.marriageDao().getAllForFamilyOnce(cloudFamilyId)
            val migratedLinks = db.parentChildDao().getAllForFamilyOnce(cloudFamilyId)
            val migratedEdu = db.educationDao().getAllForFamilyOnce(cloudFamilyId)
            val migratedPhotos = db.photoDao().getAllForFamilyOnce(cloudFamilyId)
            val migratedEvents = db.eventDao().getAllForFamilyOnce(cloudFamilyId)
            val migratedGroups = db.familyGroupDao().getAllForFamilyOnce(cloudFamilyId)

            assertEquals(3, migratedPersons.size)
            assertEquals(1, migratedMarriages.size)
            assertEquals(1, migratedLinks.size)
            assertEquals(1, migratedEdu.size)
            assertEquals(1, migratedPhotos.size)
            assertEquals(1, migratedEvents.size)
            assertEquals(1, migratedGroups.size)
            assertTrue(migratedPersons.all { it.syncState == SyncState.PENDING_UPSERT })
            assertEquals(0, db.personDao().getAllForFamilyOnce(MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID).size)

            val outbox = db.syncOutboxDao().getPendingForFamily(cloudFamilyId)
            assertTrue(outbox.any { it.entityType == SyncEntityType.FAMILY_GROUP })
            assertTrue(outbox.count { it.entityType == SyncEntityType.PERSON } == 3)
            assertTrue(outbox.any { it.entityType == SyncEntityType.MARRIAGE })
            assertTrue(outbox.any { it.entityType == SyncEntityType.PARENT_CHILD })
            assertTrue(outbox.any { it.entityType == SyncEntityType.FAMILY && it.entityId == cloudFamilyId })
            assertTrue(outbox.all { it.status == SyncOutboxStatus.PENDING })

            val loadedPrefs = prefs.preferencesFlow.first()
            assertEquals("عائلة السامعي المركزية", loadedPrefs.familyTitle)

            // Sign out clears auth tokens but leaves all family genealogy data 100% intact
            cloudRepo.signOut().getOrThrow()
            assertNull(cloudRepo.currentSession.first())
            assertEquals(3, genealogyRepo.getAllPersonsOnce().size)
            assertEquals(1, genealogyRepo.getAllMarriagesOnce().size)
        }

    @Test
    fun `family account screen supports signup and cloud family space creation flow`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
        val genealogyRepo = RoomGenealogyRepository(db)
        val prefs = PreferencesRepository(context)
        val authStore = AuthSessionStore(context)
        authStore.clearSession()
        authStore.updateActiveFamilyId(null)
        prefs.clearPinLock()
        prefs.markOnboardingCompleted()

        val media = LocalMediaStorage(context)
        genealogyRepo.savePerson(
            Person(
                id = UUID.randomUUID().toString(),
                fullName = "عبدالرحيم عثمان سيف",
                firstName = "عبدالرحيم",
                gender = Gender.MALE,
            )
        ).getOrThrow()

        val fakeGateway = FakeSupabaseGateway()
        val cloudRepo = RoomAndSupabaseAuthRepository(db, authStore, prefs, fakeGateway)
        val viewModel = JuthoorViewModel(
            repository = genealogyRepo,
            preferencesRepository = prefs,
            mediaStorage = media,
            cloudAuthRepository = cloudRepo,
        )

        composeTestRule.setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            JuthoorAppRoot(uiState = state, viewModel = viewModel)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            !viewModel.uiState.value.isLoading && viewModel.uiState.value.persons.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Navigate to Settings -> Family Account screen
        composeTestRule.onNodeWithTag("topbar_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_family_account_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("cloud_backend_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_mode_signup_tab").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_display_name_input").performScrollTo().performTextInput("عبدالرحيم عثمان")
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("founder@juthoor.test")
        composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("secret123")
        composeTestRule.onNodeWithTag("auth_submit_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.authSession != null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("signed_in_account_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("founder@juthoor.test").assertIsDisplayed()

        // Create cloud family space
        composeTestRule.onNodeWithTag("family_account_list")
            .performScrollToNode(hasTestTag("create_family_card"))
        composeTestRule.onNodeWithTag("create_family_name_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("create_family_name_input").performTextInput("أسرة السامعي")
        composeTestRule.onNodeWithTag("family_account_list")
            .performScrollToNode(hasTestTag("family_account_bottom_spacer"))
        composeTestRule.onNodeWithTag("create_family_submit_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            val err = viewModel.uiState.value.errorMessage
            if (err != null) {
                throw AssertionError("Unexpected ViewModel error: $err")
            }
            viewModel.uiState.value.familySpaces.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("family_account_list")
            .performScrollToNode(hasTestTag("family_spaces_card"))
        composeTestRule.onNodeWithTag("family_spaces_card").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("أسرة السامعي").fetchSemanticsNodes().isNotEmpty())
        assertEquals("أسرة السامعي", viewModel.uiState.value.activeFamilySpace?.name)
        composeTestRule.onNodeWithText("نشطة").assertIsDisplayed()
    }

    private class FakeSupabaseGateway : SupabaseGateway {
        override val isConfigured: Boolean = true
        var lastCreateRootPersonId: String? = "UNSET"
        private val createdFamilies = mutableListOf<RemoteFamilyMembership>()

        override suspend fun signUp(email: String, password: String, displayName: String): Result<RemoteAuthResult> =
            Result.success(
                RemoteAuthResult.Authenticated(
                    RemoteSession(
                        userId = "usr_founder_fake",
                        email = email,
                        displayName = displayName,
                        accessToken = "fake_jwt_token",
                        refreshToken = "fake_refresh_token",
                        expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
                    )
                )
            )

        override suspend fun signInWithPassword(email: String, password: String): Result<RemoteSession> =
            Result.success(
                RemoteSession(
                    userId = "usr_founder_fake",
                    email = email,
                    displayName = "عبدالرحيم عثمان",
                    accessToken = "fake_jwt_token",
                    refreshToken = "fake_refresh_token",
                    expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
                )
            )

        override suspend fun refreshToken(refreshToken: String): Result<RemoteSession> =
            signInWithPassword("founder@juthoor.example", "")

        override suspend fun signOut(accessToken: String): Result<Unit> = Result.success(Unit)

        override suspend fun upsertUserProfile(
            accessToken: String,
            userId: String,
            email: String,
            displayName: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun createFamilySpace(
            accessToken: String,
            familyId: String,
            name: String,
            motto: String,
            rootPersonId: String?,
        ): Result<RemoteFamilyMembership> {
            lastCreateRootPersonId = rootPersonId
            val now = System.currentTimeMillis()
            val membership = RemoteFamilyMembership(
                familyId = familyId,
                familyName = name,
                familyMotto = motto,
                ownerUserId = "usr_founder_fake",
                rootPersonId = rootPersonId,
                familyVersion = 1L,
                familyCreatedAt = now,
                familyUpdatedAt = now,
                memberId = UUID.randomUUID().toString(),
                linkedPersonId = rootPersonId,
                role = FamilyRole.OWNER,
                branchRootPersonId = rootPersonId,
                canInvite = true,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
                joinedAt = now,
                memberUpdatedAt = now,
                memberVersion = 1L,
            )
            createdFamilies.add(membership)
            return Result.success(membership)
        }

        override suspend fun fetchMyFamilyMemberships(accessToken: String): Result<List<RemoteFamilyMembership>> =
            Result.success(createdFamilies.toList())

        override suspend fun createFamilyInvitation(
            accessToken: String,
            familyId: String,
            targetPersonId: String?,
            assignedRole: FamilyRole,
            branchRootPersonId: String?,
            inviteCodeHash: String,
            inviteCodeHint: String,
            maxUses: Int,
            expiresAt: Long,
        ): Result<RemoteInvitation> = Result.failure(UnsupportedOperationException())

        override suspend fun revokeFamilyInvitation(accessToken: String, invitationId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun fetchFamilyInvitations(accessToken: String, familyId: String): Result<List<RemoteInvitation>> =
            Result.success(emptyList())

        override suspend fun acceptFamilyInvitation(accessToken: String, inviteCode: String): Result<RemoteFamilyMembership> =
            Result.failure(UnsupportedOperationException())

        override suspend fun fetchFamilyMembersWithProfiles(accessToken: String, familyId: String): Result<List<RemoteMemberProfile>> =
            Result.success(emptyList())

        override suspend fun updateFamilyMemberLinkAndRole(
            accessToken: String,
            familyId: String,
            memberId: String,
            linkedPersonId: String?,
            branchRootPersonId: String?,
            role: FamilyRole,
            canInvite: Boolean,
            canExport: Boolean,
            canManageMedia: Boolean,
            status: MembershipStatus,
        ): Result<RemoteMemberProfile> = Result.failure(UnsupportedOperationException())

        override suspend fun pushGenealogyMutation(
            accessToken: String,
            familyId: String,
            entityType: String,
            entityId: String,
            operation: String,
            clientVersion: Long,
            forceOverwrite: Boolean,
            payloadJson: String,
        ): Result<RemotePushOutcome> = Result.success(
            RemotePushOutcome.Applied(clientVersion, System.currentTimeMillis())
        )

        override suspend fun pullFamilyDelta(
            accessToken: String,
            familyId: String,
            sinceUpdatedAt: Long,
        ): Result<RemoteSyncDelta> = Result.success(
            RemoteSyncDelta(System.currentTimeMillis(), "{}")
        )

        override suspend fun uploadFamilyMedia(
            accessToken: String,
            objectKey: String,
            bytes: ByteArray,
            mimeType: String,
        ): Result<String> = Result.success(objectKey)

        override suspend fun downloadFamilyMedia(
            accessToken: String,
            objectKey: String,
        ): Result<ByteArray> = Result.failure(UnsupportedOperationException())
    }
}
