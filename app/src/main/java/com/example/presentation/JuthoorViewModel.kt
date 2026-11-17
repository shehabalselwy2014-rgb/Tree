package com.example.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.ZipBackupManager
import com.example.data.export.FamilyExportManager
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.PreferencesRepository
import com.example.domain.backup.BackupPreview
import com.example.domain.model.AuthActionResult
import com.example.domain.model.AuthSession
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.CreatedInvitationResult
import com.example.domain.model.DatePrecision
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyGroup
import com.example.domain.model.FamilyInvitation
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemberWithProfile
import com.example.domain.model.FamilyRole
import com.example.domain.model.FamilySpace
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.model.SyncConflict
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.domain.repository.CloudAuthAndFamilyRepository
import com.example.domain.repository.GenealogyRepository
import com.example.domain.security.PinSecurityHelper
import com.example.domain.stats.FamilyStatisticsReport
import com.example.domain.stats.StatisticsEngine
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.domain.util.HomeOverviewMetrics
import com.example.domain.util.UpcomingOccasion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class JuthoorUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val shouldShowOnboarding: Boolean = false,
    val persons: List<Person> = emptyList(),
    val marriages: List<Marriage> = emptyList(),
    val parentChildLinks: List<ParentChildLink> = emptyList(),
    val familyGroups: List<FamilyGroup> = emptyList(),
    val events: List<EventRecord> = emptyList(),
    val photos: List<PhotoRecord> = emptyList(),
    val overviewMetrics: HomeOverviewMetrics = HomeOverviewMetrics(),
    val statisticsReport: FamilyStatisticsReport = FamilyStatisticsReport(),
    val statsScopeRootId: String? = null,
    val generationByPerson: Map<String, Int> = emptyMap(),
    val upcomingOccasions: List<UpcomingOccasion> = emptyList(),
    val yearOccasions: List<UpcomingOccasion> = emptyList(),
    val recentPersons: List<Person> = emptyList(),
    val isAppUnlocked: Boolean = false,
    val isMaintenanceBusy: Boolean = false,
    val pendingBackupPreview: BackupPreview? = null,
    val isCloudBackendConfigured: Boolean = false,
    val authSession: AuthSession? = null,
    val activeFamilySpace: FamilySpace? = null,
    val familySpaces: List<FamilySpace> = emptyList(),
    val activeMembership: FamilyMember? = null,
    val activeFamilyMembers: List<FamilyMemberWithProfile> = emptyList(),
    val activeFamilyInvitations: List<FamilyInvitation> = emptyList(),
    val latestGeneratedInvite: CreatedInvitationResult? = null,
    val pendingSyncCount: Int = 0,
    val unresolvedSyncConflicts: List<SyncConflict> = emptyList(),
    val lastSyncedAtMillis: Long? = null,
    val isSyncing: Boolean = false,
    val isAuthBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
) {
    val shouldRequireUnlock: Boolean
        get() = !isLoading && preferences.pinEnabled && !isAppUnlocked
}

class JuthoorViewModel(
    private val repository: GenealogyRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaStorage: LocalMediaStorage,
    private val backupManager: ZipBackupManager? = null,
    private val exportManager: FamilyExportManager? = null,
    private val cloudAuthRepository: CloudAuthAndFamilyRepository? = null,
) : ViewModel() {

    private val feedbackState = MutableStateFlow<Pair<String?, String?>>(null to null)
    private val statsScopeRootId = MutableStateFlow<String?>(null)
    private val isAppUnlockedState = MutableStateFlow(false)
    private val isMaintenanceBusyState = MutableStateFlow(false)
    private val isAuthBusyState = MutableStateFlow(false)
    private val isSyncingState = MutableStateFlow(false)
    private val latestGeneratedInviteState = MutableStateFlow<CreatedInvitationResult?>(null)
    private val pendingBackupPreviewState = MutableStateFlow<BackupPreview?>(null)
    @Volatile
    private var latestPreferences: UserPreferences = UserPreferences()

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                latestPreferences = prefs
            }
        }
    }

    private val baseDataFlow = combine(
        preferencesRepository.preferencesFlow,
        repository.observeAllPersons(),
        repository.observeAllMarriages(),
        repository.observeAllParentChildLinks(),
        repository.observeAllFamilyGroups(),
    ) { prefs, persons, marriages, links, groups ->
        BaseDataBundle(prefs, persons, marriages, links, groups)
    }

    private val cloudCoreFlow = combine(
        cloudAuthRepository?.currentSession ?: flowOf(null),
        cloudAuthRepository?.activeFamilySpace ?: flowOf(null),
        cloudAuthRepository?.allFamilySpaces ?: flowOf(emptyList()),
        cloudAuthRepository?.activeMembership ?: flowOf(null),
        cloudAuthRepository?.pendingSyncCount ?: flowOf(0),
    ) { session, activeFamily, families, membership, pendingCount ->
        CloudBundle(
            authSession = session,
            activeFamilySpace = activeFamily,
            familySpaces = families,
            activeMembership = membership,
            pendingSyncCount = pendingCount,
        )
    }

    private val cloudDetailsFlow = combine(
        cloudAuthRepository?.activeFamilyMembers ?: flowOf(emptyList()),
        cloudAuthRepository?.activeFamilyInvitations ?: flowOf(emptyList()),
        cloudAuthRepository?.unresolvedConflicts ?: flowOf(emptyList()),
        cloudAuthRepository?.lastSyncedAtMillis ?: flowOf(null),
        latestGeneratedInviteState,
    ) { members, invitations, conflicts, lastSyncedAt, latestInvite ->
        CloudDetailsBundle(
            activeFamilyMembers = members,
            activeFamilyInvitations = invitations,
            unresolvedSyncConflicts = conflicts,
            lastSyncedAtMillis = lastSyncedAt,
            latestGeneratedInvite = latestInvite,
        )
    }

    private val cloudStateFlow = combine(
        cloudCoreFlow,
        cloudDetailsFlow,
        isSyncingState,
    ) { core, details, isSyncing ->
        core.copy(
            activeFamilyMembers = details.activeFamilyMembers,
            activeFamilyInvitations = details.activeFamilyInvitations,
            unresolvedSyncConflicts = details.unresolvedSyncConflicts,
            lastSyncedAtMillis = details.lastSyncedAtMillis,
            latestGeneratedInvite = details.latestGeneratedInvite,
            isSyncing = isSyncing,
        )
    }

    private val maintenanceStateFlow = combine(
        isAppUnlockedState,
        isMaintenanceBusyState,
        pendingBackupPreviewState,
        feedbackState,
        combine(cloudStateFlow, isAuthBusyState) { cloud, busy -> cloud to busy },
    ) { unlocked, busy, preview, messages, (cloud, authBusy) ->
        MaintenanceBundle(
            isAppUnlocked = unlocked,
            isMaintenanceBusy = busy,
            pendingBackupPreview = preview,
            messages = messages,
            cloud = cloud,
            isAuthBusy = authBusy,
        )
    }

    val uiState: StateFlow<JuthoorUiState> = combine(
        baseDataFlow,
        repository.observeAllEvents(),
        repository.observeAllPhotos(),
        statsScopeRootId,
        maintenanceStateFlow,
    ) { base, events, photos, scopeRootId, maint ->
        val persons = base.persons
        val marriages = base.marriages
        val links = base.links
        val groups = base.groups
        val prefs = base.prefs

        val overview = GenealogyMetricsCalculator.computeHomeOverview(persons, marriages, links)
        val genMap = GenealogyMetricsCalculator.computeGenerationByPerson(persons, links)
        val upcoming90 = GenealogyMetricsCalculator.computeUpcomingOccasions(
            persons, marriages, events, horizonDays = 90, limit = 5
        )
        val yearAll = GenealogyMetricsCalculator.computeUpcomingOccasions(
            persons, marriages, events, horizonDays = 365, limit = 100
        )
        val statsReport = StatisticsEngine.compute(
            persons = persons,
            marriages = marriages,
            links = links,
            familyGroups = groups,
            scopedRootPersonId = scopeRootId,
        )
        val recent = persons.sortedByDescending { it.createdAt }.take(5)
        val showOnboarding = !prefs.onboardingCompleted && persons.isEmpty()

        JuthoorUiState(
            isLoading = false,
            preferences = prefs,
            shouldShowOnboarding = showOnboarding,
            persons = persons,
            marriages = marriages,
            parentChildLinks = links,
            familyGroups = groups,
            events = events,
            photos = photos,
            overviewMetrics = overview,
            statisticsReport = statsReport,
            statsScopeRootId = scopeRootId,
            generationByPerson = genMap,
            upcomingOccasions = upcoming90,
            yearOccasions = yearAll,
            recentPersons = recent,
            isAppUnlocked = if (prefs.pinEnabled) maint.isAppUnlocked else true,
            isMaintenanceBusy = maint.isMaintenanceBusy,
            pendingBackupPreview = maint.pendingBackupPreview,
            isCloudBackendConfigured = cloudAuthRepository?.isBackendConfigured ?: false,
            authSession = maint.cloud.authSession,
            activeFamilySpace = maint.cloud.activeFamilySpace,
            familySpaces = maint.cloud.familySpaces,
            activeMembership = maint.cloud.activeMembership,
            activeFamilyMembers = maint.cloud.activeFamilyMembers,
            activeFamilyInvitations = maint.cloud.activeFamilyInvitations,
            latestGeneratedInvite = maint.cloud.latestGeneratedInvite,
            pendingSyncCount = maint.cloud.pendingSyncCount,
            unresolvedSyncConflicts = maint.cloud.unresolvedSyncConflicts,
            lastSyncedAtMillis = maint.cloud.lastSyncedAtMillis,
            isSyncing = maint.cloud.isSyncing,
            isAuthBusy = maint.isAuthBusy,
            feedbackMessage = maint.messages.first,
            errorMessage = maint.messages.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JuthoorUiState(),
    )

    private data class BaseDataBundle(
        val prefs: UserPreferences,
        val persons: List<Person>,
        val marriages: List<Marriage>,
        val links: List<ParentChildLink>,
        val groups: List<FamilyGroup>,
    )

    private data class CloudDetailsBundle(
        val activeFamilyMembers: List<FamilyMemberWithProfile> = emptyList(),
        val activeFamilyInvitations: List<FamilyInvitation> = emptyList(),
        val unresolvedSyncConflicts: List<SyncConflict> = emptyList(),
        val lastSyncedAtMillis: Long? = null,
        val latestGeneratedInvite: CreatedInvitationResult? = null,
    )

    private data class CloudBundle(
        val authSession: AuthSession? = null,
        val activeFamilySpace: FamilySpace? = null,
        val familySpaces: List<FamilySpace> = emptyList(),
        val activeMembership: FamilyMember? = null,
        val activeFamilyMembers: List<FamilyMemberWithProfile> = emptyList(),
        val activeFamilyInvitations: List<FamilyInvitation> = emptyList(),
        val unresolvedSyncConflicts: List<SyncConflict> = emptyList(),
        val lastSyncedAtMillis: Long? = null,
        val latestGeneratedInvite: CreatedInvitationResult? = null,
        val pendingSyncCount: Int = 0,
        val isSyncing: Boolean = false,
    )

    private data class MaintenanceBundle(
        val isAppUnlocked: Boolean,
        val isMaintenanceBusy: Boolean,
        val pendingBackupPreview: BackupPreview?,
        val messages: Pair<String?, String?>,
        val cloud: CloudBundle = CloudBundle(),
        val isAuthBusy: Boolean = false,
    )

    fun observeEducationForPerson(personId: String): Flow<List<EducationRecord>> =
        repository.observeEducationForPerson(personId)

    fun observeEventsForPerson(personId: String): Flow<List<EventRecord>> =
        repository.observeEventsForPerson(personId)

    fun setStatsScopeRoot(personId: String?) {
        statsScopeRootId.value = personId
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
            feedbackState.value = if (enabled) {
                "تم تفعيل تذكيرات المناسبات العائلية." to null
            } else {
                "تم إيقاف تذكيرات المناسبات." to null
            }
        }
    }

    fun saveEducation(
        personId: String,
        qualification: String,
        specialization: String,
        institution: String,
        graduationYear: Int?,
        notes: String?,
        existingId: String? = null,
    ) {
        viewModelScope.launch {
            val record = EducationRecord(
                id = existingId ?: UUID.randomUUID().toString(),
                personId = personId,
                qualification = qualification.trim(),
                specialization = specialization.trim().takeIf { it.isNotEmpty() },
                institution = institution.trim().takeIf { it.isNotEmpty() },
                graduationYear = graduationYear,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            repository.saveEducation(record)
                .onSuccess {
                    feedbackState.value = "تم حفظ المؤهل الدراسي." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حفظ المؤهل الدراسي.")
                }
        }
    }

    fun deleteEducation(educationId: String) {
        viewModelScope.launch {
            repository.deleteEducation(educationId)
                .onSuccess { feedbackState.value = "تم حذف المؤهل الدراسي." to null }
                .onFailure { err -> feedbackState.value = null to err.message }
        }
    }

    fun saveEvent(
        personId: String?,
        eventType: FamilyEventType,
        eventDate: String,
        eventPlace: String,
        description: String,
    ) {
        viewModelScope.launch {
            val event = EventRecord(
                id = UUID.randomUUID().toString(),
                personId = personId,
                eventType = eventType,
                eventDate = eventDate.trim().takeIf { it.isNotEmpty() },
                eventDatePrecision = inferDatePrecision(eventDate),
                eventPlace = eventPlace.trim().takeIf { it.isNotEmpty() },
                description = description.trim().takeIf { it.isNotEmpty() },
            )
            repository.saveEvent(event)
                .onSuccess { feedbackState.value = "تم حفظ الحدث العائلي." to null }
                .onFailure { err -> feedbackState.value = null to err.message }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
                .onSuccess { feedbackState.value = "تم حذف الحدث." to null }
                .onFailure { err -> feedbackState.value = null to err.message }
        }
    }

    fun savePhotoFromUri(
        uri: Uri,
        primaryPersonId: String?,
        taggedPersonIds: List<String>,
        description: String,
        photoDate: String,
        mediaType: MediaType,
    ) {
        viewModelScope.launch {
            mediaStorage.copyFromUri(uri)
                .onSuccess { path ->
                    val photo = PhotoRecord(
                        id = UUID.randomUUID().toString(),
                        personId = primaryPersonId,
                        taggedPersonIds = (taggedPersonIds + listOfNotNull(primaryPersonId)).distinct(),
                        filePath = path,
                        description = description.trim().takeIf { it.isNotEmpty() },
                        photoDate = photoDate.trim().takeIf { it.isNotEmpty() },
                        photoDatePrecision = inferDatePrecision(photoDate),
                        mediaType = mediaType,
                    )
                    repository.savePhoto(photo)
                        .onSuccess { feedbackState.value = "تم حفظ الصورة في ألبوم العائلة." to null }
                        .onFailure { err -> feedbackState.value = null to err.message }
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حفظ الصورة محلياً.")
                }
        }
    }

    fun deletePhoto(photo: PhotoRecord) {
        viewModelScope.launch {
            repository.deletePhoto(photo.id)
                .onSuccess {
                    mediaStorage.deleteFileIfManaged(photo.filePath)
                    feedbackState.value = "تم حذف الصورة من الألبوم." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to err.message
                }
        }
    }

    fun clearMessages() {
        feedbackState.value = null to null
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            preferencesRepository.markOnboardingCompleted()
        }
    }

    fun completeOnboarding(
        familyTitle: String,
        familyMotto: String,
        founderFirstName: String,
        founderMiddleName: String,
        founderFamilyName: String,
        founderGender: Gender,
        founderBirthDate: String,
        onFounderCreated: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            preferencesRepository.updateFamilyIdentity(familyTitle, familyMotto)
            var createdFounderId: String? = null
            if (founderFirstName.isNotBlank()) {
                val fullName = listOf(founderFirstName, founderMiddleName, founderFamilyName)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                val precision = inferDatePrecision(founderBirthDate)
                val founder = Person(
                    id = UUID.randomUUID().toString(),
                    fullName = fullName,
                    firstName = founderFirstName.trim(),
                    middleName = founderMiddleName.trim().takeIf { it.isNotEmpty() },
                    familyName = founderFamilyName.trim().takeIf { it.isNotEmpty() },
                    gender = founderGender,
                    birthDate = founderBirthDate.trim().takeIf { it.isNotEmpty() },
                    birthDatePrecision = precision,
                )
                repository.savePerson(founder)
                    .onSuccess { saved ->
                        createdFounderId = saved.id
                        repository.saveFamilyGroup(
                            FamilyGroup(
                                id = UUID.randomUUID().toString(),
                                groupName = familyTitle.trim().ifBlank { "الفرع الرئيسي" },
                                description = "الفرع المؤسس للعائلة",
                                rootPersonId = saved.id,
                            )
                        )
                    }
                    .onFailure { err ->
                        feedbackState.value = null to (err.message ?: "تعذر حفظ الفرد الأول.")
                        return@launch
                    }
            }
            preferencesRepository.markOnboardingCompleted()
            feedbackState.value = "تم تأسيس ملف العائلة بنجاح." to null
            createdFounderId?.let(onFounderCreated)
        }
    }

    fun createPartnerAndMarry(
        anchorPerson: Person,
        partnerFirstName: String,
        partnerFullName: String,
        partnerBirthDate: String,
        marriageDate: String,
        marriagePlace: String,
        status: MarriageRecordStatus,
        endDate: String? = null,
        endReason: String? = null,
        notes: String? = null,
        onSaved: (Marriage) -> Unit = {},
    ) {
        viewModelScope.launch {
            val partnerGender = if (anchorPerson.gender == Gender.MALE) Gender.FEMALE else Gender.MALE
            val cleanFull = partnerFullName.trim().ifBlank { partnerFirstName.trim() }
            val cleanFirst = partnerFirstName.trim().ifBlank { cleanFull.substringBefore(" ") }
            val newPartner = Person(
                id = UUID.randomUUID().toString(),
                fullName = cleanFull,
                firstName = cleanFirst,
                gender = partnerGender,
                birthDate = partnerBirthDate.trim().takeIf { it.isNotEmpty() },
                birthDatePrecision = inferDatePrecision(partnerBirthDate),
                familyGroupId = anchorPerson.familyGroupId,
            )
            repository.savePerson(newPartner)
                .onSuccess { savedPartner ->
                    val marriage = Marriage(
                        id = UUID.randomUUID().toString(),
                        partnerOneId = anchorPerson.id,
                        partnerTwoId = savedPartner.id,
                        marriageDate = marriageDate.trim().takeIf { it.isNotEmpty() },
                        marriageDatePrecision = inferDatePrecision(marriageDate),
                        marriagePlace = marriagePlace.trim().takeIf { it.isNotEmpty() },
                        marriageStatus = status,
                        endDate = if (status == MarriageRecordStatus.ACTIVE) null else endDate?.trim()?.takeIf { it.isNotEmpty() },
                        endReason = if (status == MarriageRecordStatus.ACTIVE) null else endReason?.trim()?.takeIf { it.isNotEmpty() },
                        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    )
                    repository.saveMarriage(marriage)
                        .onSuccess { savedMarriage ->
                            feedbackState.value = "تمت إضافة «${savedPartner.fullName}» وتسجيل الزواج." to null
                            onSaved(savedMarriage)
                        }
                        .onFailure { err ->
                            // Rollback newly created orphan partner if marriage rule rejected it (e.g., >4 wives)
                            repository.deletePersonSafely(savedPartner.id)
                            feedbackState.value = null to (err.message ?: "تعذر تسجيل الزواج.")
                        }
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حفظ بيانات الزوج/الزوجة.")
                }
        }
    }

    fun savePerson(
        person: Person,
        optionalFatherId: String? = null,
        optionalMotherId: String? = null,
        optionalMarriageId: String? = null,
        onSaved: (Person) -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.savePerson(person)
                .onSuccess { saved ->
                    if (!optionalFatherId.isNullOrBlank()) {
                        repository.addParentChildLink(
                            parentId = optionalFatherId,
                            childId = saved.id,
                            relationshipType = ParentRelationshipType.BIOLOGICAL_FATHER,
                            marriageId = optionalMarriageId,
                        )
                    }
                    if (!optionalMotherId.isNullOrBlank()) {
                        repository.addParentChildLink(
                            parentId = optionalMotherId,
                            childId = saved.id,
                            relationshipType = ParentRelationshipType.BIOLOGICAL_MOTHER,
                            marriageId = optionalMarriageId,
                        )
                    }
                    feedbackState.value = "تم حفظ بيانات «${saved.fullName}»." to null
                    onSaved(saved)
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حفظ بيانات الفرد.")
                }
        }
    }

    fun deletePerson(personId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val existing = repository.getPersonById(personId)
            repository.deletePersonSafely(personId)
                .onSuccess {
                    mediaStorage.deleteFileIfManaged(existing?.profileImagePath)
                    feedbackState.value = "تم حذف السجل ومعالجة العلاقات المرتبطة بأمان." to null
                    onDeleted()
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حذف الفرد.")
                }
        }
    }

    suspend fun copyPickedImageToInternal(uri: Uri): Result<String> =
        mediaStorage.copyFromUri(uri)

    fun saveOrUpdateMarriage(
        existingMarriageId: String? = null,
        partnerOneId: String,
        partnerTwoId: String,
        marriageDate: String,
        marriagePlace: String,
        status: MarriageRecordStatus,
        endDate: String? = null,
        endReason: String? = null,
        notes: String? = null,
        onSaved: (Marriage) -> Unit = {},
    ) {
        viewModelScope.launch {
            val marriage = Marriage(
                id = existingMarriageId ?: UUID.randomUUID().toString(),
                partnerOneId = partnerOneId,
                partnerTwoId = partnerTwoId,
                marriageDate = marriageDate.trim().takeIf { it.isNotEmpty() },
                marriageDatePrecision = inferDatePrecision(marriageDate),
                marriagePlace = marriagePlace.trim().takeIf { it.isNotEmpty() },
                marriageStatus = status,
                endDate = if (status == MarriageRecordStatus.ACTIVE) null else endDate?.trim()?.takeIf { it.isNotEmpty() },
                endReason = if (status == MarriageRecordStatus.ACTIVE) null else endReason?.trim()?.takeIf { it.isNotEmpty() },
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            repository.saveMarriage(marriage)
                .onSuccess { saved ->
                    feedbackState.value = if (existingMarriageId == null) {
                        "تم تسجيل الزواج بنجاح." to null
                    } else {
                        "تم تحديث بيانات الزواج مع حفظ ارتباط الأبناء." to null
                    }
                    onSaved(saved)
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حفظ بيانات الزواج.")
                }
        }
    }

    fun deleteMarriage(marriageId: String) {
        viewModelScope.launch {
            repository.deleteMarriage(marriageId)
                .onSuccess {
                    feedbackState.value = "تم حذف سجل الزواج مع الإبقاء على بيانات الأفراد." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حذف الزواج.")
                }
        }
    }

    fun addQuickChild(
        father: Person?,
        mother: Person?,
        marriageId: String?,
        childFirstName: String,
        customFullName: String?,
        gender: Gender,
        birthDate: String,
        isKafala: Boolean = false,
        isUnconfirmed: Boolean = false,
        onSaved: (Person) -> Unit = {},
    ) {
        viewModelScope.launch {
            val autoName = com.example.domain.util.PatronymicNameHelper.buildFromFather(childFirstName, father)
            val resolvedFull = if (isKafala) {
                customFullName?.trim()?.takeIf { it.isNotEmpty() } ?: childFirstName.trim()
            } else {
                customFullName?.trim()?.takeIf { it.isNotEmpty() } ?: autoName.fullName
            }
            val child = Person(
                id = UUID.randomUUID().toString(),
                fullName = resolvedFull,
                firstName = autoName.firstName,
                middleName = if (isKafala) null else autoName.middleName,
                familyName = if (isKafala) null else autoName.familyName,
                gender = gender,
                birthDate = birthDate.trim().takeIf { it.isNotEmpty() },
                birthDatePrecision = inferDatePrecision(birthDate),
                familyGroupId = if (isKafala) null else (father?.familyGroupId ?: mother?.familyGroupId),
            )
            repository.savePerson(child)
                .onSuccess { savedChild ->
                    if (isKafala) {
                        val guardian = father ?: mother
                        if (guardian != null) {
                            repository.addParentChildLink(
                                parentId = guardian.id,
                                childId = savedChild.id,
                                relationshipType = ParentRelationshipType.ADOPTIVE_GUARDIAN,
                                marriageId = marriageId,
                            )
                        }
                    } else {
                        if (father != null) {
                            repository.addParentChildLink(
                                parentId = father.id,
                                childId = savedChild.id,
                                relationshipType = if (isUnconfirmed) {
                                    ParentRelationshipType.UNCONFIRMED_FATHER
                                } else {
                                    ParentRelationshipType.BIOLOGICAL_FATHER
                                },
                                marriageId = marriageId,
                            )
                        }
                        if (mother != null) {
                            repository.addParentChildLink(
                                parentId = mother.id,
                                childId = savedChild.id,
                                relationshipType = if (isUnconfirmed) {
                                    ParentRelationshipType.UNCONFIRMED_MOTHER
                                } else {
                                    ParentRelationshipType.BIOLOGICAL_MOTHER
                                },
                                marriageId = marriageId,
                            )
                        }
                    }
                    feedbackState.value = "تمت إضافة «${savedChild.fullName}» وربطه بالأسرة." to null
                    onSaved(savedChild)
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر إضافة الابن.")
                }
        }
    }

    fun endMarriage(
        marriageId: String,
        endStatus: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
    ) {
        viewModelScope.launch {
            repository.endMarriage(marriageId, endStatus, endDate, endReason)
                .onSuccess {
                    feedbackState.value = "تم تحديث حالة الزواج مع حفظ السجل التاريخي." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تحديث حالة الزواج.")
                }
        }
    }

    fun addParentChildLink(
        parentId: String,
        childId: String,
        relationshipType: ParentRelationshipType,
        marriageId: String? = null,
    ) {
        viewModelScope.launch {
            repository.addParentChildLink(parentId, childId, relationshipType, marriageId)
                .onSuccess {
                    feedbackState.value = "تم ربط العلاقة العائلية بنجاح." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر ربط العلاقة.")
                }
        }
    }

    fun deleteParentChildLink(linkId: String) {
        viewModelScope.launch {
            repository.deleteParentChildLink(linkId)
                .onSuccess {
                    feedbackState.value = "تم فك ارتباط النسب بنجاح." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر فك الارتباط.")
                }
        }
    }

    fun saveFamilyGroup(name: String, description: String, rootPersonId: String? = null) {
        viewModelScope.launch {
            val group = FamilyGroup(
                id = UUID.randomUUID().toString(),
                groupName = name.trim(),
                description = description.trim().takeIf { it.isNotEmpty() },
                rootPersonId = rootPersonId,
            )
            repository.saveFamilyGroup(group)
                .onSuccess {
                    feedbackState.value = "تم حفظ الفرع العائلي «${it.groupName}»." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حفظ الفرع.")
                }
        }
    }

    fun deleteFamilyGroup(groupId: String) {
        viewModelScope.launch {
            repository.deleteFamilyGroup(groupId)
                .onSuccess {
                    feedbackState.value = "تم حذف الفرع العائلي." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حذف الفرع.")
                }
        }
    }

    fun updateFamilyIdentity(title: String, motto: String) {
        viewModelScope.launch {
            preferencesRepository.updateFamilyIdentity(title, motto)
            feedbackState.value = "تم تحديث هوية العائلة." to null
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setShowHijriDates(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowHijriDates(enabled)
        }
    }

    // ---- Phase 7: Privacy Lock (PIN & Biometric) ----

    private var pinFailedAttempts: Int = 0
    private var pinLockoutUntilMillis: Long = 0L

    fun verifyAndUnlockWithPin(candidatePin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < pinLockoutUntilMillis) {
            return false
        }
        val prefs = latestPreferences
        val ok = PinSecurityHelper.verifyPin(candidatePin, prefs.pinSalt, prefs.pinHash)
        if (ok) {
            pinFailedAttempts = 0
            pinLockoutUntilMillis = 0L
            isAppUnlockedState.value = true
            if (PinSecurityHelper.needsRehash(prefs.pinHash)) {
                viewModelScope.launch {
                    val freshSalt = PinSecurityHelper.generateSalt()
                    val upgradedHash = PinSecurityHelper.hashPin(candidatePin, freshSalt)
                    preferencesRepository.setPinLock(freshSalt, upgradedHash, prefs.biometricEnabled)
                }
            }
        } else {
            pinFailedAttempts += 1
            if (pinFailedAttempts >= 5) {
                val multiplier = (pinFailedAttempts - 4).coerceAtMost(4)
                pinLockoutUntilMillis = now + (30_000L * multiplier)
            }
        }
        return ok
    }

    fun unlockWithBiometric() {
        isAppUnlockedState.value = true
    }

    fun lockAppNow() {
        if (latestPreferences.pinEnabled || uiState.value.preferences.pinEnabled) {
            isAppUnlockedState.value = false
        }
    }

    fun configurePin(newPin: String) {
        val validationError = PinSecurityHelper.validatePinFormat(newPin)
        if (validationError != null) {
            feedbackState.value = null to validationError
            return
        }
        viewModelScope.launch {
            val salt = PinSecurityHelper.generateSalt()
            val hash = PinSecurityHelper.hashPin(newPin, salt)
            preferencesRepository.setPinLock(salt, hash)
            isAppUnlockedState.value = true
            feedbackState.value = "تم تفعيل وحفظ رمز قفل الخصوصية (PIN)." to null
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            preferencesRepository.clearPinLock()
            isAppUnlockedState.value = true
            feedbackState.value = "تم إلغاء قفل الخصوصية." to null
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(enabled)
            feedbackState.value = if (enabled) {
                "تم تفعيل الفتح بالمصادقة الحيوية." to null
            } else {
                "تم إيقاف المصادقة الحيوية (يبقى رمز PIN فعالاً)." to null
            }
        }
    }

    // ---- Phase 7: Backup & Restore (ZIP + SHA-256) ----

    fun exportBackup(uri: Uri) {
        val manager = backupManager ?: return
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.exportBackupToUri(uri)
                .onSuccess { manifest ->
                    feedbackState.value =
                        "تم تصدير النسخة الاحتياطية (${manifest.counts.personsCount} فرداً، ${manifest.counts.mediaFilesCount} ملف وسائط) بنجاح." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تصدير النسخة الاحتياطية.")
                }
            isMaintenanceBusyState.value = false
        }
    }

    fun inspectBackupForRestore(uri: Uri) {
        val manager = backupManager ?: return
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.inspectBackupFromUri(uri)
                .onSuccess { preview ->
                    pendingBackupPreviewState.value = preview
                }
                .onFailure { err ->
                    pendingBackupPreviewState.value = null
                    feedbackState.value = null to (err.message ?: "ملف النسخة الاحتياطية غير صالح أو تالف.")
                }
            isMaintenanceBusyState.value = false
        }
    }

    fun dismissBackupPreview() {
        pendingBackupPreviewState.value = null
    }

    fun confirmRestoreFromPreview() {
        val manager = backupManager ?: return
        val preview = pendingBackupPreviewState.value ?: return
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.restoreFromVerifiedPreview(preview)
                .onSuccess { result ->
                    pendingBackupPreviewState.value = null
                    feedbackState.value =
                        "تمت الاستعادة بنجاح: ${result.restoredCounts.personsCount} فرداً و${result.restoredCounts.marriagesCount} زواجاً." to null
                }
                .onFailure { err ->
                    pendingBackupPreviewState.value = null
                    feedbackState.value = null to (err.message ?: "تعذر إتمام عملية الاستعادة.")
                }
            isMaintenanceBusyState.value = false
        }
    }

    // ---- Phase 7: Real Exports (PDF, PNG, CSV) ----

    fun exportPdf(uri: Uri) {
        val manager = exportManager ?: return
        val state = uiState.value
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.exportPdfToUri(
                uri = uri,
                familyTitle = state.preferences.familyTitle,
                familyMotto = state.preferences.familyMotto,
                persons = state.persons,
                marriages = state.marriages,
                links = state.parentChildLinks,
                familyGroups = state.familyGroups,
                showHijri = state.preferences.showHijriDates,
            )
                .onSuccess { pages ->
                    feedbackState.value = "تم تصدير ملف PDF الشامل ($pages صفحات) بنجاح." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تصدير ملف PDF.")
                }
            isMaintenanceBusyState.value = false
        }
    }

    fun exportTreePng(uri: Uri) {
        val manager = exportManager ?: return
        val state = uiState.value
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.exportTreePngToUri(
                uri = uri,
                familyTitle = state.preferences.familyTitle,
                persons = state.persons,
                marriages = state.marriages,
                links = state.parentChildLinks,
                showHijri = state.preferences.showHijriDates,
            )
                .onSuccess { (w, h) ->
                    feedbackState.value = "تم تصدير صورة الشجرة بدقة ${w}×${h} بكسل." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تصدير صورة الشجرة.")
                }
            isMaintenanceBusyState.value = false
        }
    }

    fun exportCsv(uri: Uri) {
        val manager = exportManager ?: return
        val state = uiState.value
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.exportCsvToUri(
                uri = uri,
                familyTitle = state.preferences.familyTitle,
                persons = state.persons,
                marriages = state.marriages,
                links = state.parentChildLinks,
                familyGroups = state.familyGroups,
            )
                .onSuccess { count ->
                    feedbackState.value = "تم تصدير جدول CSV لعدد $count فرداً بنجاح." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تصدير ملف CSV.")
                }
            isMaintenanceBusyState.value = false
        }
    }

    fun shareTreeAsFile(
        asPdf: Boolean,
        onShareReady: (Uri, String) -> Unit,
    ) {
        val manager = exportManager ?: return
        val state = uiState.value
        viewModelScope.launch {
            isMaintenanceBusyState.value = true
            manager.exportTreeToShareUri(
                asPdf = asPdf,
                familyTitle = state.preferences.familyTitle,
                familyMotto = state.preferences.familyMotto,
                persons = state.persons,
                marriages = state.marriages,
                links = state.parentChildLinks,
                familyGroups = state.familyGroups,
                showHijri = state.preferences.showHijriDates,
            )
                .onSuccess { (uri, mimeType) ->
                    onShareReady(uri, mimeType)
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تجهيز ملف المشاركة.")
                }
            isMaintenanceBusyState.value = false
        }
    }

        fun signInWithEmail(email: String, password: String) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.signIn(email, password)
                .onSuccess { session ->
                    feedbackState.value = "تم تسجيل الدخول بنجاح (${session.displayName})." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تسجيل الدخول.")
                }
            isAuthBusyState.value = false
        }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.signUp(email, password, displayName)
                .onSuccess { result ->
                    when (result) {
                        is AuthActionResult.Authenticated -> {
                            feedbackState.value = "تم إنشاء الحساب وتسجيل الدخول بنجاح (${result.session.displayName})." to null
                        }
                        is AuthActionResult.EmailConfirmationRequired -> {
                            feedbackState.value = result.messageArabic to null
                        }
                    }
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر إنشاء الحساب.")
                }
            isAuthBusyState.value = false
        }
    }

    fun signOutFromCloud() {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.signOut()
                .onSuccess {
                    feedbackState.value = "تم تسجيل الخروج مع حفظ جميع بيانات شجرة العائلة محلياً." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تسجيل الخروج.")
                }
            isAuthBusyState.value = false
        }
    }

    fun createCloudFamilySpace(
        name: String,
        motto: String,
        founderPersonId: String?,
        adoptExistingLocalTree: Boolean,
    ) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.createCloudFamilySpace(
                name = name,
                motto = motto,
                founderPersonId = founderPersonId,
                adoptExistingLocalTree = adoptExistingLocalTree,
            )
                .onSuccess { summary ->
                    feedbackState.value = "تم إنشاء مساحة العائلة «${summary.family.name}» بصلاحية ${summary.membership.role.arabicLabel}." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر إنشاء مساحة العائلة السحابية.")
                }
            isAuthBusyState.value = false
        }
    }

    fun refreshCloudFamilies() {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.refreshMyFamilies()
                .onSuccess { list ->
                    feedbackState.value = "تم تحديث مساحات العائلة (${list.size})." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تحديث المساحات من الخادم.")
                }
            isAuthBusyState.value = false
        }
    }

    fun switchActiveFamily(familyId: String) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            repo.setActiveFamily(familyId)
                .onSuccess {
                    feedbackState.value = "تم تفعيل مساحة العائلة المختارة." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تبديل مساحة العائلة.")
                }
        }
    }

    fun createFamilyInvitation(
        targetPersonId: String?,
        assignedRole: FamilyRole,
        branchRootPersonId: String?,
        maxUses: Int = 1,
        validHours: Int = 72,
    ) {
        val repo = cloudAuthRepository ?: return
        val activeFamily = uiState.value.activeFamilySpace
        if (activeFamily == null) {
            feedbackState.value = null to "أنشئ أو اختر مساحة عائلة أولاً قبل إنشاء دعوة."
            return
        }
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.createInvitation(
                familyId = activeFamily.id,
                targetPersonId = targetPersonId,
                assignedRole = assignedRole,
                branchRootPersonId = branchRootPersonId,
                maxUses = maxUses,
                validHours = validHours,
            )
                .onSuccess { created ->
                    latestGeneratedInviteState.value = created
                    feedbackState.value = "تم إنشاء رمز الدعوة: ${created.plainInviteCode}" to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر إنشاء رمز الدعوة.")
                }
            isAuthBusyState.value = false
        }
    }

    fun dismissGeneratedInvite() {
        latestGeneratedInviteState.value = null
    }

    fun revokeFamilyInvitation(invitationId: String) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.revokeInvitation(invitationId)
                .onSuccess {
                    feedbackState.value = "تم إلغاء الدعوة بنجاح." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر إلغاء الدعوة.")
                }
            isAuthBusyState.value = false
        }
    }

    fun acceptFamilyInvitation(inviteCode: String, onSuccess: (() -> Unit)? = null) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.acceptInvitation(inviteCode)
                .onSuccess { joined ->
                    feedbackState.value = "تم الانضمام إلى «${joined.family.name}» بدور ${joined.membership.role.arabicLabel}." to null
                    onSuccess?.invoke()
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر قبول رمز الدعوة.")
                }
            isAuthBusyState.value = false
        }
    }

    fun refreshCollaborationData(silent: Boolean = false) {
        val repo = cloudAuthRepository ?: return
        val activeFamily = uiState.value.activeFamilySpace ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            val membersRes = repo.refreshFamilyMembers(activeFamily.id)
            val canInvite = uiState.value.activeMembership?.let {
                it.role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN) || it.canInvite
            } == true
            val invitesRes = if (canInvite) repo.refreshFamilyInvitations(activeFamily.id) else Result.success(emptyList())
            membersRes.onFailure { err ->
                feedbackState.value = null to (err.message ?: "تعذر تحديث قائمة الأعضاء.")
            }.onSuccess {
                invitesRes.onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تحديث سجل الدعوات.")
                }.onSuccess {
                    if (!silent) {
                        feedbackState.value = "تم تحديث الأعضاء والدعوات." to null
                    }
                }
            }
            isAuthBusyState.value = false
        }
    }

    fun updateMemberLinkAndPermissions(
        memberId: String,
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ) {
        val repo = cloudAuthRepository ?: return
        val activeFamily = uiState.value.activeFamilySpace ?: return
        viewModelScope.launch {
            isAuthBusyState.value = true
            repo.updateMemberLinkAndPermissions(
                familyId = activeFamily.id,
                memberId = memberId,
                linkedPersonId = linkedPersonId,
                branchRootPersonId = branchRootPersonId,
                role = role,
                canInvite = canInvite,
                canExport = canExport,
                canManageMedia = canManageMedia,
                status = status,
            )
                .onSuccess { updated ->
                    feedbackState.value = "تم تحديث ربط وصلاحيات ${updated.displayName}." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر تحديث بيانات العضو.")
                }
            isAuthBusyState.value = false
        }
    }

    fun syncNow() {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isSyncingState.value = true
            repo.syncNow()
                .onSuccess { report ->
                    val msg = if (report.conflictCount > 0) {
                        "تمت المزامنة: رفع ${report.pushedCount}، جلب ${report.pulledCount}، مع ${report.conflictCount} تعارض يتطلب مراجعتك."
                    } else {
                        "اكتملت المزامنة (رفع ${report.pushedCount}، جلب ${report.pulledCount})."
                    }
                    feedbackState.value = msg to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذرت المزامنة السحابية.")
                }
            isSyncingState.value = false
        }
    }

    fun resolveSyncConflict(conflictId: String, choice: ConflictResolutionChoice) {
        val repo = cloudAuthRepository ?: return
        viewModelScope.launch {
            isSyncingState.value = true
            repo.resolveSyncConflict(conflictId, choice)
                .onSuccess {
                    feedbackState.value = "تم حل التعارض واعتماد «${choice.arabicLabel}»." to null
                }
                .onFailure { err ->
                    feedbackState.value = null to (err.message ?: "تعذر حل تعارض المزامنة.")
                }
            isSyncingState.value = false
        }
    }

    companion object {
        fun inferDatePrecision(raw: String?): DatePrecision {
            val trimmed = raw?.trim().orEmpty()
            return when {
                trimmed.isEmpty() -> DatePrecision.UNKNOWN
                Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(trimmed) -> DatePrecision.EXACT
                Regex("^\\d{4}-\\d{2}$").matches(trimmed) -> DatePrecision.MONTH_YEAR
                Regex("^\\d{4}$").matches(trimmed) -> DatePrecision.YEAR_ONLY
                else -> DatePrecision.APPROXIMATE
            }
        }

        fun factory(
            repository: GenealogyRepository,
            preferencesRepository: PreferencesRepository,
            mediaStorage: LocalMediaStorage,
            backupManager: ZipBackupManager? = null,
            exportManager: FamilyExportManager? = null,
            cloudAuthRepository: CloudAuthAndFamilyRepository? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return JuthoorViewModel(
                    repository = repository,
                    preferencesRepository = preferencesRepository,
                    mediaStorage = mediaStorage,
                    backupManager = backupManager,
                    exportManager = exportManager,
                    cloudAuthRepository = cloudAuthRepository,
                ) as T
            }
        }
    }
}
