package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.InvitationStatus
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncState
import com.example.domain.security.InviteCodeHelper
import com.example.presentation.JuthoorViewModel
import com.example.presentation.navigation.JuthoorAppRoot
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseNineFiveSyncEngineTest {

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
    fun `cloud workspace auto-tags offline mutations and coalesces sync outbox entries`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
        val authStore = AuthSessionStore(context)
        authStore.clearSession()
        authStore.updateActiveFamilyId(null)

        val prefs = PreferencesRepository(context)
        val server = StatefulSyncSupabaseGateway()
        val cloudRepo = RoomAndSupabaseAuthRepository(db, authStore, prefs, server)
        val genealogyRepo = RoomGenealogyRepository(db, authStore)

        cloudRepo.signUp("founder@juthoor.test", "secret123", "عبدالرحيم").getOrThrow()
        val space = cloudRepo.createCloudFamilySpace(
            name = "عائلة السامعي",
            motto = "نوثّق الأصول",
            founderPersonId = null,
            adoptExistingLocalTree = false,
        ).getOrThrow()

        // Create person offline without explicitly setting familyId -> auto-tagged to active cloud family
        val son = genealogyRepo.savePerson(
            Person(
                id = UUID.randomUUID().toString(),
                fullName = "شهاب عبدالرحيم",
                firstName = "شهاب",
                gender = Gender.MALE,
            )
        ).getOrThrow()
        assertEquals(space.family.id, son.familyId)
        assertEquals(SyncState.PENDING_UPSERT, son.syncState)
        assertEquals(1, db.syncOutboxDao().getPendingForFamily(space.family.id).count { it.entityId == son.id })

        // Update same person again offline -> outbox must coalesce to 1 row
        val updatedSon = genealogyRepo.savePerson(son.copy(occupation = "مهندس برمجيات")).getOrThrow()
        val outboxAfterUpdate = db.syncOutboxDao().getPendingForFamily(space.family.id).filter { it.entityId == son.id }
        assertEquals(1, outboxAfterUpdate.size)
        assertEquals(2L, outboxAfterUpdate.first().baseVersion)
        assertEquals(SyncOutboxOperation.UPSERT, outboxAfterUpdate.first().operation)

        // Delete person -> soft-deleted locally & coalesced outbox entry turns into SOFT_DELETE
        genealogyRepo.deletePersonSafely(updatedSon.id).getOrThrow()
        val deletedEntity = db.personDao().getByIdIncludingDeleted(son.id)
        assertNotNull(deletedEntity)
        assertTrue(deletedEntity!!.isDeleted)
        assertEquals(SyncState.PENDING_DELETE, deletedEntity.syncState)
        val outboxAfterDelete = db.syncOutboxDao().getPendingForFamily(space.family.id).filter { it.entityId == son.id }
        assertEquals(1, outboxAfterDelete.size)
        assertEquals(SyncOutboxOperation.SOFT_DELETE, outboxAfterDelete.first().operation)
    }

    @Test
    fun `http supabase gateway executes sync push pull and media storage endpoints cleanly`() = runTest {
        val sampleImageBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                when {
                    path.endsWith("/rest/v1/rpc/sync_push_genealogy_mutation") -> {
                        val reqJson = JSONObject(okio.Buffer().also { req.body?.writeTo(it) }.readUtf8())
                        val body = if (reqJson.getLong("p_client_version") == 1L) {
                            JSONObject()
                                .put("status", "APPLIED")
                                .put("version", 2L)
                                .put("updated_at", 1_760_000_005_000L)
                                .toString()
                        } else {
                            JSONObject()
                                .put("status", "CONFLICT")
                                .put("server_version", 9L)
                                .put("server_record", JSONObject().put("id", reqJson.getString("p_entity_id")).put("full_name", "نسخة الخادم"))
                                .toString()
                        }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }

                    path.endsWith("/rest/v1/rpc/sync_pull_family_delta") -> {
                        val deltaBody = JSONObject()
                            .put("server_time", 1_760_000_010_000L)
                            .put("persons", JSONArray().put(JSONObject().put("id", "p1").put("full_name", "عبدالرحيم")))
                            .toString()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(deltaBody.toResponseBody("application/json".toMediaType()))
                            .build()
                    }

                    path.contains("/storage/v1/object/family-media/") && req.method == "POST" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"Key":"family-media/test.jpg"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }

                    path.contains("/storage/v1/object/authenticated/family-media/") && req.method == "GET" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(sampleImageBytes.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    }

                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            })
            .build()

        val gateway = HttpSupabaseGateway(
            config = SupabaseConfig(url = "https://demo.supabase.co", anonKey = "real_key_123"),
            client = client,
        )

        val applied = gateway.pushGenealogyMutation(
            accessToken = "jwt",
            familyId = "fam_1",
            entityType = "PERSON",
            entityId = "p1",
            operation = "UPSERT",
            clientVersion = 1L,
            forceOverwrite = false,
            payloadJson = """{"full_name":"عبدالرحيم"}""",
        ).getOrThrow()
        assertTrue(applied is RemotePushOutcome.Applied)
        assertEquals(2L, (applied as RemotePushOutcome.Applied).version)

        val conflict = gateway.pushGenealogyMutation(
            accessToken = "jwt",
            familyId = "fam_1",
            entityType = "PERSON",
            entityId = "p1",
            operation = "UPSERT",
            clientVersion = 2L,
            forceOverwrite = false,
            payloadJson = """{"full_name":"عبدالرحيم"}""",
        ).getOrThrow()
        assertTrue(conflict is RemotePushOutcome.Conflict)
        assertEquals(9L, (conflict as RemotePushOutcome.Conflict).serverVersion)

        val delta = gateway.pullFamilyDelta("jwt", "fam_1", 0L).getOrThrow()
        assertEquals(1_760_000_010_000L, delta.serverTimeMillis)
        assertTrue(delta.rawJson.contains("عبدالرحيم"))

        val key = gateway.uploadFamilyMedia("jwt", "fam_1/photo_1.jpg", sampleImageBytes, "image/jpeg").getOrThrow()
        assertEquals("fam_1/photo_1.jpg", key)

        val downloaded = gateway.downloadFamilyMedia("jwt", key).getOrThrow()
        assertArrayEquals(sampleImageBytes, downloaded)
    }

    @Test
    fun `two device collaboration syncs founder tree to son pushes son wife child and photo back to founder`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val mediaStorage = LocalMediaStorage(context)
            mediaStorage.clearAllManagedMedia()

            val founderDb = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openDatabases.add(it) }
            val sonDb = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openDatabases.add(it) }

            val authStore = AuthSessionStore(context)
            authStore.clearSession()
            authStore.updateActiveFamilyId(null)
            val prefs = PreferencesRepository(context)
            val server = StatefulSyncSupabaseGateway()

            // --- Device 1 (Founder offline tree + avatar photo) ---
            val founderGenealogy = RoomGenealogyRepository(founderDb, authStore)
            val founderCloud = RoomAndSupabaseAuthRepository(founderDb, authStore, prefs, server, mediaStorage)

            val avatarBytes = "AVATAR_BYTES_FOUNDER_12345".toByteArray()
            val avatarPath = mediaStorage.saveBytesForRestore("founder_avatar_src.jpg", avatarBytes)
            val father = founderGenealogy.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "عبدالرحيم عثمان سيف",
                    firstName = "عبدالرحيم",
                    gender = Gender.MALE,
                    profileImagePath = avatarPath,
                )
            ).getOrThrow()
            val son = founderGenealogy.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "شهاب عبدالرحيم عثمان",
                    firstName = "شهاب",
                    gender = Gender.MALE,
                )
            ).getOrThrow()
            founderGenealogy.addParentChildLink(father.id, son.id, ParentRelationshipType.BIOLOGICAL_FATHER).getOrThrow()

            founderCloud.signUp("founder@juthoor.test", "secret123", "عبدالرحيم عثمان").getOrThrow()
            val founderSpace = founderCloud.createCloudFamilySpace(
                name = "عائلة السامعي",
                motto = "نوثّق الأصول",
                founderPersonId = father.id,
                adoptExistingLocalTree = true,
            ).getOrThrow()
            val familyId = founderSpace.family.id

            val invite = founderCloud.createInvitation(
                familyId = familyId,
                targetPersonId = son.id,
                assignedRole = FamilyRole.BRANCH_MANAGER,
                branchRootPersonId = son.id,
            ).getOrThrow()

            val pushReport = founderCloud.syncNow(familyId).getOrThrow()
            assertTrue(pushReport.pushedCount >= 4) // 2 persons + 1 link + 1 family update
            assertEquals(1, pushReport.uploadedMediaCount)
            assertEquals(0, pushReport.conflictCount)

            // --- Device 2 (Son signs up, accepts invitation, pulls tree & avatar, then adds Wife + Child + Album Photo) ---
            founderCloud.signOut().getOrThrow()
            mediaStorage.clearAllManagedMedia() // Simulate fresh second device disk

            val sonGenealogy = RoomGenealogyRepository(sonDb, authStore)
            val sonCloud = RoomAndSupabaseAuthRepository(sonDb, authStore, prefs, server, mediaStorage)
            val sonSession = sonCloud.signUp("shehab@juthoor.test", "secret123", "شهاب عبدالرحيم").getOrThrow()
                .let { (it as AuthActionResult.Authenticated).session }
            sonCloud.acceptInvitation(invite.plainInviteCode).getOrThrow()

            val sonPullReport = sonCloud.syncNow(familyId).getOrThrow()
            assertTrue(sonPullReport.pulledCount >= 2)
            assertEquals(1, sonPullReport.downloadedMediaCount)

            val sonLocalPersons = sonGenealogy.getAllPersonsOnce()
            assertEquals(2, sonLocalPersons.size)
            val pulledFather = sonLocalPersons.first { it.id == father.id }
            assertNotNull(pulledFather.profileImagePath)
            assertArrayEquals(avatarBytes, File(pulledFather.profileImagePath!!).readBytes())
            assertEquals(son.id, sonDb.familyMemberDao().getMembership(familyId, sonSession.userId)?.linkedPersonId)

            // Son works OFFLINE on his branch: adds wife, marriage, son's child, and family photo
            val wife = sonGenealogy.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "أمل محمد",
                    firstName = "أمل",
                    gender = Gender.FEMALE,
                )
            ).getOrThrow()
            val marriage = sonGenealogy.saveMarriage(
                Marriage(
                    id = UUID.randomUUID().toString(),
                    partnerOneId = son.id,
                    partnerTwoId = wife.id,
                    marriageStatus = MarriageRecordStatus.ACTIVE,
                )
            ).getOrThrow()
            val grandson = sonGenealogy.savePerson(
                Person(
                    id = UUID.randomUUID().toString(),
                    fullName = "يوسف شهاب عبدالرحيم",
                    firstName = "يوسف",
                    gender = Gender.MALE,
                )
            ).getOrThrow()
            sonGenealogy.addParentChildLink(son.id, grandson.id, ParentRelationshipType.BIOLOGICAL_FATHER, marriage.id).getOrThrow()
            sonGenealogy.addParentChildLink(wife.id, grandson.id, ParentRelationshipType.BIOLOGICAL_MOTHER, marriage.id).getOrThrow()

            val familyPhotoBytes = "SON_FAMILY_PHOTO_999".toByteArray()
            val familyPhotoPath = mediaStorage.saveBytesForRestore("son_album_photo.jpg", familyPhotoBytes)
            sonGenealogy.savePhoto(
                PhotoRecord(
                    id = UUID.randomUUID().toString(),
                    personId = grandson.id,
                    taggedPersonIds = listOf(son.id, wife.id, grandson.id),
                    filePath = familyPhotoPath,
                    description = "صورة تذكارية",
                    mediaType = MediaType.GROUP_PHOTO,
                )
            ).getOrThrow()

            // Son comes online and syncs his additions
            val sonPushReport = sonCloud.syncNow(familyId).getOrThrow()
            assertEquals(0, sonPushReport.conflictCount)
            assertTrue(sonPushReport.pushedCount >= 6) // wife + grandson + marriage + 2 links + photo
            assertEquals(1, sonPushReport.uploadedMediaCount)

            // --- Back on Device 1 (Founder signs in & pulls Son's Wife + Grandson + Links + Photo) ---
            sonCloud.signOut().getOrThrow()
            mediaStorage.clearAllManagedMedia()

            founderCloud.signIn("founder@juthoor.test", "secret123").getOrThrow()
            val founderPullReport = founderCloud.syncNow(familyId).getOrThrow()
            assertTrue(founderPullReport.pulledCount >= 6)
            assertTrue(founderPullReport.downloadedMediaCount >= 1)

            val mergedPersons = founderGenealogy.getAllPersonsOnce()
            assertEquals(4, mergedPersons.size)
            assertTrue(mergedPersons.any { it.id == wife.id && it.fullName == "أمل محمد" })
            assertTrue(mergedPersons.any { it.id == grandson.id && it.fullName == "يوسف شهاب عبدالرحيم" })

            val mergedMarriages = founderGenealogy.getAllMarriagesOnce()
            assertEquals(1, mergedMarriages.size)
            assertEquals(marriage.id, mergedMarriages.first().id)

            val mergedLinks = founderGenealogy.getAllParentChildLinksOnce()
            assertEquals(3, mergedLinks.size)

            val mergedPhotos = founderDb.photoDao().getAllForFamilyOnce(familyId)
            assertEquals(1, mergedPhotos.size)
            assertTrue(mergedPhotos.first().filePath.isNotBlank())
            assertArrayEquals(familyPhotoBytes, File(mergedPhotos.first().filePath).readBytes())
        }

    @Test
    fun `conflict detection surfaces in family account screen and user resolution clears conflict`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openDatabases.add(it) }
            val authStore = AuthSessionStore(context)
            authStore.clearSession()
            authStore.updateActiveFamilyId(null)
            val prefs = PreferencesRepository(context)
            prefs.markOnboardingCompleted()

            val server = StatefulSyncSupabaseGateway()
            val genealogyRepo = RoomGenealogyRepository(db, authStore)
            val mediaStorage = LocalMediaStorage(context)
            val cloudRepo = RoomAndSupabaseAuthRepository(db, authStore, prefs, server, mediaStorage)

            cloudRepo.signUp("founder@juthoor.test", "secret123", "عبدالرحيم عثمان").getOrThrow()
            val father = genealogyRepo.savePerson(
                Person(
                    id = "p_conflict_father",
                    fullName = "عبدالرحيم عثمان",
                    firstName = "عبدالرحيم",
                    gender = Gender.MALE,
                )
            ).getOrThrow()
            val space = cloudRepo.createCloudFamilySpace(
                name = "أسرة السامعي",
                motto = "نوثّق الأصول",
                founderPersonId = father.id,
                adoptExistingLocalTree = true,
            ).getOrThrow()
            cloudRepo.syncNow(space.family.id).getOrThrow()

            // Simulate concurrent server edit from another user (bumps version on server)
            server.simulateRemotePersonUpdate(
                familyId = space.family.id,
                personId = father.id,
                newFullName = "عبدالرحيم عثمان (تعديل السحابة)",
                byUserId = "other_admin_user",
            )

            // Local edit bumps local version on stale base -> will trigger CONFLICT on sync
            val localLatest = genealogyRepo.getPersonById(father.id)!!
            genealogyRepo.savePerson(localLatest.copy(fullName = "عبدالرحيم عثمان (تعديلي المحلي)")).getOrThrow()

            val viewModel = JuthoorViewModel(
                repository = genealogyRepo,
                preferencesRepository = prefs,
                mediaStorage = mediaStorage,
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

            // Navigate: Settings -> Family Account -> click Sync Now
            composeTestRule.onNodeWithTag("topbar_settings").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("open_family_account_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("family_account_list")
                .performScrollToNode(hasTestTag("cloud_sync_card"))
            composeTestRule.onNodeWithTag("sync_now_button")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                !viewModel.uiState.value.isSyncing && viewModel.uiState.value.unresolvedSyncConflicts.isNotEmpty()
            }
            composeTestRule.waitForIdle()
            viewModel.clearMessages()
            composeTestRule.waitForIdle()

            val conflict = viewModel.uiState.value.unresolvedSyncConflicts.first()
            composeTestRule.onNodeWithTag("family_account_list")
                .performScrollToNode(hasTestTag("sync_conflicts_card"))
            composeTestRule.onNodeWithTag("sync_conflicts_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("resolve_conflict_keep_local_${conflict.id}")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                val err = viewModel.uiState.value.errorMessage
                if (err != null) {
                    throw AssertionError("Unexpected ViewModel error: $err")
                }
                !viewModel.uiState.value.isSyncing && viewModel.uiState.value.unresolvedSyncConflicts.isEmpty()
            }
            composeTestRule.waitForIdle()

            // Local edit forced onto server and local conflict cleared
            assertTrue(db.syncConflictDao().getById(conflict.id)!!.resolved)
            val resolvedPerson = genealogyRepo.getPersonById(father.id)!!
            assertEquals("عبدالرحيم عثمان (تعديلي المحلي)", resolvedPerson.fullName)
            assertEquals(SyncState.SYNCED, resolvedPerson.syncState)
        }

    private class StatefulSyncSupabaseGateway : SupabaseGateway {
        override val isConfigured: Boolean = true
        private val usersByEmail = mutableMapOf<String, Triple<String, String, String>>()
        private val usersByToken = mutableMapOf<String, RemoteSession>()
        private val families = mutableMapOf<String, RemoteFamilyMembership>()
        private val familyMetaJson = mutableMapOf<String, JSONObject>()
        private val members = mutableListOf<RemoteMemberProfile>()
        private val invitations = mutableListOf<RemoteInvitation>()
        private val records = mutableMapOf<Pair<String, String>, JSONObject>() // (familyId, entityType:entityId) -> row JSON
        private val mediaStore = mutableMapOf<String, ByteArray>()

        fun simulateRemotePersonUpdate(
            familyId: String,
            personId: String,
            newFullName: String,
            byUserId: String,
        ) {
            val key = familyId to "PERSON:$personId"
            val existing = records[key] ?: JSONObject().put("id", personId).put("family_id", familyId)
            val nextVer = existing.optLong("version", 1L) + 2L
            existing.put("full_name", newFullName)
            existing.put("first_name", newFullName.substringBefore(" "))
            existing.put("updated_by_user_id", byUserId)
            existing.put("version", nextVer)
            existing.put("updated_at", System.currentTimeMillis())
            records[key] = existing
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
            val caller = usersByToken[accessToken] ?: return Result.failure(IllegalStateException("Auth required"))
            val now = System.currentTimeMillis()
            val memberId = UUID.randomUUID().toString()
            val fam = RemoteFamilyMembership(
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
            families[familyId] = fam
            familyMetaJson[familyId] = JSONObject().apply {
                put("id", familyId)
                put("name", name)
                put("motto", motto)
                put("owner_user_id", caller.userId)
                put("root_person_id", rootPersonId ?: JSONObject.NULL)
                put("version", 1L)
                put("is_deleted", false)
                put("created_at", now)
                put("updated_at", now)
            }
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
            return Result.success(fam)
        }

        override suspend fun fetchMyFamilyMemberships(accessToken: String): Result<List<RemoteFamilyMembership>> {
            val caller = usersByToken[accessToken] ?: return Result.success(emptyList())
            return Result.success(
                members.filter { it.userId == caller.userId && it.status == MembershipStatus.ACTIVE }.mapNotNull { m ->
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
                    )
                }
            )
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
            val caller = usersByToken[accessToken]!!
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

        override suspend fun revokeFamilyInvitation(accessToken: String, invitationId: String): Result<Unit> = Result.success(Unit)

        override suspend fun fetchFamilyInvitations(accessToken: String, familyId: String): Result<List<RemoteInvitation>> =
            Result.success(invitations.filter { it.familyId == familyId })

        override suspend fun acceptFamilyInvitation(accessToken: String, inviteCode: String): Result<RemoteFamilyMembership> {
            val caller = usersByToken[accessToken]!!
            val hash = InviteCodeHelper.sha256Hex(InviteCodeHelper.normalizeCode(inviteCode))
            val idx = invitations.indexOfFirst { it.inviteCodeHash == hash }
            val inv = invitations[idx]
            val now = System.currentTimeMillis()
            val member = RemoteMemberProfile(
                memberId = UUID.randomUUID().toString(),
                familyId = inv.familyId,
                userId = caller.userId,
                email = caller.email,
                displayName = caller.displayName,
                linkedPersonId = inv.targetPersonId,
                role = inv.assignedRole,
                branchRootPersonId = inv.branchRootPersonId ?: inv.targetPersonId,
                canInvite = false,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
                invitedByUserId = inv.createdByUserId,
                joinedAt = now,
                updatedAt = now,
                version = 1L,
            )
            members.add(member)
            invitations[idx] = inv.copy(usesCount = inv.usesCount + 1, status = InvitationStatus.ACCEPTED, acceptedByUserId = caller.userId)
            val fam = families.getValue(inv.familyId)
            return Result.success(
                fam.copy(
                    memberId = member.memberId,
                    linkedPersonId = member.linkedPersonId,
                    role = member.role,
                    branchRootPersonId = member.branchRootPersonId,
                    canManageMedia = member.canManageMedia,
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
        ): Result<RemotePushOutcome> {
            val caller = usersByToken[accessToken] ?: return Result.failure(IllegalStateException("Auth required"))
            val now = System.currentTimeMillis()
            val payload = JSONObject(payloadJson)

            if (entityType == "FAMILY") {
                val famObj = familyMetaJson[familyId] ?: JSONObject().put("id", familyId)
                famObj.put("root_person_id", payload.opt("root_person_id"))
                famObj.put("updated_at", now)
                val nextVer = famObj.optLong("version", 1L) + 1L
                famObj.put("version", nextVer)
                familyMetaJson[familyId] = famObj
                return Result.success(RemotePushOutcome.Applied(nextVer, now))
            }

            val key = familyId to "$entityType:$entityId"
            val existing = records[key]
            if (existing != null && !forceOverwrite) {
                val serverVer = existing.optLong("version", 1L)
                val serverUpdatedBy = existing.optString("updated_by_user_id", "")
                if (serverVer > clientVersion || (serverVer == clientVersion && serverUpdatedBy != caller.userId)) {
                    return Result.success(
                        RemotePushOutcome.Conflict(
                            serverVersion = serverVer,
                            serverRecordJson = existing.toString(),
                        )
                    )
                }
            }

            val nextVersion = if (existing == null) {
                maxOf(1L, clientVersion)
            } else {
                maxOf(existing.optLong("version", 1L) + 1L, clientVersion)
            }
            val merged = JSONObject(payload.toString()).apply {
                put("id", entityId)
                put("family_id", familyId)
                put("updated_by_user_id", caller.userId)
                put("created_by_user_id", existing?.optString("created_by_user_id") ?: caller.userId)
                put("version", nextVersion)
                put("is_deleted", operation == "SOFT_DELETE")
                put("updated_at", now)
            }
            records[key] = merged
            return Result.success(RemotePushOutcome.Applied(nextVersion, now))
        }

        override suspend fun pullFamilyDelta(
            accessToken: String,
            familyId: String,
            sinceUpdatedAt: Long,
        ): Result<RemoteSyncDelta> {
            val now = System.currentTimeMillis()
            fun collect(type: String): JSONArray {
                val arr = JSONArray()
                records.entries
                    .filter { (k, v) -> k.first == familyId && k.second.startsWith("$type:") && v.optLong("updated_at", 0L) > sinceUpdatedAt }
                    .forEach { arr.put(it.value) }
                return arr
            }

            val delta = JSONObject().apply {
                put("server_time", now)
                familyMetaJson[familyId]?.takeIf { it.optLong("updated_at", 0L) > sinceUpdatedAt }?.let {
                    put("family", it)
                }
                put("family_groups", collect("FAMILY_GROUP"))
                put("persons", collect("PERSON"))
                put("marriages", collect("MARRIAGE"))
                put("parent_child_relationships", collect("PARENT_CHILD"))
                put("education", collect("EDUCATION"))
                put("person_photos", collect("PHOTO"))
                put("photo_person_tags", JSONArray())
                put("events", collect("EVENT"))
                val memArr = JSONArray()
                members.filter { it.familyId == familyId && it.updatedAt > sinceUpdatedAt }.forEach { m ->
                    memArr.put(
                        JSONObject().apply {
                            put("member_id", m.memberId)
                            put("family_id", m.familyId)
                            put("user_id", m.userId)
                            put("email", m.email)
                            put("display_name", m.displayName)
                            put("linked_person_id", m.linkedPersonId ?: JSONObject.NULL)
                            put("role", m.role.name)
                            put("branch_root_person_id", m.branchRootPersonId ?: JSONObject.NULL)
                            put("can_invite", m.canInvite)
                            put("can_export", m.canExport)
                            put("can_manage_media", m.canManageMedia)
                            put("status", m.status.name)
                            put("joined_at", m.joinedAt)
                            put("updated_at", m.updatedAt)
                            put("version", m.version)
                        }
                    )
                }
                put("family_members", memArr)
            }
            return Result.success(RemoteSyncDelta(now, delta.toString()))
        }

        override suspend fun uploadFamilyMedia(
            accessToken: String,
            objectKey: String,
            bytes: ByteArray,
            mimeType: String,
        ): Result<String> {
            mediaStore[objectKey] = bytes
            return Result.success(objectKey)
        }

        override suspend fun downloadFamilyMedia(
            accessToken: String,
            objectKey: String,
        ): Result<ByteArray> {
            val bytes = mediaStore[objectKey]
                ?: return Result.failure(IllegalStateException("Media not found: $objectKey"))
            return Result.success(bytes)
        }
    }
}
