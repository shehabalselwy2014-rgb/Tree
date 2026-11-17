package com.example.presentation.profile

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.MediaType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.util.HijriDateConverter
import com.example.presentation.album.AddAlbumPhotoDialog
import com.example.presentation.album.PhotoFullscreenViewerDialog
import java.io.File

@Composable
fun ProfileEducationSection(
    educationList: List<EducationRecord>,
    onAddEducation: (qualification: String, specialization: String, institution: String, year: Int?, notes: String?) -> Unit,
    onDeleteEducation: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("المؤهلات العلمية (${educationList.size})", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_add_education_button"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" إضافة مؤهل")
                }
            }
            if (educationList.isEmpty()) {
                Text(
                    "لا توجد مؤهلات دراسية مسجلة بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                educationList.forEach { edu ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(edu.qualification, style = MaterialTheme.typography.titleMedium)
                                val sub = listOfNotNull(
                                    edu.specialization,
                                    edu.institution,
                                    edu.graduationYear?.toString(),
                                ).joinToString(" • ")
                                if (sub.isNotEmpty()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(
                                onClick = { onDeleteEducation(edu.id) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف المؤهل")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEducationDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { q, spec, inst, yr, notes ->
                onAddEducation(q, spec, inst, yr, notes)
                showAddDialog = false
            },
        )
    }
}

@Composable
fun ProfileEventsSection(
    events: List<EventRecord>,
    showHijriDates: Boolean,
    onAddEvent: (FamilyEventType, String, String, String) -> Unit,
    onDeleteEvent: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("الأحداث والمحطات المهمة (${events.size})", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_add_event_button"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" إضافة حدث")
                }
            }
            if (events.isEmpty()) {
                Text(
                    "لا توجد أحداث خاصة مسجلة بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                events.forEach { evt ->
                    val dateFormatted = HijriDateConverter.formatDateForDisplay(
                        evt.eventDate,
                        evt.eventDatePrecision,
                        showHijriDates,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(evt.eventType.arabicLabel, style = MaterialTheme.typography.titleMedium)
                                evt.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                                val meta = listOfNotNull(
                                    dateFormatted.takeIf { it != "غير معروف" },
                                    evt.eventPlace,
                                ).joinToString(" • ")
                                if (meta.isNotEmpty()) {
                                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(
                                onClick = { onDeleteEvent(evt.id) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الحدث")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPersonEventDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { type, date, place, desc ->
                onAddEvent(type, date, place, desc)
                showAddDialog = false
            },
        )
    }
}

@Composable
fun ProfilePhotosSection(
    person: Person,
    allPersons: List<Person>,
    personPhotos: List<PhotoRecord>,
    showHijriDates: Boolean,
    onSavePhoto: (Uri, String?, List<String>, String, String, MediaType) -> Unit,
    onDeletePhoto: (PhotoRecord) -> Unit,
    onOpenPersonProfile: (String) -> Unit,
) {
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<PhotoRecord?>(null) }
    val personsById = remember(allPersons) { allPersons.associateBy { it.id } }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("الصور والوثائق (${personPhotos.size})", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showAddPhotoDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_add_photo_button"),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" إضافة صورة")
                }
            }
            if (personPhotos.isEmpty()) {
                Text(
                    "لا توجد صور أو وثائق مرتبطة بهذا الفرد بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    personPhotos.forEach { photo ->
                        val file = File(photo.filePath)
                        Column(
                            modifier = Modifier
                                .width(132.dp)
                                .clickable { viewingPhoto = photo },
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = photo.description ?: photo.mediaType.arabicLabel,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(132.dp)
                                        .height(100.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                )
                            }
                            Text(
                                text = photo.description ?: photo.mediaType.arabicLabel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddPhotoDialog) {
        AddAlbumPhotoDialog(
            persons = allPersons,
            initialTaggedPersonId = person.id,
            onDismiss = { showAddPhotoDialog = false },
            onConfirm = { uri, primaryId, taggedIds, desc, date, mediaType ->
                onSavePhoto(uri, primaryId ?: person.id, (taggedIds + person.id).distinct(), desc, date, mediaType)
                showAddPhotoDialog = false
            },
        )
    }

    viewingPhoto?.let { photo ->
        PhotoFullscreenViewerDialog(
            photo = photo,
            personsById = personsById,
            showHijriDates = showHijriDates,
            onDismiss = { viewingPhoto = null },
            onOpenPersonProfile = { pid ->
                viewingPhoto = null
                if (pid != person.id) onOpenPersonProfile(pid)
            },
            onDelete = {
                onDeletePhoto(photo)
                viewingPhoto = null
            },
        )
    }
}

@Composable
private fun AddEducationDialog(
    onDismiss: () -> Unit,
    onConfirm: (qualification: String, specialization: String, institution: String, year: Int?, notes: String?) -> Unit,
) {
    var qualification by remember { mutableStateOf("") }
    var specialization by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مؤهل علمي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qualification,
                    onValueChange = { qualification = it },
                    label = { Text("المؤهل (مثل: بكالوريوس / ماجستير)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edu_qualification_input"),
                )
                OutlinedTextField(
                    value = specialization,
                    onValueChange = { specialization = it },
                    label = { Text("التخصص (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edu_specialization_input"),
                )
                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text("الجامعة / المؤسسة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edu_institution_input"),
                )
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it },
                    label = { Text("سنة التخرج (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edu_year_input"),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
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
                        qualification,
                        specialization,
                        institution,
                        yearText.trim().toIntOrNull(),
                        notes.takeIf { it.isNotBlank() },
                    )
                },
                enabled = qualification.isNotBlank(),
                modifier = Modifier.testTag("confirm_save_education"),
            ) {
                Text("حفظ المؤهل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
private fun AddPersonEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (FamilyEventType, String, String, String) -> Unit,
) {
    var eventType by remember { mutableStateOf(FamilyEventType.GRADUATION) }
    var eventDate by remember { mutableStateOf("") }
    var eventPlace by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حدث أو محطة مهمة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نوع الحدث:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FamilyEventType.entries.forEach { t ->
                        FilterChip(
                            selected = eventType == t,
                            onClick = { eventType = t },
                            label = { Text(t.arabicLabel) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف الحدث") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("event_description_input"),
                )
                OutlinedTextField(
                    value = eventDate,
                    onValueChange = { eventDate = it },
                    label = { Text("التاريخ (YYYY-MM-DD أو YYYY)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("event_date_input"),
                )
                OutlinedTextField(
                    value = eventPlace,
                    onValueChange = { eventPlace = it },
                    label = { Text("المكان (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(eventType, eventDate, eventPlace, description) },
                enabled = description.isNotBlank() || eventDate.isNotBlank(),
                modifier = Modifier.testTag("confirm_save_event"),
            ) {
                Text("حفظ الحدث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
