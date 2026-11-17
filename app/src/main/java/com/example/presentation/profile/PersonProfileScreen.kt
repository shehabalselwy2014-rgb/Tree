package com.example.presentation.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.domain.util.HijriDateConverter
import com.example.presentation.JuthoorUiState
import com.example.presentation.components.PersonAvatar
import com.example.presentation.relationships.LinkExistingRelativeDialog
import com.example.presentation.relationships.MarriageFormDialog
import com.example.presentation.relationships.PostSaveFamilyPromptDialog
import com.example.presentation.relationships.RapidChildAddDialog
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonProfileScreen(
    personId: String,
    promptNextOnEntry: Boolean = false,
    uiState: JuthoorUiState,
    onNavigateBack: () -> Unit,
    onEditPerson: (String) -> Unit,
    onQuickAddChild: (
        father: Person?,
        mother: Person?,
        marriageId: String?,
        childFirstName: String,
        customFullName: String?,
        gender: Gender,
        birthDate: String,
        isKafala: Boolean,
        isUnconfirmed: Boolean,
    ) -> Unit,
    onSaveOrUpdateMarriage: (
        existingMarriageId: String?,
        partnerOneId: String,
        partnerTwoId: String,
        marriageDate: String,
        marriagePlace: String,
        status: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
        notes: String?,
        onSaved: (Marriage) -> Unit,
    ) -> Unit,
    onCreatePartnerAndMarry: (
        anchorPerson: Person,
        partnerFirstName: String,
        partnerFullName: String,
        partnerBirthDate: String,
        marriageDate: String,
        marriagePlace: String,
        status: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
        notes: String?,
        onSaved: (Marriage) -> Unit,
    ) -> Unit,
    onEndMarriage: (String, MarriageRecordStatus, String?, String?) -> Unit,
    onDeleteMarriage: (String) -> Unit,
    onLinkParentChild: (String, String, ParentRelationshipType, String?) -> Unit,
    onUnlinkParentChild: (String) -> Unit,
    onOpenPersonProfile: (String) -> Unit,
    onOpenTreeForPerson: (String) -> Unit,
    onDeletePerson: (String) -> Unit,
    educationFlow: Flow<List<EducationRecord>>,
    eventsFlow: Flow<List<EventRecord>>,
    onSaveEducation: (qualification: String, specialization: String, institution: String, year: Int?, notes: String?) -> Unit,
    onDeleteEducation: (String) -> Unit,
    onSaveEvent: (FamilyEventType, String, String, String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onSavePhoto: (Uri, String?, List<String>, String, String, MediaType) -> Unit,
    onDeletePhoto: (PhotoRecord) -> Unit,
) {
    BackHandler(onBack = onNavigateBack)

    val person = remember(personId, uiState.persons) {
        uiState.persons.firstOrNull { it.id == personId }
    }
    val educationList by educationFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val personEvents by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val personPhotos = remember(personId, uiState.photos) {
        uiState.photos.filter { it.personId == personId || personId in it.taggedPersonIds }
    }

    var showPostSavePrompt by remember(personId, promptNextOnEntry) { mutableStateOf(promptNextOnEntry) }
    var showRapidChildDialog by remember { mutableStateOf(false) }
    var preselectedMarriageForChild by remember { mutableStateOf<String?>(null) }
    var showMarriageDialog by remember { mutableStateOf(false) }
    var editingMarriage by remember { mutableStateOf<Marriage?>(null) }
    var endingMarriageTarget by remember { mutableStateOf<Marriage?>(null) }
    var deletingMarriageTarget by remember { mutableStateOf<Marriage?>(null) }
    var postMarriageAddChildPrompt by remember { mutableStateOf<Marriage?>(null) }
    var showLinkRelativeDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (person == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("الملف الشخصي") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("تعذر العثور على بيانات هذا الفرد.")
            }
        }
        return
    }

    val personsById = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val branchName = remember(person.familyGroupId, uiState.familyGroups) {
        person.familyGroupId?.let { id -> uiState.familyGroups.firstOrNull { it.id == id }?.groupName }
    }
    val parentLinks = remember(person.id, uiState.parentChildLinks) {
        uiState.parentChildLinks.filter { it.childId == person.id }
    }
    val father = parentLinks.firstOrNull {
        it.relationshipType == ParentRelationshipType.BIOLOGICAL_FATHER ||
            it.relationshipType == ParentRelationshipType.UNCONFIRMED_FATHER
    }?.let { personsById[it.parentId] }
    val mother = parentLinks.firstOrNull {
        it.relationshipType == ParentRelationshipType.BIOLOGICAL_MOTHER ||
            it.relationshipType == ParentRelationshipType.UNCONFIRMED_MOTHER
    }?.let { personsById[it.parentId] }

    val marriages = remember(person.id, uiState.marriages) {
        uiState.marriages.filter { it.involves(person.id) }
    }
    val childLinks = remember(person.id, uiState.parentChildLinks) {
        uiState.parentChildLinks.filter { it.parentId == person.id }
    }
    val unlinkedMarriageChildren = remember(childLinks, marriages, personsById) {
        val validMarriageIds = marriages.map { it.id }.toSet()
        childLinks
            .filter { it.marriageId == null || it.marriageId !in validMarriageIds }
            .mapNotNull { link -> personsById[link.childId]?.let { child -> link to child } }
    }
    val siblings = remember(person.id, personsById, uiState.parentChildLinks) {
        GenealogyMetricsCalculator.findSiblings(person.id, personsById, uiState.parentChildLinks)
    }
    val generation = uiState.generationByPerson[person.id] ?: 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الملف الشخصي") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEditPerson(person.id) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_edit_button"),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "تعديل البيانات")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_delete_button"),
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "حذف الفرد",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                PersonAvatar(
                                    fullName = person.fullName,
                                    firstName = person.firstName,
                                    gender = person.gender,
                                    imagePath = person.profileImagePath,
                                    size = 72.dp,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(person.fullName, style = MaterialTheme.typography.headlineSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AssistChip(onClick = {}, label = { Text(person.gender.arabicLabel) })
                                        AssistChip(onClick = {}, label = { Text("الجيل $generation") })
                                        AssistChip(onClick = {}, label = { Text(person.currentMaritalStatus.arabicLabel) })
                                    }
                                    Text(
                                        text = "المعرّف: ${person.id.take(8)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        preselectedMarriageForChild = marriages.firstOrNull {
                                            it.marriageStatus == MarriageRecordStatus.ACTIVE
                                        }?.id
                                        showRapidChildDialog = true
                                    },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("profile_add_child"),
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(" إضافة الأبناء")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        editingMarriage = null
                                        showMarriageDialog = true
                                    },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("profile_add_spouse"),
                                ) {
                                    Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(if (person.gender == Gender.MALE) " إضافة زوجة" else " إضافة زوج")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenTreeForPerson(person.id) },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("profile_open_tree"),
                                ) {
                                    Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(" الشجرة")
                                }
                                OutlinedButton(
                                    onClick = { showLinkRelativeDialog = true },
                                    enabled = uiState.persons.size >= 2,
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("profile_link_parent"),
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(" ربط والد/كافل")
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "البيانات الشخصية والمهنية") {
                        val birthFormatted = HijriDateConverter.formatDateForDisplay(
                            person.birthDate,
                            person.birthDatePrecision,
                            uiState.preferences.showHijriDates,
                        )
                        DetailRow("تاريخ الميلاد", birthFormatted)
                        person.birthPlace?.let { DetailRow("مكان الميلاد", it) }
                        if (person.isDeceased) {
                            val deathFormatted = HijriDateConverter.formatDateForDisplay(
                                person.deathDate,
                                person.deathDatePrecision,
                                uiState.preferences.showHijriDates,
                            )
                            DetailRow("تاريخ الوفاة", deathFormatted)
                            person.deathPlace?.let { DetailRow("مكان الوفاة", it) }
                        }
                        person.occupation?.let { DetailRow("المهنة / الوظيفة", it) }
                        branchName?.let { DetailRow("الفرع العائلي", it) }
                        person.biography?.let { DetailRow("نبذة شخصية", it) }
                        person.notes?.let { DetailRow("ملاحظات", it) }
                    }
                }

                item {
                    SectionCard(title = "الوالدان والأشقاء") {
                        if (father != null) {
                            PersonNavRow("الأب", father, onOpenPersonProfile)
                        } else {
                            DetailRow("الأب", "غير مسجل")
                        }
                        if (mother != null) {
                            PersonNavRow("الأم", mother, onOpenPersonProfile)
                        } else {
                            DetailRow("الأم", "غير مسجلة")
                        }
                        parentLinks.filter {
                            it.relationshipType == ParentRelationshipType.ADOPTIVE_GUARDIAN ||
                                !it.relationshipType.isConfirmed
                        }.forEach { specialLink ->
                            personsById[specialLink.parentId]?.let { relPerson ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        PersonNavRow(specialLink.relationshipType.arabicLabel, relPerson, onOpenPersonProfile)
                                    }
                                    IconButton(onClick = { onUnlinkParentChild(specialLink.id) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "فك الرابط")
                                    }
                                }
                            }
                        }
                        if (siblings.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Text("الأشقاء (${siblings.size})", style = MaterialTheme.typography.labelLarge)
                            siblings.forEach { sibling ->
                                PersonNavRow("شقيق/شقيقة", sibling, onOpenPersonProfile)
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "الزيجات والأبناء (${marriages.size})") {
                        if (marriages.isEmpty()) {
                            Text(
                                "لا توجد زيجات مسجلة بعد. يمكنك تسجيل زواج أو إضافة الأبناء مباشرة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            marriages.forEachIndexed { index, marriage ->
                                val spouse = marriage.otherPartnerId(person.id)?.let { personsById[it] }
                                val marriageChildren = childLinks
                                    .filter { it.marriageId == marriage.id }
                                    .mapNotNull { link -> personsById[link.childId]?.let { c -> link to c } }
                                val dateFormatted = HijriDateConverter.formatDateForDisplay(
                                    marriage.marriageDate,
                                    marriage.marriageDatePrecision,
                                    uiState.preferences.showHijriDates,
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().testTag("marriage_detail_card_${marriage.id}"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = spouse != null) {
                                                    spouse?.let { onOpenPersonProfile(it.id) }
                                                },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${index + 1}. ${spouse?.fullName ?: "غير محدد"}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                Text(
                                                    text = "${marriage.marriageStatus.arabicLabel} • $dateFormatted",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                marriage.marriagePlace?.let {
                                                    Text(
                                                        "مكان الزواج: $it",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                if (marriage.marriageStatus != MarriageRecordStatus.ACTIVE) {
                                                    val endInfo = listOfNotNull(
                                                        marriage.endDate?.let { "تاريخ الانتهاء: $it" },
                                                        marriage.endReason?.let { "السبب: $it" },
                                                    ).joinToString(" • ")
                                                    if (endInfo.isNotEmpty()) {
                                                        Text(
                                                            endInfo,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                        )
                                                    }
                                                }
                                            }
                                            if (spouse != null) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "فتح ملف الشريك")
                                            }
                                        }

                                        if (marriageChildren.isNotEmpty()) {
                                            HorizontalDivider()
                                            Text(
                                                "أبناء هذا الزواج (${marriageChildren.size}):",
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                            marriageChildren.forEach { (link, child) ->
                                                PersonNavRow(
                                                    "${child.gender.arabicLabel} • ${link.relationshipType.arabicLabel}",
                                                    child,
                                                    onOpenPersonProfile,
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    preselectedMarriageForChild = marriage.id
                                                    showRapidChildDialog = true
                                                },
                                                modifier = Modifier.weight(1f).testTag("add_child_for_marriage_${marriage.id}"),
                                            ) {
                                                Text("إضافة ابن")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    editingMarriage = marriage
                                                    showMarriageDialog = true
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("تعديل")
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (marriage.marriageStatus == MarriageRecordStatus.ACTIVE) {
                                                OutlinedButton(
                                                    onClick = { endingMarriageTarget = marriage },
                                                    modifier = Modifier.weight(1f).testTag("end_marriage_${marriage.id}"),
                                                ) {
                                                    Text("إنهاء الزواج")
                                                }
                                            }
                                            TextButton(
                                                onClick = { deletingMarriageTarget = marriage },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("حذف السجل", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (unlinkedMarriageChildren.isNotEmpty()) {
                    item {
                        SectionCard(title = "أبناء غير مرتبطين بزواج محدد (${unlinkedMarriageChildren.size})") {
                            unlinkedMarriageChildren.forEach { (link, child) ->
                                PersonNavRow(
                                    "${child.gender.arabicLabel} • ${link.relationshipType.arabicLabel}",
                                    child,
                                    onOpenPersonProfile,
                                )
                            }
                        }
                    }
                }

                item {
                    ProfileEducationSection(
                        educationList = educationList,
                        onAddEducation = onSaveEducation,
                        onDeleteEducation = onDeleteEducation,
                    )
                }

                item {
                    ProfileEventsSection(
                        events = personEvents,
                        showHijriDates = uiState.preferences.showHijriDates,
                        onAddEvent = onSaveEvent,
                        onDeleteEvent = onDeleteEvent,
                    )
                }

                item {
                    ProfilePhotosSection(
                        person = person,
                        allPersons = uiState.persons,
                        personPhotos = personPhotos,
                        showHijriDates = uiState.preferences.showHijriDates,
                        onSavePhoto = onSavePhoto,
                        onDeletePhoto = onDeletePhoto,
                        onOpenPersonProfile = onOpenPersonProfile,
                    )
                }
            }
        }
    }

    if (showPostSavePrompt) {
        PostSaveFamilyPromptDialog(
            personName = person.fullName,
            isMale = person.gender == Gender.MALE,
            onAddChildrenNow = {
                showPostSavePrompt = false
                preselectedMarriageForChild = marriages.firstOrNull {
                    it.marriageStatus == MarriageRecordStatus.ACTIVE
                }?.id
                showRapidChildDialog = true
            },
            onAddSpouseNow = {
                showPostSavePrompt = false
                editingMarriage = null
                showMarriageDialog = true
            },
            onDismiss = { showPostSavePrompt = false },
        )
    }

    postMarriageAddChildPrompt?.let { newMarriage ->
        val spouseName = newMarriage.otherPartnerId(person.id)?.let { personsById[it]?.fullName } ?: "الزوجة"
        AlertDialog(
            onDismissRequest = { postMarriageAddChildPrompt = null },
            title = { Text("تم تسجيل الزواج بنجاح") },
            text = {
                Text("هل تريد إضافة أسماء الأبناء لهذا الزواج (مع $spouseName) الآن وإكمال النسب تلقائياً؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        preselectedMarriageForChild = newMarriage.id
                        postMarriageAddChildPrompt = null
                        showRapidChildDialog = true
                    },
                    modifier = Modifier.testTag("post_marriage_add_children_button"),
                ) {
                    Text("إضافة الأبناء الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { postMarriageAddChildPrompt = null }) { Text("لاحقاً") }
            },
        )
    }

    if (showRapidChildDialog) {
        RapidChildAddDialog(
            anchorPerson = person,
            allPersons = uiState.persons,
            marriagesForAnchor = marriages,
            preselectedMarriageId = preselectedMarriageForChild,
            onDismiss = { showRapidChildDialog = false },
            onSaveChild = { f, m, mId, first, customFull, gen, bDate, isKafala, isUnconfirmed ->
                onQuickAddChild(f, m, mId, first, customFull, gen, bDate, isKafala, isUnconfirmed)
            },
        )
    }

    if (showMarriageDialog) {
        MarriageFormDialog(
            anchorPerson = person,
            allPersons = uiState.persons,
            editingMarriage = editingMarriage,
            onDismiss = {
                showMarriageDialog = false
                editingMarriage = null
            },
            onConfirmExisting = { partnerTwoId, date, place, status, endD, endR, notes ->
                val wasCreating = editingMarriage == null
                onSaveOrUpdateMarriage(
                    editingMarriage?.id,
                    person.id,
                    partnerTwoId,
                    date,
                    place,
                    status,
                    endD,
                    endR,
                    notes,
                ) { savedMarriage ->
                    if (wasCreating) {
                        postMarriageAddChildPrompt = savedMarriage
                    }
                }
                showMarriageDialog = false
                editingMarriage = null
            },
            onConfirmNewPartner = { pFirst, pFull, pBirth, date, place, status, endD, endR, notes ->
                onCreatePartnerAndMarry(
                    person,
                    pFirst,
                    pFull,
                    pBirth,
                    date,
                    place,
                    status,
                    endD,
                    endR,
                    notes,
                ) { savedMarriage ->
                    postMarriageAddChildPrompt = savedMarriage
                }
                showMarriageDialog = false
                editingMarriage = null
            },
        )
    }

    endingMarriageTarget?.let { target ->
        EndMarriageDialog(
            onDismiss = { endingMarriageTarget = null },
            onConfirm = { endStatus, endDate, endReason ->
                onEndMarriage(target.id, endStatus, endDate, endReason)
                endingMarriageTarget = null
            },
        )
    }

    deletingMarriageTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingMarriageTarget = null },
            title = { Text("تأكيد حذف سجل الزواج") },
            text = { Text("سيتم حذف سجل الزواج فقط مع بقاء ملفات الزوجين والأبناء محفوظة.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMarriage(target.id)
                        deletingMarriageTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("حذف السجل")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMarriageTarget = null }) { Text("إلغاء") }
            },
        )
    }

    if (showLinkRelativeDialog) {
        LinkExistingRelativeDialog(
            childPerson = person,
            allPersons = uiState.persons,
            marriages = uiState.marriages,
            onDismiss = { showLinkRelativeDialog = false },
            onConfirm = { parentId, relType, marriageId ->
                onLinkParentChild(parentId, person.id, relType, marriageId)
                showLinkRelativeDialog = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف السجل") },
            text = {
                Text(
                    "سيؤدي حذف «${person.fullName}» إلى إزالة ${marriages.size} سجل زواج و${childLinks.size} رابط أبوة دون حذف بيانات الأبناء أو الزوجات أنفسهم. هل تريد المتابعة؟"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePerson(person.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("profile_confirm_delete"),
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun EndMarriageDialog(
    onDismiss: () -> Unit,
    onConfirm: (MarriageRecordStatus, String?, String?) -> Unit,
) {
    var endStatus by remember { mutableStateOf(MarriageRecordStatus.DIVORCED) }
    var endDate by remember { mutableStateOf("") }
    var endReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل انتهاء الزواج") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سبب الانتهاء:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = endStatus == MarriageRecordStatus.DIVORCED,
                        onClick = { endStatus = MarriageRecordStatus.DIVORCED },
                        label = { Text("طلاق") },
                    )
                    FilterChip(
                        selected = endStatus == MarriageRecordStatus.ENDED_BY_DEATH,
                        onClick = { endStatus = MarriageRecordStatus.ENDED_BY_DEATH },
                        label = { Text("وفاة") },
                    )
                }
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("تاريخ الانتهاء (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endReason,
                    onValueChange = { endReason = it },
                    label = { Text("ملاحظات (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        endStatus,
                        endDate.trim().takeIf { it.isNotEmpty() },
                        endReason.trim().takeIf { it.isNotEmpty() },
                    )
                },
                modifier = Modifier.testTag("confirm_end_marriage_button"),
            ) {
                Text("حفظ الحالة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PersonNavRow(roleLabel: String, target: Person, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(target.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PersonAvatar(
            fullName = target.fullName,
            firstName = target.firstName,
            gender = target.gender,
            imagePath = target.profileImagePath,
            size = 38.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(target.fullName, style = MaterialTheme.typography.titleMedium)
            Text(roleLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "فتح الملف")
    }
}
