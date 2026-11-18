package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.JuthoorDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.FamilyInvitationEntity
import com.example.data.local.entity.FamilyMemberEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.data.mapper.toDomain
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.AuthSessionStore
import com.example.data.preferences.PreferencesRepository
import com.example.data.remote.RemoteAuthResult
import com.example.data.remote.RemoteFamilyMembership
import com.example.data.remote.RemoteInvitation
import com.example.data.remote.RemoteMemberProfile
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseGateway
import com.example.data.sync.CloudSyncEngine
import com.example.domain.model.AuditOperation
import com.example.domain.model.AuthActionResult
import com.example.domain.model.AuthSession
import com.example.domain.model.CloudFamilySummary
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.CreatedInvitationResult
import com.example.domain.model.FamilyInvitation
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemberWithProfile
import com.example.domain.model.FamilyRole
import com.example.domain.model.FamilySpace
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MembershipStatus
import com.example.domain.model.MultiUserDefaults
import com.example.domain.model.SyncConflict
import com.example.domain.model.SyncCycleReport
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState
import com.example.domain.repository.CloudAuthAndFamilyRepository
import com.example.domain.security.InviteCodeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RoomAndSupabaseAuthRepository(
    private val database: JuthoorDatabase,
    private val authSessionStore: AuthSessionStore,
    private val preferencesRepository: PreferencesRepository,
    private val supabaseGateway: SupabaseGateway,
    private val mediaStorage: LocalMediaStorage? = null,
    private val supabaseConfigState: MutableStateFlow<SupabaseConfig>? = null,
) : CloudAuthAndFamilyRepository {

    private val configScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val familySpaceDao = database.familySpaceDao()
    private val userAccountDao = database.userAccountDao()
    private val familyMemberDao = database.familyMemberDao()
    private val familyInvitationDao = database.familyInvitationDao()
    private val personDao = database.personDao()
    private val marriageDao = database.marriageDao()
    private val parentChildDao = database.parentChildDao()
    private val educationDao = database.educationDao()
    private val photoDao = database.photoDao()
    private val eventDao = database.eventDao()
    private val familyGroupDao = database.familyGroupDao()
    private val auditLogDao = database.auditLogDao()
    private val syncOutboxDao = database.syncOutboxDao()
    private val syncMetadataDao = database.syncMetadataDao()
    private val syncConflictDao = database.syncConflictDao()

    private val syncEngine = CloudSyncEngine(
        database = database,
        supabaseGateway = supabaseGateway,
        mediaStorage = mediaStorage,
        preferencesRepository = preferencesRepository,
    )

    init {
        if (supabaseConfigState != null) {
            configScope.launch {
                authSessionStore.customCloudEndpointFlow.collect { endpoint ->
                    supabaseConfigState.value = SupabaseConfig.resolve(endpoint.url, endpoint.anonKey)
                }
            }
        }
    }

    override val isBackendConfigured: Boolean
        get() = supabaseGateway.isConfigured

    override val isBackendConfiguredFlow: Flow<Boolean> =
        authSessionStore.customCloudEndpointFlow.map { endpoint ->
            if (supabaseConfigState != null) {
                val resolved = SupabaseConfig.resolve(endpoint.url, endpoint.anonKey)
                supabaseConfigState.value = resolved
                resolved.isConfigured
            } else {
                supabaseGateway.isConfigured
            }
        }

    override val configuredServerUrl: Flow<String?> =
        authSessionStore.customCloudEndpointFlow.map { endpoint ->
            val resolved = SupabaseConfig.resolve(endpoint.url, endpoint.anonKey)
            resolved.url.takeIf { resolved.isConfigured }
        }

    override val hasCustomCloudConfigFlow: Flow<Boolean> =
        authSessionStore.customCloudEndpointFlow.map { endpoint ->
            endpoint.isPopulated
        }

    override suspend fun saveCustomCloudConfig(url: String, anonKey: String): Result<Unit> = runCatching {
        val validationError = SupabaseConfig.validateInputs(url, anonKey)
        if (validationError != null) {
            throw IllegalArgumentException(validationError)
        }
        val normalized = SupabaseConfig(url = url, anonKey = anonKey).normalized()
        authSessionStore.saveCustomCloudEndpoint(normalized.url, normalized.anonKey)
        supabaseConfigState?.value = normalized
    }

    override suspend fun clearCustomCloudConfig(): Result<Unit> = runCatching {
        authSessionStore.clearCustomCloudEndpoint()
        supabaseConfigState?.value = SupabaseConfig.fromBuildConfig()
    }

    override val currentSession: Flow<AuthSession?> = authSessionStore.sessionFlow

    override val allFamilySpaces: Flow<List<FamilySpace>> =
        familySpaceDao.observeAllActive().map { list -> list.map { it.toDomain() } }

    override val activeFamilySpace: Flow<FamilySpace?> =
        combine(authSessionStore.activeFamilyIdFlow, allFamilySpaces) { activeId, families ->
            families.firstOrNull { it.id == activeId } ?: families.firstOrNull()
        }

    override val activeMembership: Flow<FamilyMember?> =
        combine(currentSession, activeFamilySpace) { session, family ->
            session to family
        }.flatMapLatest { (session, family) ->
            if (session == null || family == null) {
                flowOf(null)
            } else {
                familyMemberDao.observeForFamily(family.id).map { members ->
                    members.firstOrNull { it.userId == session.userId }?.toDomain()
                }
            }
        }

    override val activeFamilyMembers: Flow<List<FamilyMemberWithProfile>> =
        activeFamilySpace.flatMapLatest { family ->
            if (family == null) {
                flowOf(emptyList())
            } else {
                combine(
                    familyMemberDao.observeForFamily(family.id),
                    userAccountDao.observeAll(),
                ) { members, accounts ->
                    val accountMap = accounts.associateBy { it.id }
                    members.map { entity ->
                        val account = accountMap[entity.userId]
                        FamilyMemberWithProfile(
                            member = entity.toDomain(),
                            displayName = account?.displayName ?: "عضو العائلة",
                            email = account?.email.orEmpty(),
                        )
                    }
                }
            }
        }

    override val activeFamilyInvitations: Flow<List<FamilyInvitation>> =
        activeFamilySpace.flatMapLatest { family ->
            if (family == null) {
                flowOf(emptyList())
            } else {
                familyInvitationDao.observeForFamily(family.id).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }

    override val pendingSyncCount: Flow<Int> =
        syncOutboxDao.observeAllPendingCount()

    override val unresolvedConflicts: Flow<List<SyncConflict>> =
        activeFamilySpace.flatMapLatest { family ->
            if (family == null) {
                flowOf(emptyList())
            } else {
                syncConflictDao.observeUnresolved(family.id).map { list -> list.map { it.toDomain() } }
            }
        }

    override val lastSyncedAtMillis: Flow<Long?> =
        activeFamilySpace.flatMapLatest { family ->
            if (family == null) {
                flowOf(null)
            } else {
                syncMetadataDao.observeForFamily(family.id).map { list ->
                    list.maxOfOrNull { it.lastSyncedAt }?.takeIf { it > 0L }
                }
            }
        }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
    ): Result<AuthActionResult> = runCatching {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()
        require(EMAIL_REGEX.matches(cleanEmail)) { "يرجى إدخال بريد إلكتروني صحيح." }
        require(password.length >= 6) { "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل." }
        require(cleanName.isNotBlank()) { "يرجى إدخال الاسم الكامل لصاحب الحساب." }

        when (val result = supabaseGateway.signUp(cleanEmail, password, cleanName).getOrThrow()) {
            is RemoteAuthResult.ConfirmationRequired -> {
                AuthActionResult.EmailConfirmationRequired(
                    email = result.email,
                    messageArabic = "تم إنشاء الحساب وإرسال رابط التفعيل إلى ${result.email}. فعّل بريدك ثم سجّل الدخول.",
                )
            }

            is RemoteAuthResult.Authenticated -> {
                val remote = result.session
                runCatching {
                    supabaseGateway.upsertUserProfile(
                        accessToken = remote.accessToken,
                        userId = remote.userId,
                        email = remote.email,
                        displayName = remote.displayName,
                    ).getOrThrow()
                }
                val session = persistAuthenticatedSession(remote)
                runCatching { syncRemoteMembershipsLocally(session) }
                AuthActionResult.Authenticated(session)
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> = runCatching {
        val cleanEmail = email.trim()
        require(EMAIL_REGEX.matches(cleanEmail)) { "يرجى إدخال بريد إلكتروني صحيح." }
        require(password.isNotBlank()) { "يرجى إدخال كلمة المرور." }

        val remote = supabaseGateway.signInWithPassword(cleanEmail, password).getOrThrow()
        runCatching {
            supabaseGateway.upsertUserProfile(
                accessToken = remote.accessToken,
                userId = remote.userId,
                email = remote.email,
                displayName = remote.displayName,
            ).getOrThrow()
        }
        val session = persistAuthenticatedSession(remote)
        val summaries = syncRemoteMembershipsLocally(session)
        val preferredFamilyId = session.activeFamilyId
            ?.takeIf { id -> summaries.any { it.family.id == id } }
            ?: summaries.firstOrNull()?.family?.id
        if (preferredFamilyId != null && preferredFamilyId != session.activeFamilyId) {
            authSessionStore.updateActiveFamilyId(preferredFamilyId)
            val updated = session.copy(activeFamilyId = preferredFamilyId)
            val activeSummary = summaries.firstOrNull { it.family.id == preferredFamilyId }
            if (activeSummary != null) {
                preferencesRepository.updateFamilyIdentity(
                    title = activeSummary.family.name,
                    motto = activeSummary.family.motto,
                )
            }
            updated
        } else {
            session
        }
    }

    override suspend fun refreshSessionIfNeeded(): Result<AuthSession?> = runCatching {
        val current = authSessionStore.getSessionOnce() ?: return@runCatching null
        if (!current.isAccessTokenExpired()) return@runCatching current
        if (current.refreshToken.isBlank()) return@runCatching current

        val refreshed = supabaseGateway.refreshToken(current.refreshToken).getOrThrow()
        persistAuthenticatedSession(refreshed)
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        val current = authSessionStore.getSessionOnce()
        if (current != null && current.accessToken.isNotBlank()) {
            runCatching { supabaseGateway.signOut(current.accessToken) }
        }
        authSessionStore.clearSession()
        // Keep all local Room genealogy records 100% intact; switch active pointer if local workspace exists
        val localDefault = familySpaceDao.getById(MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID)
        authSessionStore.updateActiveFamilyId(localDefault?.id)
    }

    override suspend fun createCloudFamilySpace(
        name: String,
        motto: String,
        founderPersonId: String?,
        adoptExistingLocalTree: Boolean,
    ): Result<CloudFamilySummary> = runCatching {
        val cleanName = name.trim()
        val cleanMotto = motto.trim().ifBlank { "نوثّق الأصول، ونحفظ امتداد الأجيال" }
        require(cleanName.isNotBlank()) { "يرجى إدخال اسم مساحة العائلة." }

        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أولاً قبل إنشاء مساحة العائلة السحابية.")

        val newFamilyId = UUID.randomUUID().toString()
        // Pass root_person_id = null during bootstrap RPC because local persons have not yet synced to PostgreSQL;
        // once persons are synced in Phase 9.5, root_person_id and linked_person_id are updated cleanly.
        val remoteMembership = supabaseGateway.createFamilySpace(
            accessToken = session.accessToken,
            familyId = newFamilyId,
            name = cleanName,
            motto = cleanMotto,
            rootPersonId = null,
        ).getOrThrow()

        val now = System.currentTimeMillis()
        val resolvedRootId = founderPersonId?.takeIf { it.isNotBlank() }
        val familyEntity = FamilySpaceEntity(
            id = remoteMembership.familyId,
            name = remoteMembership.familyName,
            motto = remoteMembership.familyMotto,
            ownerUserId = session.userId,
            rootPersonId = resolvedRootId,
            version = remoteMembership.familyVersion,
            isDeleted = false,
            deletedAt = null,
            syncState = if (resolvedRootId != null) SyncState.PENDING_UPSERT else SyncState.SYNCED,
            createdAt = remoteMembership.familyCreatedAt,
            updatedAt = now,
        )
        val memberEntity = FamilyMemberEntity(
            id = remoteMembership.memberId,
            familyId = remoteMembership.familyId,
            userId = session.userId,
            linkedPersonId = resolvedRootId,
            role = FamilyRole.OWNER,
            branchRootPersonId = resolvedRootId,
            canInvite = true,
            canExport = true,
            canManageMedia = true,
            status = MembershipStatus.ACTIVE,
            invitedByUserId = session.userId,
            joinedAt = remoteMembership.joinedAt,
            updatedAt = now,
            version = remoteMembership.memberVersion,
            syncState = if (resolvedRootId != null) SyncState.PENDING_UPSERT else SyncState.SYNCED,
        )

        database.withTransaction {
            ensureUserAccountCached(session, now)
            familySpaceDao.upsert(familyEntity)
            familyMemberDao.upsert(memberEntity)

            if (adoptExistingLocalTree) {
                promoteLocalRecordsToCloudFamily(
                    fromFamilyId = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID,
                    toFamilyId = familyEntity.id,
                    actorUserId = session.userId,
                    now = now,
                )
            }

            if (resolvedRootId != null) {
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        id = UUID.randomUUID().toString(),
                        familyId = familyEntity.id,
                        entityType = SyncEntityType.FAMILY,
                        entityId = familyEntity.id,
                        operation = SyncOutboxOperation.UPSERT,
                        payloadJson = JSONObject()
                            .put("id", familyEntity.id)
                            .put("rootPersonId", resolvedRootId)
                            .toString(),
                        baseVersion = familyEntity.version,
                        status = SyncOutboxStatus.PENDING,
                        retryCount = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }

            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "FamilySpaces",
                    entityId = familyEntity.id,
                    operation = AuditOperation.CREATE,
                    oldValue = null,
                    newValue = "إنشاء مساحة عائلة سحابية: ${familyEntity.name}",
                    operationDate = now,
                    familyId = familyEntity.id,
                    actorUserId = session.userId,
                    actorMemberRole = FamilyRole.OWNER,
                    syncState = SyncState.LOCAL_ONLY,
                )
            )
        }

        authSessionStore.updateActiveFamilyId(familyEntity.id)
        preferencesRepository.updateFamilyIdentity(title = familyEntity.name, motto = familyEntity.motto)

        CloudFamilySummary(
            family = familyEntity.toDomain(),
            membership = memberEntity.toDomain(),
        )
    }

    override suspend fun refreshMyFamilies(): Result<List<CloudFamilySummary>> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("لا توجد جلسة نشطة.")
        syncRemoteMembershipsLocally(session)
    }

    override suspend fun setActiveFamily(familyId: String): Result<Unit> = runCatching {
        val target = familySpaceDao.getById(familyId)
            ?: error("مساحة العائلة غير موجودة محلياً.")
        authSessionStore.updateActiveFamilyId(target.id)
        preferencesRepository.updateFamilyIdentity(title = target.name, motto = target.motto)
    }

    override suspend fun createInvitation(
        familyId: String,
        targetPersonId: String?,
        assignedRole: FamilyRole,
        branchRootPersonId: String?,
        maxUses: Int,
        validHours: Int,
    ): Result<CreatedInvitationResult> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أولاً قبل إنشاء دعوة عائلية.")
        require(assignedRole != FamilyRole.OWNER) { "لا يمكن إسناد دور المالك عبر دعوة." }
        require(maxUses in 1..10) { "عدد مرات الاستخدام المسموح يجب أن يكون بين 1 و 10." }
        require(validHours >= 1) { "مدة صلاحية الدعوة يجب أن تكون ساعة واحدة على الأقل." }

        val caller = familyMemberDao.getMembership(familyId, session.userId)
            ?: error("يجب أن تكون عضواً نشطاً في مساحة العائلة.")
        require(caller.status == MembershipStatus.ACTIVE && (caller.role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN) || caller.canInvite)) {
            "ليست لديك صلاحية إنشاء دعوات في هذه العائلة."
        }
        if (assignedRole == FamilyRole.ADMIN && caller.role != FamilyRole.OWNER) {
            error("مؤسس العائلة فقط يمكنه إرسال دعوة بصلاحية مدير مساعد.")
        }

        val cleanTargetId = targetPersonId?.takeIf { it.isNotBlank() }
        if (cleanTargetId != null) {
            val existingLinked = familyMemberDao.getActiveByLinkedPerson(familyId, cleanTargetId)
            if (existingLinked != null) {
                error("هذا الفرد في الشجرة مرتبط بالفعل بحساب عضو نشط آخر.")
            }
        }

        val resolvedBranchId = branchRootPersonId?.takeIf { it.isNotBlank() } ?: cleanTargetId
        val plainCode = InviteCodeHelper.generateCode()
        val codeHash = InviteCodeHelper.sha256Hex(plainCode)
        val codeHint = InviteCodeHelper.buildHint(plainCode)
        val now = System.currentTimeMillis()
        val expiresAt = now + (validHours.toLong() * 3_600_000L)

        val remote = supabaseGateway.createFamilyInvitation(
            accessToken = session.accessToken,
            familyId = familyId,
            targetPersonId = cleanTargetId,
            assignedRole = assignedRole,
            branchRootPersonId = resolvedBranchId,
            inviteCodeHash = codeHash,
            inviteCodeHint = codeHint,
            maxUses = maxUses,
            expiresAt = expiresAt,
        ).getOrThrow()

        val entity = remote.toEntity()
        database.withTransaction {
            familyInvitationDao.upsert(entity)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "FamilyInvitations",
                    entityId = entity.id,
                    operation = AuditOperation.CREATE,
                    oldValue = null,
                    newValue = "إنشاء دعوة (${entity.inviteCodeHint}) بدور ${assignedRole.arabicLabel}",
                    operationDate = now,
                    familyId = familyId,
                    actorUserId = session.userId,
                    actorMemberRole = caller.role,
                    syncState = SyncState.LOCAL_ONLY,
                )
            )
        }

        CreatedInvitationResult(
            invitation = entity.toDomain(),
            plainInviteCode = plainCode,
        )
    }

    override suspend fun revokeInvitation(invitationId: String): Result<Unit> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أولاً.")
        val existing = familyInvitationDao.getById(invitationId)
            ?: error("الدعوة غير موجودة.")
        supabaseGateway.revokeFamilyInvitation(session.accessToken, invitationId).getOrThrow()
        val now = System.currentTimeMillis()
        val caller = familyMemberDao.getMembership(existing.familyId, session.userId)
        database.withTransaction {
            familyInvitationDao.upsert(
                existing.copy(
                    status = InvitationStatus.REVOKED,
                    updatedAt = now,
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "FamilyInvitations",
                    entityId = invitationId,
                    operation = AuditOperation.UPDATE,
                    oldValue = existing.status.name,
                    newValue = InvitationStatus.REVOKED.name,
                    operationDate = now,
                    familyId = existing.familyId,
                    actorUserId = session.userId,
                    actorMemberRole = caller?.role,
                    syncState = SyncState.LOCAL_ONLY,
                )
            )
        }
    }

    override suspend fun refreshFamilyInvitations(familyId: String): Result<List<FamilyInvitation>> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("لا توجد جلسة نشطة.")
        val remoteList = supabaseGateway.fetchFamilyInvitations(session.accessToken, familyId).getOrThrow()
        val entities = remoteList.map { it.toEntity() }
        database.withTransaction {
            entities.forEach { familyInvitationDao.upsert(it) }
        }
        entities.map { it.toDomain() }
    }

    override suspend fun acceptInvitation(inviteCode: String): Result<CloudFamilySummary> = runCatching {
        val normalized = InviteCodeHelper.normalizeCode(inviteCode)
        require(normalized.isNotBlank()) { "يرجى إدخال رمز الدعوة." }

        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أو إنشاء حساب أولاً قبل قبول الدعوة.")

        val joined = supabaseGateway.acceptFamilyInvitation(session.accessToken, normalized).getOrThrow()
        val now = System.currentTimeMillis()
        val familyEntity = FamilySpaceEntity(
            id = joined.familyId,
            name = joined.familyName,
            motto = joined.familyMotto,
            ownerUserId = joined.ownerUserId.takeIf { it.isNotBlank() },
            rootPersonId = joined.rootPersonId,
            version = joined.familyVersion,
            isDeleted = false,
            deletedAt = null,
            syncState = SyncState.SYNCED,
            createdAt = joined.familyCreatedAt,
            updatedAt = joined.familyUpdatedAt,
        )
        val safeLinkedPersonId = joined.linkedPersonId?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
        val memberEntity = FamilyMemberEntity(
            id = joined.memberId,
            familyId = joined.familyId,
            userId = session.userId,
            linkedPersonId = safeLinkedPersonId,
            role = joined.role,
            branchRootPersonId = joined.branchRootPersonId ?: joined.linkedPersonId,
            canInvite = joined.canInvite,
            canExport = joined.canExport,
            canManageMedia = joined.canManageMedia,
            status = joined.status,
            invitedByUserId = null,
            joinedAt = joined.joinedAt,
            updatedAt = joined.memberUpdatedAt,
            version = joined.memberVersion,
            syncState = SyncState.SYNCED,
        )

        database.withTransaction {
            ensureUserAccountCached(session, now)
            familySpaceDao.upsert(familyEntity)
            // Guard local unique index (familyId, linkedPersonId) before upserting joined membership
            val linkedId = memberEntity.linkedPersonId
            if (!linkedId.isNullOrBlank()) {
                val conflictingLocal = familyMemberDao.getActiveByLinkedPerson(joined.familyId, linkedId)
                if (conflictingLocal != null && conflictingLocal.id != memberEntity.id) {
                    familyMemberDao.upsert(conflictingLocal.copy(linkedPersonId = null, updatedAt = now))
                }
            }
            familyMemberDao.upsert(memberEntity)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "FamilyMembers",
                    entityId = memberEntity.id,
                    operation = AuditOperation.CREATE,
                    oldValue = null,
                    newValue = "انضمام عضو جديد إلى ${familyEntity.name} بدور ${memberEntity.role.arabicLabel}",
                    operationDate = now,
                    familyId = familyEntity.id,
                    actorUserId = session.userId,
                    actorMemberRole = memberEntity.role,
                    syncState = SyncState.LOCAL_ONLY,
                )
            )
        }

        authSessionStore.updateActiveFamilyId(familyEntity.id)
        preferencesRepository.updateFamilyIdentity(title = familyEntity.name, motto = familyEntity.motto)
        runCatching { refreshFamilyMembers(familyEntity.id) }

        CloudFamilySummary(
            family = familyEntity.toDomain(),
            membership = memberEntity.toDomain(),
        )
    }

    override suspend fun refreshFamilyMembers(familyId: String): Result<List<FamilyMemberWithProfile>> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("لا توجد جلسة نشطة.")
        val remoteMembers = supabaseGateway.fetchFamilyMembersWithProfiles(session.accessToken, familyId).getOrThrow()
        val now = System.currentTimeMillis()
        val resultList = mutableListOf<FamilyMemberWithProfile>()
        database.withTransaction {
            remoteMembers.forEach { remote ->
                cacheRemoteMemberProfile(remote, now)
                resultList.add(
                    FamilyMemberWithProfile(
                        member = remote.toEntity(SyncState.SYNCED).toDomain(),
                        displayName = remote.displayName,
                        email = remote.email,
                    )
                )
            }
        }
        resultList
    }

    override suspend fun updateMemberLinkAndPermissions(
        familyId: String,
        memberId: String,
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ): Result<FamilyMemberWithProfile> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أولاً.")
        val caller = familyMemberDao.getMembership(familyId, session.userId)
            ?: error("ليست لديك عضوية نشطة في هذه العائلة.")
        require(caller.status == MembershipStatus.ACTIVE && caller.role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN)) {
            "إدارة الأعضاء وربط الحسابات متاحة للمؤسس أو المدير المساعد فقط."
        }

        val target = familyMemberDao.getById(memberId)
            ?: error("تعذر العثور على سجل العضو محلياً.")
        if (target.role == FamilyRole.OWNER && (role != FamilyRole.OWNER || status != MembershipStatus.ACTIVE)) {
            error("لا يمكن خفض صلاحية مؤسس العائلة أو تعطيل حسابه.")
        }
        if (caller.role == FamilyRole.ADMIN) {
            if (target.role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN) && target.userId != session.userId) {
                error("المدير المساعد لا يمكنه تعديل المؤسس أو مدير مساعد آخر.")
            }
            if (role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN) && role != target.role) {
                error("مؤسس العائلة فقط يملك صلاحية منح دور المدير المساعد.")
            }
        }
        if (role == FamilyRole.OWNER && target.role != FamilyRole.OWNER) {
            error("لا يمكن نقل دور المؤسس بهذه العملية.")
        }

        val cleanLinkedId = linkedPersonId?.takeIf { it.isNotBlank() }
        val cleanBranchId = branchRootPersonId?.takeIf { it.isNotBlank() } ?: cleanLinkedId
        if (cleanLinkedId != null && status == MembershipStatus.ACTIVE) {
            val conflict = familyMemberDao.getActiveByLinkedPerson(familyId, cleanLinkedId)
            if (conflict != null && conflict.id != memberId) {
                error("هذا الشخص في الشجرة مرتبط بالفعل بحساب عضو نشط آخر.")
            }
        }

        val remote = supabaseGateway.updateFamilyMemberLinkAndRole(
            accessToken = session.accessToken,
            familyId = familyId,
            memberId = memberId,
            linkedPersonId = cleanLinkedId,
            branchRootPersonId = cleanBranchId,
            role = role,
            canInvite = canInvite,
            canExport = canExport,
            canManageMedia = canManageMedia,
            status = status,
        ).getOrThrow()

        val now = System.currentTimeMillis()
        val needsDeferredLinkSync = cleanLinkedId != null && remote.linkedPersonId == null
        val localMemberEntity = remote.toEntity(
            syncState = if (needsDeferredLinkSync) SyncState.PENDING_UPSERT else SyncState.SYNCED,
            overrideLinkedPersonId = cleanLinkedId ?: remote.linkedPersonId,
            overrideBranchRootId = cleanBranchId ?: remote.branchRootPersonId,
        )

        database.withTransaction {
            val existingUser = userAccountDao.getById(remote.userId)
            if (remote.email.isNotBlank()) {
                userAccountDao.upsert(
                    UserAccountEntity(
                        id = remote.userId,
                        email = remote.email,
                        displayName = remote.displayName,
                        avatarStorageKey = existingUser?.avatarStorageKey,
                        createdAt = existingUser?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
            }
            if (!localMemberEntity.linkedPersonId.isNullOrBlank()) {
                val stale = familyMemberDao.getActiveByLinkedPerson(familyId, localMemberEntity.linkedPersonId)
                if (stale != null && stale.id != localMemberEntity.id) {
                    familyMemberDao.upsert(stale.copy(linkedPersonId = null, updatedAt = now))
                }
            }
            familyMemberDao.upsert(localMemberEntity)

            if (needsDeferredLinkSync) {
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        id = UUID.randomUUID().toString(),
                        familyId = familyId,
                        entityType = SyncEntityType.FAMILY_MEMBER,
                        entityId = localMemberEntity.id,
                        operation = SyncOutboxOperation.UPSERT,
                        payloadJson = JSONObject()
                            .put("memberId", localMemberEntity.id)
                            .put("linkedPersonId", localMemberEntity.linkedPersonId)
                            .put("branchRootPersonId", localMemberEntity.branchRootPersonId)
                            .toString(),
                        baseVersion = localMemberEntity.version,
                        status = SyncOutboxStatus.PENDING,
                        retryCount = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }

            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "FamilyMembers",
                    entityId = localMemberEntity.id,
                    operation = AuditOperation.UPDATE,
                    oldValue = target.linkedPersonId,
                    newValue = "دور=${role.arabicLabel}، ربط=${localMemberEntity.linkedPersonId ?: "بدون"}",
                    operationDate = now,
                    familyId = familyId,
                    actorUserId = session.userId,
                    actorMemberRole = caller.role,
                    syncState = SyncState.LOCAL_ONLY,
                )
            )
        }

        val cachedAccount = userAccountDao.getById(localMemberEntity.userId)
        FamilyMemberWithProfile(
            member = localMemberEntity.toDomain(),
            displayName = cachedAccount?.displayName ?: remote.displayName,
            email = cachedAccount?.email ?: remote.email,
        )
    }

    override suspend fun syncNow(familyId: String?): Result<SyncCycleReport> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أولاً قبل إجراء المزامنة السحابية.")
        val targetFamilyId = familyId
            ?: authSessionStore.getSessionOnce()?.activeFamilyId
            ?: familySpaceDao.getAllOnce()
                .firstOrNull { it.id != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID }
                ?.id
            ?: error("لا توجد مساحة عائلة سحابية نشطة للمزامنة.")

        require(targetFamilyId != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID) {
            "المساحة الحالية محلية فقط. أنشئ مساحة عائلة سحابية أو انضم إلى واحدة للمزامنة."
        }
        syncEngine.syncFamily(
            accessToken = session.accessToken,
            currentUserId = session.userId,
            familyId = targetFamilyId,
        )
    }

    override suspend fun resolveSyncConflict(
        conflictId: String,
        choice: ConflictResolutionChoice,
    ): Result<Unit> = runCatching {
        val session = refreshSessionIfNeeded().getOrThrow()
            ?: error("يرجى تسجيل الدخول أولاً لحل تعارض المزامنة.")
        syncEngine.resolveConflict(
            accessToken = session.accessToken,
            currentUserId = session.userId,
            conflictId = conflictId,
            choice = choice,
        )
    }

    private suspend fun persistAuthenticatedSession(remote: com.example.data.remote.RemoteSession): AuthSession {
        val previous = authSessionStore.getSessionOnce()
        val session = AuthSession(
            userId = remote.userId,
            email = remote.email,
            displayName = remote.displayName,
            accessToken = remote.accessToken,
            refreshToken = remote.refreshToken,
            expiresAtMillis = remote.expiresAtMillis,
            activeFamilyId = previous?.activeFamilyId,
        )
        authSessionStore.saveSession(session)
        database.withTransaction {
            ensureUserAccountCached(session, System.currentTimeMillis())
        }
        return session
    }

    private suspend fun ensureUserAccountCached(session: AuthSession, now: Long) {
        val existing = userAccountDao.getById(session.userId)
        userAccountDao.upsert(
            UserAccountEntity(
                id = session.userId,
                email = session.email,
                displayName = session.displayName,
                avatarStorageKey = existing?.avatarStorageKey,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    private suspend fun syncRemoteMembershipsLocally(session: AuthSession): List<CloudFamilySummary> {
        val remoteList = supabaseGateway.fetchMyFamilyMemberships(session.accessToken).getOrThrow()
        val now = System.currentTimeMillis()
        val summaries = mutableListOf<CloudFamilySummary>()
        database.withTransaction {
            ensureUserAccountCached(session, now)
            remoteList.forEach { item ->
                val existingFamily = familySpaceDao.getById(item.familyId)
                val familyEntity = FamilySpaceEntity(
                    id = item.familyId,
                    name = item.familyName,
                    motto = item.familyMotto,
                    ownerUserId = item.ownerUserId,
                    rootPersonId = item.rootPersonId ?: existingFamily?.rootPersonId,
                    version = item.familyVersion,
                    isDeleted = false,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                    createdAt = item.familyCreatedAt,
                    updatedAt = item.familyUpdatedAt,
                )
                val existingMember = familyMemberDao.getMembership(item.familyId, session.userId)
                val safeLinkedId = (item.linkedPersonId ?: existingMember?.linkedPersonId)
                    ?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
                val memberEntity = FamilyMemberEntity(
                    id = item.memberId,
                    familyId = item.familyId,
                    userId = session.userId,
                    linkedPersonId = safeLinkedId,
                    role = item.role,
                    branchRootPersonId = item.branchRootPersonId ?: existingMember?.branchRootPersonId,
                    canInvite = item.canInvite,
                    canExport = item.canExport,
                    canManageMedia = item.canManageMedia,
                    status = item.status,
                    invitedByUserId = existingMember?.invitedByUserId,
                    joinedAt = item.joinedAt,
                    updatedAt = item.memberUpdatedAt,
                    version = item.memberVersion,
                    syncState = SyncState.SYNCED,
                )
                familySpaceDao.upsert(familyEntity)
                if (!safeLinkedId.isNullOrBlank()) {
                    val stale = familyMemberDao.getActiveByLinkedPerson(item.familyId, safeLinkedId)
                    if (stale != null && stale.id != memberEntity.id) {
                        familyMemberDao.upsert(stale.copy(linkedPersonId = null, updatedAt = now))
                    }
                }
                familyMemberDao.upsert(memberEntity)
                summaries.add(CloudFamilySummary(familyEntity.toDomain(), memberEntity.toDomain()))
            }
        }
        return summaries
    }

    private suspend fun promoteLocalRecordsToCloudFamily(
        fromFamilyId: String,
        toFamilyId: String,
        actorUserId: String,
        now: Long,
    ) {
        familyGroupDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        personDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        marriageDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        parentChildDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        educationDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        photoDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        photoDao.reassignCrossRefsFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now)
        eventDao.reassignFamilyId(fromFamilyId, toFamilyId, SyncState.PENDING_UPSERT, now, actorUserId)
        auditLogDao.reassignFamilyId(fromFamilyId, toFamilyId)
        familySpaceDao.deleteById(fromFamilyId)

        // Enqueue in strict dependency order for future cloud push
        val outboxBatch = mutableListOf<SyncOutboxEntity>()
        fun enqueue(type: SyncEntityType, id: String, version: Long, label: String) {
            outboxBatch.add(
                SyncOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = toFamilyId,
                    entityType = type,
                    entityId = id,
                    operation = SyncOutboxOperation.UPSERT,
                    payloadJson = JSONObject().put("id", id).put("summary", label).toString(),
                    baseVersion = version,
                    status = SyncOutboxStatus.PENDING,
                    retryCount = 0,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        familyGroupDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.FAMILY_GROUP, it.id, it.version, it.groupName)
        }
        personDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.PERSON, it.id, it.version, it.fullName)
        }
        marriageDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.MARRIAGE, it.id, it.version, it.marriageStatus.name)
        }
        parentChildDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.PARENT_CHILD, it.id, it.version, it.relationshipType.name)
        }
        educationDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.EDUCATION, it.id, it.version, it.qualification)
        }
        photoDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.PHOTO, it.id, it.version, it.mediaType.name)
        }
        eventDao.getAllForFamilyOnce(toFamilyId).forEach {
            enqueue(SyncEntityType.EVENT, it.id, it.version, it.eventType.name)
        }

        if (outboxBatch.isNotEmpty()) {
            syncOutboxDao.insertAll(outboxBatch)
        }
    }

    private suspend fun cacheRemoteMemberProfile(remote: RemoteMemberProfile, now: Long) {
        val existingUser = userAccountDao.getById(remote.userId)
        if (remote.email.isNotBlank()) {
            userAccountDao.upsert(
                UserAccountEntity(
                    id = remote.userId,
                    email = remote.email,
                    displayName = remote.displayName,
                    avatarStorageKey = existingUser?.avatarStorageKey,
                    createdAt = existingUser?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
        val existingLocal = familyMemberDao.getById(remote.memberId)
        val preservedLinkedId = (remote.linkedPersonId ?: existingLocal?.linkedPersonId)
            ?.takeIf { personDao.getByIdIncludingDeleted(it) != null }
        val preservedBranchId = remote.branchRootPersonId ?: existingLocal?.branchRootPersonId
        if (!preservedLinkedId.isNullOrBlank()) {
            val stale = familyMemberDao.getActiveByLinkedPerson(remote.familyId, preservedLinkedId)
            if (stale != null && stale.id != remote.memberId) {
                familyMemberDao.upsert(stale.copy(linkedPersonId = null, updatedAt = now))
            }
        }
        familyMemberDao.upsert(
            remote.toEntity(
                syncState = existingLocal?.syncState ?: SyncState.SYNCED,
                overrideLinkedPersonId = preservedLinkedId,
                overrideBranchRootId = preservedBranchId,
            )
        )
    }

    private fun RemoteInvitation.toEntity(): FamilyInvitationEntity = FamilyInvitationEntity(
        id = id,
        familyId = familyId,
        targetPersonId = targetPersonId,
        assignedRole = assignedRole,
        branchRootPersonId = branchRootPersonId,
        inviteCodeHash = inviteCodeHash,
        inviteCodeHint = inviteCodeHint,
        maxUses = maxUses,
        usesCount = usesCount,
        expiresAt = expiresAt,
        status = status,
        createdByUserId = createdByUserId,
        acceptedByUserId = acceptedByUserId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun RemoteMemberProfile.toEntity(
        syncState: SyncState,
        overrideLinkedPersonId: String? = linkedPersonId,
        overrideBranchRootId: String? = branchRootPersonId,
    ): FamilyMemberEntity = FamilyMemberEntity(
        id = memberId,
        familyId = familyId,
        userId = userId,
        linkedPersonId = overrideLinkedPersonId,
        role = role,
        branchRootPersonId = overrideBranchRootId,
        canInvite = canInvite,
        canExport = canExport,
        canManageMedia = canManageMedia,
        status = status,
        invitedByUserId = invitedByUserId,
        joinedAt = joinedAt,
        updatedAt = updatedAt,
        version = version,
        syncState = syncState,
    )

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
