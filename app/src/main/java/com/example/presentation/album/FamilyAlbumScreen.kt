package com.example.presentation.album

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domain.model.MediaType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import com.example.domain.util.HijriDateConverter
import com.example.presentation.JuthoorUiState
import java.io.File

@Composable
fun FamilyAlbumScreen(
    uiState: JuthoorUiState,
    onSavePhoto: (
        uri: Uri,
        primaryPersonId: String?,
        taggedPersonIds: List<String>,
        description: String,
        photoDate: String,
        mediaType: MediaType,
    ) -> Unit,
    onDeletePhoto: (PhotoRecord) -> Unit,
    onOpenPersonProfile: (String) -> Unit,
) {
    var selectedType by remember { mutableStateOf<MediaType?>(null) }
    var selectedPersonFilterId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<PhotoRecord?>(null) }
    var showPersonFilterDialog by remember { mutableStateOf(false) }

    val personsById = remember(uiState.persons) { uiState.persons.associateBy { it.id } }

    val filteredPhotos = remember(uiState.photos, selectedType, selectedPersonFilterId, searchQuery, personsById) {
        val query = searchQuery.trim().lowercase()
        uiState.photos.filter { photo ->
            val typeOk = selectedType == null || photo.mediaType == selectedType
            val personOk = selectedPersonFilterId == null ||
                photo.personId == selectedPersonFilterId ||
                selectedPersonFilterId in photo.taggedPersonIds
            val queryOk = if (query.isEmpty()) {
                true
            } else {
                val descMatch = photo.description?.lowercase()?.contains(query) == true
                val personMatch = photo.taggedPersonIds.any { id ->
                    personsById[id]?.fullName?.lowercase()?.contains(query) == true
                } || (photo.personId?.let { personsById[it]?.fullName?.lowercase()?.contains(query) } == true)
                descMatch || personMatch
            }
            typeOk && personOk && queryOk
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPhotoDialog = true },
                modifier = Modifier.testTag("album_add_photo_fab"),
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "إضافة صورة أو وثيقة")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ألبوم العائلة والوثائق (${uiState.photos.size})",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text("ابحث في الوصف أو أسماء الأفراد الموسومين") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("album_search_input"),
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = selectedType == null,
                                onClick = { selectedType = null },
                                label = { Text("الكل") },
                            )
                            MediaType.entries.forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectedType = if (selectedType == type) null else type },
                                    label = { Text(type.arabicLabel) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val filterPersonName = selectedPersonFilterId?.let { personsById[it]?.fullName }
                            OutlinedButton(
                                onClick = { showPersonFilterDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(filterPersonName?.let { "تصفية: $it" } ?: "تصفية حسب الفرد")
                            }
                            if (selectedPersonFilterId != null) {
                                TextButton(onClick = { selectedPersonFilterId = null }) {
                                    Text("مسح التصفية")
                                }
                            }
                        }
                    }
                }

                if (filteredPhotos.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = if (uiState.photos.isEmpty()) {
                                        "الألبوم فارغ حالياً. اضغط على زر الإضافة لحفظ الصور الشخصية والصور الجماعية ووثائق الأجداد محلياً."
                                    } else {
                                        "لا توجد صور مطابقة لمعايير التصفية الحالية."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredPhotos, key = { it.id }) { photo ->
                        AlbumPhotoCard(
                            photo = photo,
                            personsById = personsById,
                            showHijriDates = uiState.preferences.showHijriDates,
                            onClick = { viewingPhoto = photo },
                        )
                    }
                }
            }
        }
    }

    if (showAddPhotoDialog) {
        AddAlbumPhotoDialog(
            persons = uiState.persons,
            initialTaggedPersonId = selectedPersonFilterId,
            onDismiss = { showAddPhotoDialog = false },
            onConfirm = { uri, primaryId, taggedIds, desc, date, mediaType ->
                onSavePhoto(uri, primaryId, taggedIds, desc, date, mediaType)
                showAddPhotoDialog = false
            },
        )
    }

    viewingPhoto?.let { photo ->
        PhotoFullscreenViewerDialog(
            photo = photo,
            personsById = personsById,
            showHijriDates = uiState.preferences.showHijriDates,
            onDismiss = { viewingPhoto = null },
            onOpenPersonProfile = { pid ->
                viewingPhoto = null
                onOpenPersonProfile(pid)
            },
            onDelete = {
                onDeletePhoto(photo)
                viewingPhoto = null
            },
        )
    }

    if (showPersonFilterDialog) {
        AlertDialog(
            onDismissRequest = { showPersonFilterDialog = false },
            title = { Text("تصفية الألبوم حسب الفرد") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    items(uiState.persons, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPersonFilterId = p.id
                                    showPersonFilterDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(p.fullName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedPersonFilterId = null
                    showPersonFilterDialog = false
                }) {
                    Text("إظهار الجميع")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPersonFilterDialog = false }) { Text("إغلاق") }
            },
        )
    }
}

@Composable
private fun AlbumPhotoCard(
    photo: PhotoRecord,
    personsById: Map<String, Person>,
    showHijriDates: Boolean,
    onClick: () -> Unit,
) {
    val file = File(photo.filePath)
    val taggedNames = (photo.taggedPersonIds + listOfNotNull(photo.personId))
        .distinct()
        .mapNotNull { personsById[it]?.firstName }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("album_photo_card_${photo.id}"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = photo.description ?: photo.mediaType.arabicLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = photo.mediaType.arabicLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    val dateText = HijriDateConverter.formatDateForDisplay(
                        photo.photoDate,
                        photo.photoDatePrecision,
                        showHijriDates,
                    )
                    if (dateText != "غير معروف") {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                photo.description?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (taggedNames.isNotEmpty()) {
                    Text(
                        text = "الأفراد: ${taggedNames.joinToString("، ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AddAlbumPhotoDialog(
    persons: List<Person>,
    initialTaggedPersonId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        uri: Uri,
        primaryPersonId: String?,
        taggedPersonIds: List<String>,
        description: String,
        photoDate: String,
        mediaType: MediaType,
    ) -> Unit,
) {
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var description by remember { mutableStateOf("") }
    var photoDate by remember { mutableStateOf("") }
    var mediaType by remember {
        mutableStateOf(
            if (initialTaggedPersonId != null) MediaType.PERSON_PORTRAIT else MediaType.GROUP_PHOTO
        )
    }
    var taggedIds by remember {
        mutableStateOf(listOfNotNull(initialTaggedPersonId).toSet())
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صورة أو وثيقة عائلية") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pick_album_image_button"),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Text(if (pickedUri == null) " اختيار صورة من الجهاز" else " تم اختيار الصورة (تغيير)")
                    }
                    if (pickedUri != null) {
                        AsyncImage(
                            model = pickedUri,
                            contentDescription = "معاينة الصورة المختارة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .padding(top = 6.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                }
                item {
                    Text("نوع المادة:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MediaType.entries.forEach { type ->
                            FilterChip(
                                selected = mediaType == type,
                                onClick = { mediaType = type },
                                label = { Text(type.arabicLabel) },
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("وصف الصورة أو الوثيقة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("album_description_input"),
                    )
                }
                item {
                    OutlinedTextField(
                        value = photoDate,
                        onValueChange = { photoDate = it },
                        label = { Text("التاريخ (YYYY-MM-DD أو YYYY اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (persons.isNotEmpty()) {
                    item {
                        Text("وسم الأفراد الظاهرين:", style = MaterialTheme.typography.labelLarge)
                    }
                    items(persons, key = { it.id }) { person ->
                        val checked = person.id in taggedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    taggedIds = if (checked) taggedIds - person.id else taggedIds + person.id
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    taggedIds = if (isChecked) taggedIds + person.id else taggedIds - person.id
                                },
                            )
                            Text(person.fullName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = pickedUri ?: return@Button
                    val orderedTags = taggedIds.toList()
                    onConfirm(
                        uri,
                        initialTaggedPersonId ?: orderedTags.firstOrNull(),
                        orderedTags,
                        description,
                        photoDate,
                        mediaType,
                    )
                },
                enabled = pickedUri != null,
                modifier = Modifier.testTag("confirm_save_album_photo"),
            ) {
                Text("حفظ في الألبوم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoFullscreenViewerDialog(
    photo: PhotoRecord,
    personsById: Map<String, Person>,
    showHijriDates: Boolean,
    onDismiss: () -> Unit,
    onOpenPersonProfile: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val taggedIds = (photo.taggedPersonIds + listOfNotNull(photo.personId)).distinct()
    val file = File(photo.filePath)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق العارض")
                    }
                    Text(photo.mediaType.arabicLabel, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "حذف الصورة",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = photo.description ?: photo.mediaType.arabicLabel,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("تعذر العثور على ملف الصورة المحلي.")
                    }
                }

                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        photo.description?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium)
                        }
                        val dateText = HijriDateConverter.formatDateForDisplay(
                            photo.photoDate,
                            photo.photoDatePrecision,
                            showHijriDates,
                        )
                        if (dateText != "غير معروف") {
                            Text(
                                text = "التاريخ: $dateText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (taggedIds.isNotEmpty()) {
                            Text("الأفراد الموسومون:", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                taggedIds.forEach { pid ->
                                    personsById[pid]?.let { rel ->
                                        AssistChip(
                                            onClick = { onOpenPersonProfile(rel.id) },
                                            label = { Text(rel.fullName) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الصورة") },
            text = { Text("هل تريد حذف هذه الصورة من ألبوم العائلة نهائياً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") }
            },
        )
    }
}
