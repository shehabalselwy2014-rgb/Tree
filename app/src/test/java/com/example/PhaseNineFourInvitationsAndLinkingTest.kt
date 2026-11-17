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
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MembershipStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.security.InviteCodeHelper
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
class PhaseNineFourInvitationsAndLinkingTest {

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
    fun `invite code helper generates random normalized codes consistent sha256 and masked hint`() {
        val code1 = InviteCodeHelper.generateCode()
        val code2 = InviteCodeHelper.generateCode()

        assertTrue(code1.startsWith("JTH-"))
        assertEquals(13, code1.length)
        assertNotEquals(code1, code2)

        val compactLower = code1.lowercase().replace("-", " ")
        assertEquals(code1, InviteCodeHelper.normalizeCode(compactLower))

        val hash1 = InviteCodeHelper.sha256Hex(code1)
        val hash2 = InviteCodeHelper.sha256Hex(InviteCodeHelper.normalizeCode(compactLower))
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)

        val hint = InviteCodeHelper.buildHint(code1)
        assertTrue(hint.startsWith("JTH-••••-"))
        assertTrue(hint.endsWith(code1.takeLast(4)))
    }

    @Test
    fun `http supabase gateway executes phase 9_4 invitation and member rpcs and translates errors`() =
        runTest {
            val famId = UUID.randomUUID().toString()
            val invId = UUID.randomUUID().toString()
            val memberId = UUID.randomUUID().toString()
            val sonPersonId = UUID.randomUUID().toString()
            val now = 1_760_000_000_000L

            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val req = chain.request()
                    val path = req.url.encodedPath
                    val (code, json) = when {
                        path.endsWith("/rest/v1/rpc/create_family_invitation") -> {
                            200 to JSONObject()
                                .put("id", invId)
                                .put("family_id", famId)
                                .put("target_person_id", sonPersonId)
                                .put("assigned_role", "BRANCH_MANAGER")
                                .put("branch_root_person_id", sonPersonId)
                                .put("invite_code_hash", "a".repeat(64))
                                .put("invite_code_hint", "JTH-••••-9K2M")
                                .put("max_uses", 1)
                                .put("uses_count", 0)
                                .put("expires_at", now + 86_400_000L)
                                .put("status", "PENDING")
                                .put("created_by_user_id", "usr_owner")
                                .put("created_at", now)
                                .put("updated_at", now)
                                .toString()
                        }

                        path.endsWith("/rest/v1/rpc/accept_family_invitation") -> {
                            val body = okio.Buffer().also { req.body?.writeTo(it) }.readUtf8()
                            val inviteCode = JSONObject(body).getString("p_invite_code")
                            if (inviteCode == "BAD-CODE") {
                                400 to JSONObject().put("message", "Invalid invitation code").toString()
                            } else {
                                200 to JSONObject()
                                    .put("family_id", famId)
                                    .put("family_name", "أسرة السامعي")
                                    .put("family_motto", "نوثّق الأصول")
                                    .put("owner_user_id", "usr_owner")
                                    .put("member_id", memberId)
                                    .put("linked_person_id", sonPersonId)
                                    .put("role", "BRANCH_MANAGER")
                                    .put("branch_root_person_id", sonPersonId)
                                    .put("can_invite", false)
                                    .put("can_export", true)
                                    .put("can_manage_media", true)
                                    .put("status", "ACTIVE")
                                    .put("joined_at", now)
                                    .toString()
                            }
                        }

                        path.endsWith("/rest/v1/rpc/get_family_members_with_profiles") -> {
                            200 to JSONArray().put(
                                JSONObject()
                                    .put("member_id", memberId)
                                    .put("family_id", famId)
                                    .put("user_id", "usr_son")
                                    .put("email", "son@juthoor.test")
                                    .put("display_name", "شهاب عبدالرحيم")
                                    .put("linked_person_id", sonPersonId)
                                    .put("role", "BRANCH_MANAGER")
                                    .put("branch_root_person_id", sonPersonId)
                                    .put("can_invite", false)
                                    .put("can_export", true)
                                    .put("can_manage_media", true)
                                    .put("status", "ACTIVE")
                                    .put("joined_at", now)
                                    .put("updated_at", now)
                                    .put("version", 1L)
                            ).toString()
                        }

                        path.endsWith("/rest/v1/rpc/update_family_member_link_and_role") -> {
                            200 to JSONObject()
                                .put("member_id", memberId)
                                .put("family_id", famId)
                                .put("user_id", "usr_son")
                                .put("email", "son@juthoor.test")
                                .put("display_name", "شهاب عبدالرحيم")
                                .put("linked_person_id", sonPersonId)
                                .put("role", "ADMIN")
                                .put("branch_root_person_id", sonPersonId)
                                .put("can_invite", true)
                                .put("can_export", true)
                                .put("can_manage_media", true)
                                .put("status", "ACTIVE")
                                .put("joined_at", now)
                                .put("updated_at", now + 5000L)
                                .put("version", 2L)
                                .toString()
                        }

                        else -> 404 to "{}"
                    }

                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build()

            val gateway = HttpSupabaseGateway(
                config = SupabaseConfig(url = "https://abc.supabase.co", anonKey = "real_anon_key_123456"),
                client = client,
            )

            val created = gateway.createFamilyInvitation(
                accessToken = "jwt",
                familyId = famId,
                targetPersonId = sonPersonId,
                assignedRole = FamilyRole.BRANCH_MANAGER,
                branchRootPersonId = sonPersonId,
                inviteCodeHash = "a".repeat(64),
                inviteCodeHint = "JTH-••••-9K2M",
                maxUses = 1,
                expiresAt = now + 86_400_000L,
            ).getOrThrow()
            assertEquals(invId, created.id)
            assertEquals(FamilyRole.BRANCH_MANAGER, created.assignedRole)

            val badAccept = gateway.acceptFamilyInvitation("jwt", "BAD-CODE")
            assertTrue(badAccept.isFailure)
            assertTrue(badAccept.exceptionOrNull()?.message?.contains("رمز الدعوة غير صحيح") == true)

            val accepted = gateway.acceptFamilyInvitation("jwt", "JTH-ABCD-9K2M").getOrThrow()
            assertEquals(famId, accepted.familyId)
            assertEquals(sonPersonId, accepted.linkedPersonId)

            val members = gateway.fetchFamilyMembersWithProfiles("jwt", famId).getOrThrow()
            assertEquals(1, members.size)
            assertEquals("شهاب عبدالرحيم", members.first().displayName)

            val updated = gateway.updateFamilyMemberLinkAndRole(
                accessToken = "jwt",
                familyId = famId,
                memberId = memberId,
                linkedPersonId = sonPersonId,
                branchRootPersonId = sonPersonId,
                role = FamilyRole.ADMIN,
                canInvite = true,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
            ).getOrThrow()
            assertEquals(FamilyRole.ADMIN, updated.role)
            assertTrue(updated.canInvite)
        }

    @Test
    fun `founder invites son who accepts code links to person preserves data and blocks duplicate person claim`() =
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

            val father = Person(
                id = UUID.randomUUID().toString(),
                fullName = "عبدالرحيم عثمان سيف",
                firstName = "عبدالرحيم",
                gender = Gender.MALE,
            )
            val son = Person(
                id = UUID.randomUUID().toString(),
                fullName = "شهاب عبدالرحيم عثمان سيف",
                firstName = "شهاب",
                gender = Gender.MALE,
            )
            val son2 = Person(
                id = UUID.randomUUID().toString(),
                fullName = "صقر عبدالرحيم عثمان سيف",
                firstName = "صقر",
                gender = Gender.MALE,
            )
            genealogyRepo.savePerson(father).getOrThrow()
            genealogyRepo.savePerson(son).getOrThrow()
            genealogyRepo.savePerson(son2).getOrThrow()
            genealogyRepo.addParentChildLink(father.id, son.id, ParentRelationshipType.BIOLOGICAL_FATHER).getOrThrow()

            val fakeServer = StatefulFakeSupabaseGateway()
            val cloudRepo = RoomAndSupabaseAuthRepository(db, authStore, prefs, fakeServer)

            // 1. Founder creates account + cloud family space adopting existing tree
            cloudRepo.signUp("founder@juthoor.test", "secret123", "عبدالرحيم عثمان").getOrThrow()
            val createdSpace = cloudRepo.createCloudFamilySpace(
                name = "أسرة السامعي",
                motto = "نوثّق الأصول",
                founderPersonId = father.id,
                adoptExistingLocalTree = true,
            ).getOrThrow()
            val familyId = createdSpace.family.id

            // 2. Founder generates invite for Son 1 (linked to son.id as BRANCH_MANAGER)
            val inviteForSon = cloudRepo.createInvitation(
                familyId = familyId,
                targetPersonId = son.id,
                assignedRole = FamilyRole.BRANCH_MANAGER,
                branchRootPersonId = son.id,
                maxUses = 1,
                validHours = 72,
            ).getOrThrow()
            assertTrue(inviteForSon.plainInviteCode.startsWith("JTH-"))

            // 3. Son 1 signs up and accepts invitation (using lowercase & spaces to test normalization)
            cloudRepo.signOut().getOrThrow()
            cloudRepo.signUp("shehab@juthoor.test", "secret123", "شهاب عبدالرحيم").getOrThrow()
            val joinedSummary = cloudRepo.acceptInvitation(
                inviteForSon.plainInviteCode.lowercase().replace("-", " ")
            ).getOrThrow()

            assertEquals(familyId, joinedSummary.family.id)
            assertEquals(FamilyRole.BRANCH_MANAGER, joinedSummary.membership.role)
            assertEquals(son.id, joinedSummary.membership.linkedPersonId)
            assertEquals(son.id, joinedSummary.membership.branchRootPersonId)
            // Original genealogy records must remain 100% intact
            assertEquals(3, genealogyRepo.getAllPersonsOnce().size)

            // 4. Another user (Son 2) signs up and joins via open invitation, then Founder links Son 2
            cloudRepo.signOut().getOrThrow()
            cloudRepo.signIn("founder@juthoor.test", "secret123").getOrThrow()
            val openInvite = cloudRepo.createInvitation(
                familyId = familyId,
                targetPersonId = null,
                assignedRole = FamilyRole.MEMBER,
                branchRootPersonId = null,
                maxUses = 2,
                validHours = 48,
            ).getOrThrow()

            // Duplicate invite targeting already-linked son.id must be rejected!
            val duplicateTargetInvite = cloudRepo.createInvitation(
                familyId = familyId,
                targetPersonId = son.id,
                assignedRole = FamilyRole.BRANCH_MANAGER,
                branchRootPersonId = son.id,
            )
            assertTrue(duplicateTargetInvite.isFailure)

            cloudRepo.signOut().getOrThrow()
            cloudRepo.signUp("saqr@juthoor.test", "secret123", "صقر عبدالرحيم").getOrThrow()
            val son2Joined = cloudRepo.acceptInvitation(openInvite.plainInviteCode).getOrThrow()

            // Founder refreshes members and tries to link Son 2 to son.id -> MUST fail 1:1 uniqueness check
            cloudRepo.signOut().getOrThrow()
            cloudRepo.signIn("founder@juthoor.test", "secret123").getOrThrow()
            val members = cloudRepo.refreshFamilyMembers(familyId).getOrThrow()
            assertEquals(3, members.size)

            val conflictLinkResult = cloudRepo.updateMemberLinkAndPermissions(
                familyId = familyId,
                memberId = son2Joined.membership.id,
                linkedPersonId = son.id,
                branchRootPersonId = son.id,
                role = FamilyRole.BRANCH_MANAGER,
                canInvite = false,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
            )
            assertTrue(conflictLinkResult.isFailure)

            // Linking Son 2 to son2.id succeeds and promotes him to BRANCH_MANAGER
            val validLink = cloudRepo.updateMemberLinkAndPermissions(
                familyId = familyId,
                memberId = son2Joined.membership.id,
                linkedPersonId = son2.id,
                branchRootPersonId = son2.id,
                role = FamilyRole.BRANCH_MANAGER,
                canInvite = false,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
            ).getOrThrow()
            assertEquals(son2.id, validLink.member.linkedPersonId)
            assertEquals(FamilyRole.BRANCH_MANAGER, validLink.member.role)

            // Revoking remaining open invite works cleanly
            cloudRepo.revokeInvitation(openInvite.invitation.id).getOrThrow()
            val cachedInvites = db.familyInvitationDao().observeForFamily(familyId).first()
            assertEquals(
                InvitationStatus.REVOKED,
                cachedInvites.first { it.id == openInvite.invitation.id }.status,
            )
        }

    @Test
    fun `collaboration screens support creating invitations and linking member accounts to persons`() =
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
            prefs.markOnboardingCompleted()

            val father = Person(
                id = "p_father_ui",
                fullName = "عبدالرحيم عثمان سيف",
                firstName = "عبدالرحيم",
                gender = Gender.MALE,
            )
            val son = Person(
                id = "p_son_ui",
                fullName = "شهاب عبدالرحيم عثمان",
                firstName = "شهاب",
                gender = Gender.MALE,
            )
            genealogyRepo.savePerson(father).getOrThrow()
            genealogyRepo.savePerson(son).getOrThrow()

            val fakeServer = StatefulFakeSupabaseGateway()
            val cloudRepo = RoomAndSupabaseAuthRepository(db, authStore, prefs, fakeServer)
            cloudRepo.signUp("founder@juthoor.test", "secret123", "عبدالرحيم عثمان").getOrThrow()
            val space = cloudRepo.createCloudFamilySpace(
                name = "عائلة السامعي",
                motto = "نوثّق الجذور",
                founderPersonId = father.id,
                adoptExistingLocalTree = true,
            ).getOrThrow()

            // Simulate a second member who joined without a person link yet
            val guestUser = fakeServer.seedDirectMember(
                familyId = space.family.id,
                email = "brother@juthoor.test",
                displayName = "ماجد عبدالرحيم",
                role = FamilyRole.MEMBER,
            )

            val media = LocalMediaStorage(context)
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
                !viewModel.uiState.value.isLoading && viewModel.uiState.value.activeFamilySpace != null
            }
            composeTestRule.waitForIdle()

            // Navigate: Settings -> Family Account -> Members & Invitations
            composeTestRule.onNodeWithTag("topbar_settings").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("open_family_account_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("family_account_list")
                .performScrollToNode(hasTestTag("join_family_card"))
            composeTestRule.onNodeWithTag("join_family_card").assertExists()
            composeTestRule.onNodeWithTag("family_account_list")
                .performScrollToNode(hasTestTag("signed_in_account_card"))
            composeTestRule.onNodeWithTag("open_members_invitations_button")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                !viewModel.uiState.value.isAuthBusy && viewModel.uiState.value.activeFamilyMembers.size >= 2
            }
            composeTestRule.waitForIdle()

            // Create an invitation targeting Son (p_son_ui)
            composeTestRule.onNodeWithTag("collaboration_list")
                .performScrollToNode(hasTestTag("create_invitation_card"))
            composeTestRule.onNodeWithTag("invite_target_person_${son.id}").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("create_invitation_submit_button")
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
                !viewModel.uiState.value.isAuthBusy && viewModel.uiState.value.latestGeneratedInvite != null
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collaboration_list")
                .performScrollToNode(hasTestTag("generated_invite_code_card"))
            composeTestRule.onNodeWithTag("generated_invite_code_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("generated_invite_plain_code").assertIsDisplayed()

            // Switch to Members tab and link guestUser to son.id
            composeTestRule.onNodeWithTag("members_tab").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collaboration_list")
                .performScrollToNode(hasTestTag("collaboration_bottom_spacer"))
            composeTestRule.onNodeWithTag("edit_member_${guestUser.memberId}")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("member_edit_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("link_person_option_${son.id}").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("edit_member_role_${FamilyRole.BRANCH_MANAGER.name}").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("save_member_link_button").performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                val err = viewModel.uiState.value.errorMessage
                if (err != null) {
                    throw AssertionError("Unexpected ViewModel error: $err")
                }
                viewModel.uiState.value.activeFamilyMembers
                    .firstOrNull { it.member.id == guestUser.memberId }
                    ?.member
                    ?.linkedPersonId == son.id
            }
            composeTestRule.waitForIdle()

            assertTrue(composeTestRule.onAllNodesWithText("مربوط في الشجرة بـ: ${son.fullName}").fetchSemanticsNodes().isNotEmpty())
        }

    private class StatefulFakeSupabaseGateway : SupabaseGateway {
        override val isConfigured: Boolean = true
        private val usersByEmail = mutableMapOf<String, Triple<String, String, String>>() // email -> (uid, displayName, token)
        private val usersByToken = mutableMapOf<String, RemoteSession>()
        private val families = mutableMapOf<String, RemoteFamilyMembership>()
        private val members = mutableListOf<RemoteMemberProfile>()
        private val invitations = mutableListOf<RemoteInvitation>()

        fun seedDirectMember(
            familyId: String,
            email: String,
            displayName: String,
            role: FamilyRole,
        ): RemoteMemberProfile {
            val uid = UUID.randomUUID().toString()
            val member = RemoteMemberProfile(
                memberId = UUID.randomUUID().toString(),
                familyId = familyId,
                userId = uid,
                email = email,
                displayName = displayName,
                linkedPersonId = null,
                role = role,
                branchRootPersonId = null,
                canInvite = false,
                canExport = true,
                canManageMedia = false,
                status = MembershipStatus.ACTIVE,
                invitedByUserId = null,
                joinedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                version = 1L,
            )
            members.add(member)
            return member
        }

        override suspend fun signUp(email: String, password: String, displayName: String): Result<RemoteAuthResult> {
            val uid = UUID.randomUUID().toString()
            val token = "tok_$uid"
            val session = RemoteSession(
                userId = uid,
                email = email,
                displayName = displayName,
                accessToken = token,
                refreshToken = "ref_$uid",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
            )
            usersByEmail[email] = Triple(uid, displayName, token)
            usersByToken[token] = session
            return Result.success(RemoteAuthResult.Authenticated(session))
        }

        override suspend fun signInWithPassword(email: String, password: String): Result<RemoteSession> {
            val (_, _, token) = usersByEmail[email]
                ?: return Result.failure(IllegalStateException("البريد الإلكتروني أو كلمة المرور غير صحيحة."))
            return Result.success(usersByToken.getValue(token))
        }

        override suspend fun refreshToken(refreshToken: String): Result<RemoteSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut(accessToken: String): Result<Unit> = Result.success(Unit)

        override suspend fun upsertUserProfile(accessToken: String, userId: String, email: String, displayName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun createFamilySpace(
            accessToken: String,
            familyId: String,
            name: String,
            motto: String,
            rootPersonId: String?,
        ): Result<RemoteFamilyMembership> {
            val caller = usersByToken[accessToken]
                ?: return Result.failure(IllegalStateException("Session expired"))
            val now = System.currentTimeMillis()
            val memberId = UUID.randomUUID().toString()
            val membership = RemoteFamilyMembership(
                familyId = familyId,
                familyName = name,
                familyMotto = motto,
                ownerUserId = caller.userId,
                rootPersonId = rootPersonId,
                familyVersion = 1L,
                familyCreatedAt = now,
                familyUpdatedAt = now,
                memberId = memberId,
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
            families[familyId] = membership
            members.add(
                RemoteMemberProfile(
                    memberId = memberId,
                    familyId = familyId,
                    userId = caller.userId,
                    email = caller.email,
                    displayName = caller.displayName,
                    linkedPersonId = rootPersonId,
                    role = FamilyRole.OWNER,
                    branchRootPersonId = rootPersonId,
                    canInvite = true,
                    canExport = true,
                    canManageMedia = true,
                    status = MembershipStatus.ACTIVE,
                    invitedByUserId = caller.userId,
                    joinedAt = now,
                    updatedAt = now,
                    version = 1L,
                )
            )
            return Result.success(membership)
        }

        override suspend fun fetchMyFamilyMemberships(accessToken: String): Result<List<RemoteFamilyMembership>> {
            val caller = usersByToken[accessToken] ?: return Result.success(emptyList())
            val result = members.filter { it.userId == caller.userId && it.status == MembershipStatus.ACTIVE }.mapNotNull { m ->
                val fam = families[m.familyId] ?: return@mapNotNull null
                fam.copy(
                    memberId = m.memberId,
                    linkedPersonId = m.linkedPersonId,
                    role = m.role,
                    branchRootPersonId = m.branchRootPersonId,
                    canInvite = m.canInvite,
                    canExport = m.canExport,
                    canManageMedia = m.canManageMedia,
                    status = m.status,
                    joinedAt = m.joinedAt,
                    memberUpdatedAt = m.updatedAt,
                    memberVersion = m.version,
                )
            }
            return Result.success(result)
        }

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
        ): Result<RemoteInvitation> {
            val caller = usersByToken[accessToken] ?: return Result.failure(IllegalStateException("Auth required"))
            val now = System.currentTimeMillis()
            val inv = RemoteInvitation(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                targetPersonId = targetPersonId,
                assignedRole = assignedRole,
                branchRootPersonId = branchRootPersonId,
                inviteCodeHash = inviteCodeHash,
                inviteCodeHint = inviteCodeHint,
                maxUses = maxUses,
                usesCount = 0,
                expiresAt = expiresAt,
                status = InvitationStatus.PENDING,
                createdByUserId = caller.userId,
                acceptedByUserId = null,
                createdAt = now,
                updatedAt = now,
            )
            invitations.add(inv)
            return Result.success(inv)
        }

        override suspend fun revokeFamilyInvitation(accessToken: String, invitationId: String): Result<Unit> {
            val idx = invitations.indexOfFirst { it.id == invitationId }
            if (idx >= 0) {
                invitations[idx] = invitations[idx].copy(status = InvitationStatus.REVOKED, updatedAt = System.currentTimeMillis())
            }
            return Result.success(Unit)
        }

        override suspend fun fetchFamilyInvitations(accessToken: String, familyId: String): Result<List<RemoteInvitation>> =
            Result.success(invitations.filter { it.familyId == familyId })

        override suspend fun acceptFamilyInvitation(accessToken: String, inviteCode: String): Result<RemoteFamilyMembership> {
            val caller = usersByToken[accessToken] ?: return Result.failure(IllegalStateException("Auth required"))
            val codeHash = InviteCodeHelper.sha256Hex(InviteCodeHelper.normalizeCode(inviteCode))
            val idx = invitations.indexOfFirst { it.inviteCodeHash == codeHash }
            if (idx < 0) return Result.failure(IllegalStateException("رمز الدعوة غير صحيح أو غير موجود."))
            val inv = invitations[idx]
            if (inv.status != InvitationStatus.PENDING) {
                return Result.failure(IllegalStateException("هذه الدعوة لم تعد صالحة للاستخدام."))
            }
            val now = System.currentTimeMillis()
            val newMember = RemoteMemberProfile(
                memberId = UUID.randomUUID().toString(),
                familyId = inv.familyId,
                userId = caller.userId,
                email = caller.email,
                displayName = caller.displayName,
                linkedPersonId = inv.targetPersonId,
                role = inv.assignedRole,
                branchRootPersonId = inv.branchRootPersonId ?: inv.targetPersonId,
                canInvite = inv.assignedRole == FamilyRole.ADMIN,
                canExport = inv.assignedRole != FamilyRole.RESTRICTED_MEMBER,
                canManageMedia = inv.assignedRole in listOf(FamilyRole.ADMIN, FamilyRole.BRANCH_MANAGER),
                status = MembershipStatus.ACTIVE,
                invitedByUserId = inv.createdByUserId,
                joinedAt = now,
                updatedAt = now,
                version = 1L,
            )
            members.add(newMember)
            val nextUses = inv.usesCount + 1
            invitations[idx] = inv.copy(
                usesCount = nextUses,
                acceptedByUserId = caller.userId,
                status = if (nextUses >= inv.maxUses) InvitationStatus.ACCEPTED else InvitationStatus.PENDING,
                updatedAt = now,
            )
            val fam = families.getValue(inv.familyId)
            return Result.success(
                fam.copy(
                    memberId = newMember.memberId,
                    linkedPersonId = newMember.linkedPersonId,
                    role = newMember.role,
                    branchRootPersonId = newMember.branchRootPersonId,
                    canInvite = newMember.canInvite,
                    canExport = newMember.canExport,
                    canManageMedia = newMember.canManageMedia,
                    status = newMember.status,
                    joinedAt = newMember.joinedAt,
                    memberUpdatedAt = newMember.updatedAt,
                    memberVersion = 1L,
                )
            )
        }

        override suspend fun fetchFamilyMembersWithProfiles(accessToken: String, familyId: String): Result<List<RemoteMemberProfile>> =
            Result.success(members.filter { it.familyId == familyId })

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
        ): Result<RemoteMemberProfile> {
            val idx = members.indexOfFirst { it.idMatches(memberId, familyId) }
            if (idx < 0) return Result.failure(IllegalStateException("Member not found"))
            if (linkedPersonId != null && status == MembershipStatus.ACTIVE) {
                val claimed = members.any {
                    it.familyId == familyId &&
                        it.memberId != memberId &&
                        it.status == MembershipStatus.ACTIVE &&
                        it.linkedPersonId == linkedPersonId
                }
                if (claimed) {
                    return Result.failure(IllegalStateException("هذا الفرد في الشجرة مرتبط مسبقاً بحساب مستخدم نشط آخر."))
                }
            }
            val prev = members[idx]
            val updated = prev.copy(
                linkedPersonId = linkedPersonId,
                branchRootPersonId = branchRootPersonId ?: linkedPersonId,
                role = role,
                canInvite = canInvite,
                canExport = canExport,
                canManageMedia = canManageMedia,
                status = status,
                updatedAt = System.currentTimeMillis(),
                version = prev.version + 1L,
            )
            members[idx] = updated
            return Result.success(updated)
        }

        private fun RemoteMemberProfile.idMatches(mId: String, fId: String): Boolean =
            memberId == mId && familyId == fId

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
