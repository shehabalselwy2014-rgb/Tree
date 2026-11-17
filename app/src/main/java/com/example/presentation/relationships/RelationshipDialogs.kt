package com.example.presentation.relationships

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.util.PatronymicNameHelper

/**
 * Rapid child entry dialog with automatic Arabic patronymic completion from the Father's name.
 * Supports adding multiple children consecutively ("حفظ وإضافة ابن آخر").
 */
@Composable
fun RapidChildAddDialog(
    anchorPerson: Person,
    allPersons: List<Person>,
    marriagesForAnchor: List<Marriage>,
    preselectedMarriageId: String? = null,
    onDismiss: () -> Unit,
    onSaveChild: (
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
) {
    val personsById = remember(allPersons) { allPersons.associateBy { it.id } }

    var selectedMarriageId by remember(preselectedMarriageId) { mutableStateOf(preselectedMarriageId) }
    val selectedMarriage = remember(selectedMarriageId, marriagesForAnchor) {
        marriagesForAnchor.firstOrNull { it.id == selectedMarriageId }
    }

    val resolvedFather: Person? = remember(anchorPerson, selectedMarriage, personsById) {
        when {
            anchorPerson.gender == Gender.MALE -> anchorPerson
            selectedMarriage != null -> selectedMarriage.otherPartnerId(anchorPerson.id)?.let { personsById[it] }
                ?.takeIf { it.gender == Gender.MALE }
            else -> null
        }
    }
    val resolvedMother: Person? = remember(anchorPerson, selectedMarriage, personsById) {
        when {
            anchorPerson.gender == Gender.FEMALE -> anchorPerson
            selectedMarriage != null -> selectedMarriage.otherPartnerId(anchorPerson.id)?.let { personsById[it] }
                ?.takeIf { it.gender == Gender.FEMALE }
            else -> null
        }
    }

    var childFirstName by remember { mutableStateOf("") }
    var customFullName by remember { mutableStateOf("") }
    var editFullNameManually by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var birthDate by remember { mutableStateOf("") }
    var isKafala by remember { mutableStateOf(false) }
    var isUnconfirmed by remember { mutableStateOf(false) }
    var addedCount by remember { mutableStateOf(0) }

    val autoCompleted = remember(childFirstName, resolvedFather) {
        PatronymicNameHelper.buildFromFather(childFirstName, resolvedFather)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("إضافة ابن / ابنة", style = MaterialTheme.typography.titleLarge)
                if (addedCount > 0) {
                    Text(
                        "تمت إضافة $addedCount بنجاح في هذه الجلسة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (marriagesForAnchor.isNotEmpty()) {
                    Text("الزواج / الأم المرتبطة:", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = selectedMarriageId == null,
                        onClick = { selectedMarriageId = null },
                        label = { Text("دون تحديد زواج") },
                    )
                    marriagesForAnchor.forEach { m ->
                        val spouseName = m.otherPartnerId(anchorPerson.id)?.let { personsById[it]?.fullName } ?: "غير محدد"
                        FilterChip(
                            selected = selectedMarriageId == m.id,
                            onClick = { selectedMarriageId = m.id },
                            label = { Text("$spouseName (${m.marriageStatus.arabicLabel})") },
                            modifier = Modifier.testTag("rapid_child_marriage_${m.id}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = childFirstName,
                    onValueChange = {
                        childFirstName = it
                        if (!editFullNameManually && !isKafala) {
                            customFullName = ""
                        }
                    },
                    label = { Text("الاسم الأول للابن/الابنة *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rapid_child_first_name"),
                )

                if (!isKafala && resolvedFather != null && childFirstName.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "الاسم الكامل التلقائي (من نسب الأب):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                autoCompleted.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("تعديل الاسم الكامل يدوياً", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = editFullNameManually, onCheckedChange = { editFullNameManually = it })
                }

                if (editFullNameManually || isKafala) {
                    OutlinedTextField(
                        value = customFullName,
                        onValueChange = { customFullName = it },
                        label = { Text("الاسم الكامل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rapid_child_custom_full"),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gender.entries.forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { gender = option },
                            label = { Text(option.arabicLabel) },
                            modifier = Modifier.testTag("rapid_child_gender_${option.name.lowercase()}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("تاريخ الميلاد (اختياري: 2015 أو 2015-03-10)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rapid_child_birth_date"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("كفالة / رعاية (غير منسوب)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = isKafala,
                        onCheckedChange = {
                            isKafala = it
                            if (it) isUnconfirmed = false
                        },
                    )
                }

                if (!isKafala) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("نسب غير مؤكد (يحتاج توثيقاً)", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = isUnconfirmed, onCheckedChange = { isUnconfirmed = it })
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        onSaveChild(
                            resolvedFather,
                            resolvedMother,
                            selectedMarriageId,
                            childFirstName,
                            customFullName.takeIf { editFullNameManually || isKafala },
                            gender,
                            birthDate,
                            isKafala,
                            isUnconfirmed,
                        )
                        addedCount++
                        childFirstName = ""
                        customFullName = ""
                        birthDate = ""
                    },
                    enabled = childFirstName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("rapid_child_save_and_add_more"),
                ) {
                    Text("حفظ وإضافة ابن آخر")
                }
                OutlinedButton(
                    onClick = {
                        onSaveChild(
                            resolvedFather,
                            resolvedMother,
                            selectedMarriageId,
                            childFirstName,
                            customFullName.takeIf { editFullNameManually || isKafala },
                            gender,
                            birthDate,
                            isKafala,
                            isUnconfirmed,
                        )
                        onDismiss()
                    },
                    enabled = childFirstName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("rapid_child_save_and_close"),
                ) {
                    Text("حفظ وإغلاق")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}

@Composable
fun MarriageFormDialog(
    anchorPerson: Person,
    allPersons: List<Person>,
    editingMarriage: Marriage? = null,
    onDismiss: () -> Unit,
    onConfirmExisting: (
        partnerTwoId: String,
        marriageDate: String,
        marriagePlace: String,
        status: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
        notes: String?,
    ) -> Unit,
    onConfirmNewPartner: (
        partnerFirstName: String,
        partnerFullName: String,
        partnerBirthDate: String,
        marriageDate: String,
        marriagePlace: String,
        status: MarriageRecordStatus,
        endDate: String?,
        endReason: String?,
        notes: String?,
    ) -> Unit,
) {
    val candidates = remember(anchorPerson, allPersons, editingMarriage) {
        if (editingMarriage != null) {
            val otherId = editingMarriage.otherPartnerId(anchorPerson.id)
            allPersons.filter { it.id == otherId }
        } else {
            allPersons.filter { it.id != anchorPerson.id && it.gender != anchorPerson.gender }
        }
    }

    var createNewPartner by remember(editingMarriage, candidates) {
        mutableStateOf(editingMarriage == null && candidates.isEmpty())
    }
    var selectedPartnerId by remember(editingMarriage, candidates) {
        mutableStateOf(
            editingMarriage?.otherPartnerId(anchorPerson.id)
                ?: candidates.firstOrNull()?.id.orEmpty()
        )
    }
    var newPartnerFirstName by remember { mutableStateOf("") }
    var newPartnerFullName by remember { mutableStateOf("") }
    var newPartnerBirthDate by remember { mutableStateOf("") }

    var marriageDate by remember(editingMarriage) { mutableStateOf(editingMarriage?.marriageDate.orEmpty()) }
    var marriagePlace by remember(editingMarriage) { mutableStateOf(editingMarriage?.marriagePlace.orEmpty()) }
    var status by remember(editingMarriage) {
        mutableStateOf(editingMarriage?.marriageStatus ?: MarriageRecordStatus.ACTIVE)
    }
    var endDate by remember(editingMarriage) { mutableStateOf(editingMarriage?.endDate.orEmpty()) }
    var endReason by remember(editingMarriage) { mutableStateOf(editingMarriage?.endReason.orEmpty()) }
    var notes by remember(editingMarriage) { mutableStateOf(editingMarriage?.notes.orEmpty()) }

    val partnerNoun = if (anchorPerson.gender == Gender.MALE) "الزوجة" else "الزوج"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editingMarriage == null) "تسجيل زواج شرعي" else "تعديل بيانات الزواج")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editingMarriage == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = createNewPartner,
                            onClick = { createNewPartner = true },
                            label = { Text("إضافة $partnerNoun جديد") },
                            modifier = Modifier.testTag("marriage_mode_new_partner"),
                        )
                        if (candidates.isNotEmpty()) {
                            FilterChip(
                                selected = !createNewPartner,
                                onClick = { createNewPartner = false },
                                label = { Text("اختيار من المسجلين") },
                                modifier = Modifier.testTag("marriage_mode_existing_partner"),
                            )
                        }
                    }
                }

                if (createNewPartner && editingMarriage == null) {
                    OutlinedTextField(
                        value = newPartnerFirstName,
                        onValueChange = { newPartnerFirstName = it },
                        label = { Text("الاسم الأول لـ$partnerNoun *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("marriage_new_partner_first"),
                    )
                    OutlinedTextField(
                        value = newPartnerFullName,
                        onValueChange = { newPartnerFullName = it },
                        label = { Text("الاسم الكامل لـ$partnerNoun (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("marriage_new_partner_full"),
                    )
                    OutlinedTextField(
                        value = newPartnerBirthDate,
                        onValueChange = { newPartnerBirthDate = it },
                        label = { Text("تاريخ ميلاد $partnerNoun (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (candidates.isNotEmpty()) {
                    Text("اختر $partnerNoun:", style = MaterialTheme.typography.labelLarge)
                    candidates.forEach { candidate ->
                        FilterChip(
                            selected = selectedPartnerId == candidate.id,
                            onClick = { if (editingMarriage == null) selectedPartnerId = candidate.id },
                            label = { Text(candidate.fullName) },
                            modifier = Modifier.testTag("marriage_candidate_${candidate.id}"),
                        )
                    }
                }

                Text("حالة الزواج:", style = MaterialTheme.typography.labelLarge)
                MarriageRecordStatus.entries.forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { status = option },
                        label = { Text(option.arabicLabel) },
                        modifier = Modifier.testTag("marriage_status_${option.name.lowercase()}"),
                    )
                }
                OutlinedTextField(
                    value = marriageDate,
                    onValueChange = { marriageDate = it },
                    label = { Text("تاريخ الزواج (اختياري: YYYY أو YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("marriage_date_input"),
                )
                OutlinedTextField(
                    value = marriagePlace,
                    onValueChange = { marriagePlace = it },
                    label = { Text("مكان الزواج (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status != MarriageRecordStatus.ACTIVE) {
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("تاريخ انتهاء الزواج (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("marriage_end_date_input"),
                    )
                    OutlinedTextField(
                        value = endReason,
                        onValueChange = { endReason = it },
                        label = { Text("ملاحظة / سبب الانتهاء (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الزواج (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val canConfirm = if (createNewPartner && editingMarriage == null) {
                newPartnerFirstName.isNotBlank() || newPartnerFullName.isNotBlank()
            } else {
                selectedPartnerId.isNotBlank()
            }
            Button(
                onClick = {
                    if (createNewPartner && editingMarriage == null) {
                        onConfirmNewPartner(
                            newPartnerFirstName,
                            newPartnerFullName,
                            newPartnerBirthDate,
                            marriageDate,
                            marriagePlace,
                            status,
                            endDate.takeIf { it.isNotBlank() },
                            endReason.takeIf { it.isNotBlank() },
                            notes.takeIf { it.isNotBlank() },
                        )
                    } else {
                        onConfirmExisting(
                            selectedPartnerId,
                            marriageDate,
                            marriagePlace,
                            status,
                            endDate.takeIf { it.isNotBlank() },
                            endReason.takeIf { it.isNotBlank() },
                            notes.takeIf { it.isNotBlank() },
                        )
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.testTag("confirm_marriage_form_button"),
            ) {
                Text(if (editingMarriage == null) "تسجيل الزواج" else "حفظ التعديل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
fun PostSaveFamilyPromptDialog(
    personName: String,
    isMale: Boolean,
    onAddChildrenNow: () -> Unit,
    onAddSpouseNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تم تسجيل «$personName»") },
        text = {
            Text(
                if (isMale) {
                    "يمكنك الآن المتابعة مباشرة بإضافة الأبناء (يُكمل الاسم تلقائياً من اسم الأب) أو تسجيل الزوجة."
                } else {
                    "يمكنك الآن تسجيل الزوج أو إضافة الأبناء مباشرة."
                }
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onAddChildrenNow,
                    modifier = Modifier.fillMaxWidth().testTag("post_save_add_children_button"),
                ) {
                    Text("إضافة الأبناء مباشرة")
                }
                OutlinedButton(
                    onClick = onAddSpouseNow,
                    modifier = Modifier.fillMaxWidth().testTag("post_save_add_spouse_button"),
                ) {
                    Text(if (isMale) "إضافة زوجة" else "إضافة زوج")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لاحقاً") }
        },
    )
}

@Composable
fun LinkExistingRelativeDialog(
    childPerson: Person,
    allPersons: List<Person>,
    marriages: List<Marriage>,
    onDismiss: () -> Unit,
    onConfirm: (parentId: String, relationshipType: ParentRelationshipType, marriageId: String?) -> Unit,
) {
    val candidates = remember(childPerson, allPersons) { allPersons.filter { it.id != childPerson.id } }
    var parentId by remember { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }
    var relType by remember { mutableStateOf(ParentRelationshipType.BIOLOGICAL_FATHER) }
    var selectedMarriageId by remember { mutableStateOf<String?>(null) }

    val availableMarriages = remember(parentId, marriages) { marriages.filter { it.involves(parentId) } }
    val personsById = remember(allPersons) { allPersons.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط والد / كافل لـ ${childPerson.firstName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("اختر الوالد / الكافل:", style = MaterialTheme.typography.labelLarge)
                candidates.forEach { c ->
                    FilterChip(
                        selected = parentId == c.id,
                        onClick = {
                            parentId = c.id
                            selectedMarriageId = null
                        },
                        label = { Text("${c.fullName} (${c.gender.arabicLabel})") },
                    )
                }
                Text("نوع العلاقة:", style = MaterialTheme.typography.labelLarge)
                ParentRelationshipType.entries.forEach { type ->
                    FilterChip(
                        selected = relType == type,
                        onClick = { relType = type },
                        label = { Text(type.arabicLabel) },
                    )
                }
                if (availableMarriages.isNotEmpty()) {
                    Text("ربط بزواج محدد (اختياري):", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = selectedMarriageId == null,
                        onClick = { selectedMarriageId = null },
                        label = { Text("دون تحديد زواج") },
                    )
                    availableMarriages.forEach { m ->
                        val spouseName = m.otherPartnerId(parentId)?.let { personsById[it]?.firstName } ?: ""
                        FilterChip(
                            selected = selectedMarriageId == m.id,
                            onClick = { selectedMarriageId = m.id },
                            label = { Text("الزواج من $spouseName (${m.marriageStatus.arabicLabel})") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parentId, relType, selectedMarriageId) },
                enabled = parentId.isNotBlank(),
                modifier = Modifier.testTag("confirm_link_relative_button"),
            ) {
                Text("ربط العلاقة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
