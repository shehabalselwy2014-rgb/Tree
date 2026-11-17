package com.example.presentation.persons

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.DatePrecision
import com.example.domain.model.Gender
import com.example.domain.model.Person
import com.example.presentation.JuthoorUiState
import com.example.presentation.JuthoorViewModel
import com.example.presentation.components.PersonAvatar
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonScreen(
    uiState: JuthoorUiState,
    editingPersonId: String?,
    prefilledParentId: String?,
    onCopyImage: suspend (android.net.Uri) -> Result<String>,
    onSave: (
        person: Person,
        fatherId: String?,
        motherId: String?,
        marriageId: String?,
    ) -> Unit,
    onNavigateBack: () -> Unit,
) {
    BackHandler(onBack = onNavigateBack)

    val existingPerson = remember(editingPersonId, uiState.persons) {
        editingPersonId?.let { id -> uiState.persons.firstOrNull { it.id == id } }
    }
    val prefilledParent = remember(prefilledParentId, uiState.persons) {
        prefilledParentId?.let { id -> uiState.persons.firstOrNull { it.id == id } }
    }

    var firstName by remember(existingPerson) { mutableStateOf(existingPerson?.firstName.orEmpty()) }
    var middleName by remember(existingPerson) { mutableStateOf(existingPerson?.middleName.orEmpty()) }
    var familyName by remember(existingPerson) { mutableStateOf(existingPerson?.familyName.orEmpty()) }
    var customFullName by remember(existingPerson) { mutableStateOf(existingPerson?.fullName.orEmpty()) }
    var gender by remember(existingPerson) { mutableStateOf(existingPerson?.gender ?: Gender.MALE) }
    var birthDate by remember(existingPerson) { mutableStateOf(existingPerson?.birthDate.orEmpty()) }
    var birthPrecision by remember(existingPerson) {
        mutableStateOf(existingPerson?.birthDatePrecision ?: DatePrecision.UNKNOWN)
    }
    var birthPlace by remember(existingPerson) { mutableStateOf(existingPerson?.birthPlace.orEmpty()) }
    var isDeceased by remember(existingPerson) { mutableStateOf(existingPerson?.isDeceased ?: false) }
    var deathDate by remember(existingPerson) { mutableStateOf(existingPerson?.deathDate.orEmpty()) }
    var deathPlace by remember(existingPerson) { mutableStateOf(existingPerson?.deathPlace.orEmpty()) }
    var occupation by remember(existingPerson) { mutableStateOf(existingPerson?.occupation.orEmpty()) }
    var biography by remember(existingPerson) { mutableStateOf(existingPerson?.biography.orEmpty()) }
    var notes by remember(existingPerson) { mutableStateOf(existingPerson?.notes.orEmpty()) }
    var selectedGroupId by remember(existingPerson) { mutableStateOf(existingPerson?.familyGroupId) }
    var profileImagePath by remember(existingPerson) { mutableStateOf(existingPerson?.profileImagePath) }

    var selectedFatherId by remember(prefilledParent) {
        mutableStateOf(if (prefilledParent?.gender == Gender.MALE) prefilledParent.id else null)
    }
    var selectedMotherId by remember(prefilledParent) {
        mutableStateOf(if (prefilledParent?.gender == Gender.FEMALE) prefilledParent.id else null)
    }
    var selectedMarriageId by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    onCopyImage(uri).onSuccess { savedPath ->
                        profileImagePath = savedPath
                    }
                }
            }
        },
    )

    val maleCandidates = remember(uiState.persons, existingPerson) {
        uiState.persons.filter { it.gender == Gender.MALE && it.id != existingPerson?.id }
    }
    val femaleCandidates = remember(uiState.persons, existingPerson) {
        uiState.persons.filter { it.gender == Gender.FEMALE && it.id != existingPerson?.id }
    }
    val relevantMarriages = remember(selectedFatherId, selectedMotherId, uiState.marriages) {
        uiState.marriages.filter { m ->
            (selectedFatherId != null && m.involves(selectedFatherId!!)) ||
                (selectedMotherId != null && m.involves(selectedMotherId!!))
        }
    }
    val personNames = remember(uiState.persons) { uiState.persons.associate { it.id to it.fullName } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (existingPerson == null) "إضافة فرد جديد" else "تعديل بيانات الفرد")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PersonAvatar(
                        fullName = customFullName.ifBlank { firstName },
                        firstName = firstName,
                        gender = gender,
                        imagePath = profileImagePath,
                        size = 72.dp,
                    )
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("pick_person_photo_button"),
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                        Text(" اختيار صورة شخصية")
                    }
                }

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("الاسم الأول *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_first_name"),
                )
                OutlinedTextField(
                    value = middleName,
                    onValueChange = { middleName = it },
                    label = { Text("اسم الأب / الوسط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_middle_name"),
                )
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text("اسم العائلة / اللقب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_family_name"),
                )
                OutlinedTextField(
                    value = customFullName,
                    onValueChange = { customFullName = it },
                    label = { Text("الاسم الكامل (يُكوّن تلقائياً إذا تُرك فارغاً)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_full_name"),
                )

                Text("الجنس", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gender.entries.forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { gender = option },
                            label = { Text(option.arabicLabel) },
                            modifier = Modifier.testTag("form_gender_${option.name.lowercase()}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = birthDate,
                    onValueChange = {
                        birthDate = it
                        birthPrecision = JuthoorViewModel.inferDatePrecision(it)
                    },
                    label = { Text("تاريخ الميلاد (اختياري: 1985 أو 1985-04-20)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_birth_date"),
                )
                if (birthDate.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(DatePrecision.EXACT, DatePrecision.YEAR_ONLY, DatePrecision.APPROXIMATE).forEach { prec ->
                            FilterChip(
                                selected = birthPrecision == prec,
                                onClick = { birthPrecision = prec },
                                label = { Text(prec.arabicLabel) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = birthPlace,
                    onValueChange = { birthPlace = it },
                    label = { Text("مكان الميلاد (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("تسجيل حالة الوفاة (إن وجدت)", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = isDeceased,
                                onCheckedChange = { isDeceased = it },
                                modifier = Modifier.testTag("form_deceased_switch"),
                            )
                        }
                        if (isDeceased) {
                            OutlinedTextField(
                                value = deathDate,
                                onValueChange = { deathDate = it },
                                label = { Text("تاريخ الوفاة (YYYY أو YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("form_death_date"),
                            )
                            OutlinedTextField(
                                value = deathPlace,
                                onValueChange = { deathPlace = it },
                                label = { Text("مكان الوفاة (اختياري)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = occupation,
                    onValueChange = { occupation = it },
                    label = { Text("المهنة / الوظيفة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_occupation"),
                )

                if (uiState.familyGroups.isNotEmpty()) {
                    Text("الفرع العائلي (اختياري)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedGroupId == null,
                            onClick = { selectedGroupId = null },
                            label = { Text("بدون فرع") },
                        )
                        uiState.familyGroups.forEach { group ->
                            FilterChip(
                                selected = selectedGroupId == group.id,
                                onClick = { selectedGroupId = group.id },
                                label = { Text(group.groupName) },
                            )
                        }
                    }
                }

                if (existingPerson == null && (maleCandidates.isNotEmpty() || femaleCandidates.isNotEmpty())) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ربط سريع بالوالدين (اختياري)", style = MaterialTheme.typography.titleMedium)
                            if (maleCandidates.isNotEmpty()) {
                                Text("الأب:", style = MaterialTheme.typography.labelMedium)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = selectedFatherId == null,
                                        onClick = { selectedFatherId = null },
                                        label = { Text("غير محدد") },
                                    )
                                    maleCandidates.take(6).forEach { father ->
                                        FilterChip(
                                            selected = selectedFatherId == father.id,
                                            onClick = { selectedFatherId = father.id },
                                            label = { Text(father.fullName) },
                                        )
                                    }
                                }
                            }
                            if (femaleCandidates.isNotEmpty()) {
                                Text("الأم:", style = MaterialTheme.typography.labelMedium)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = selectedMotherId == null,
                                        onClick = { selectedMotherId = null },
                                        label = { Text("غير محددة") },
                                    )
                                    femaleCandidates.take(6).forEach { mother ->
                                        FilterChip(
                                            selected = selectedMotherId == mother.id,
                                            onClick = { selectedMotherId = mother.id },
                                            label = { Text(mother.fullName) },
                                        )
                                    }
                                }
                            }
                            if (relevantMarriages.isNotEmpty()) {
                                Text("ربط بزواج مسجل:", style = MaterialTheme.typography.labelMedium)
                                relevantMarriages.forEach { m ->
                                    val label = "${personNames[m.partnerOneId] ?: ""} × ${personNames[m.partnerTwoId] ?: ""}"
                                    FilterChip(
                                        selected = selectedMarriageId == m.id,
                                        onClick = {
                                            selectedMarriageId = if (selectedMarriageId == m.id) null else m.id
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = biography,
                    onValueChange = { biography = it },
                    label = { Text("نبذة شخصية (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إضافية (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val computedFull = customFullName.trim().ifBlank {
                            listOf(firstName, middleName, familyName)
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(" ")
                        }
                        val resolvedDeathPrecision = if (!isDeceased) {
                            DatePrecision.UNKNOWN
                        } else if (deathDate.isBlank()) {
                            DatePrecision.APPROXIMATE
                        } else {
                            JuthoorViewModel.inferDatePrecision(deathDate)
                        }
                        val person = Person(
                            id = existingPerson?.id ?: UUID.randomUUID().toString(),
                            fullName = computedFull,
                            firstName = firstName.trim(),
                            middleName = middleName.trim().takeIf { it.isNotEmpty() },
                            familyName = familyName.trim().takeIf { it.isNotEmpty() },
                            gender = gender,
                            birthDate = birthDate.trim().takeIf { it.isNotEmpty() },
                            birthDatePrecision = if (birthDate.isBlank()) DatePrecision.UNKNOWN else birthPrecision,
                            birthPlace = birthPlace.trim().takeIf { it.isNotEmpty() },
                            deathDate = if (isDeceased) deathDate.trim().takeIf { it.isNotEmpty() } else null,
                            deathDatePrecision = resolvedDeathPrecision,
                            deathPlace = if (isDeceased) deathPlace.trim().takeIf { it.isNotEmpty() } else null,
                            biography = biography.trim().takeIf { it.isNotEmpty() },
                            occupation = occupation.trim().takeIf { it.isNotEmpty() },
                            currentMaritalStatus = existingPerson?.currentMaritalStatus
                                ?: com.example.domain.model.MaritalStatus.SINGLE,
                            profileImagePath = profileImagePath,
                            familyGroupId = selectedGroupId,
                            notes = notes.trim().takeIf { it.isNotEmpty() },
                            createdAt = existingPerson?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                        onSave(person, selectedFatherId, selectedMotherId, selectedMarriageId)
                    },
                    enabled = firstName.isNotBlank() || customFullName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("form_save_person_button"),
                ) {
                    Text(if (existingPerson == null) "حفظ الفرد" else "تحديث البيانات")
                }
            }
        }
    }
}
