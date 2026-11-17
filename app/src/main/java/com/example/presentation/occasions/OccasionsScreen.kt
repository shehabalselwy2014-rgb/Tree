package com.example.presentation.occasions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.DatePrecision
import com.example.domain.model.FamilyEventType
import com.example.domain.util.HijriDateConverter
import com.example.domain.util.OccasionCategory
import com.example.presentation.JuthoorUiState

@Composable
fun OccasionsScreen(
    uiState: JuthoorUiState,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSaveEvent: (personId: String?, eventType: FamilyEventType, date: String, place: String, description: String) -> Unit,
    onOpenPersonProfile: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<OccasionCategory?>(null) }
    var showAddEventDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetNotificationsEnabled(granted)
    }

    val filteredOccasions = remember(uiState.yearOccasions, selectedCategory) {
        if (selectedCategory == null) {
            uiState.yearOccasions
        } else {
            uiState.yearOccasions.filter { it.category == selectedCategory }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEventDialog = true },
                modifier = Modifier.testTag("occasions_add_event_fab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة مناسبة عائلية")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("المناسبات والأحداث العائلية", style = MaterialTheme.typography.headlineSmall)
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column {
                                    Text("تفعيل تنبيهات المناسبات", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "إشعار اختياري بأعياد الميلاد وذكرى الزواج القادمة.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.preferences.notificationsEnabled,
                                onCheckedChange = { wantEnabled ->
                                    if (!wantEnabled) {
                                        onSetNotificationsEnabled(false)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onSetNotificationsEnabled(true)
                                        } else {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        onSetNotificationsEnabled(true)
                                    }
                                },
                                modifier = Modifier.testTag("occasions_notifications_switch"),
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("الكل (${uiState.yearOccasions.size})") },
                        )
                        OccasionCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat.arabicLabel) },
                            )
                        }
                    }
                }

                if (filteredOccasions.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Celebration,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp),
                                )
                                Text(
                                    "لا توجد مناسبات مسجلة ضمن هذا التصنيف خلال العام القادم.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredOccasions, key = { it.id }) { occasion ->
                        val dateText = HijriDateConverter.formatDateForDisplay(
                            occasion.dateLabel,
                            DatePrecision.EXACT,
                            uiState.preferences.showHijriDates,
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = occasion.relatedPersonId != null) {
                                    occasion.relatedPersonId?.let(onOpenPersonProfile)
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(occasion.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${occasion.category.arabicLabel} • $dateText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(
                                        text = occasion.subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEventDialog) {
        var eventType by remember { mutableStateOf(FamilyEventType.FAMILY_GATHERING) }
        var eventDate by remember { mutableStateOf("") }
        var eventPlace by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            title = { Text("إضافة مناسبة أو اجتماع عائلي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        label = { Text("عنوان أو وصف المناسبة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("occasion_desc_input"),
                    )
                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("التاريخ الدقيق (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("occasion_date_input"),
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
                    onClick = {
                        onSaveEvent(null, eventType, eventDate, eventPlace, description)
                        showAddEventDialog = false
                    },
                    enabled = description.isNotBlank() && eventDate.isNotBlank(),
                    modifier = Modifier.testTag("confirm_save_occasion"),
                ) {
                    Text("حفظ المناسبة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEventDialog = false }) { Text("إلغاء") }
            },
        )
    }
}
