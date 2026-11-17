package com.example.presentation.settings

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.domain.model.ThemeMode
import com.example.presentation.JuthoorUiState

@Composable
fun SettingsScreen(
    uiState: JuthoorUiState,
    onUpdateFamilyIdentity: (String, String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetShowHijri: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onAddFamilyGroup: (String, String) -> Unit,
    onDeleteFamilyGroup: (String) -> Unit,
    onConfigurePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onExportBackupUri: (Uri) -> Unit,
    onInspectBackupUri: (Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissBackupPreview: () -> Unit,
    onExportPdfUri: (Uri) -> Unit,
    onExportTreePngUri: (Uri) -> Unit,
    onExportCsvUri: (Uri) -> Unit,
    onOpenFamilyAccount: () -> Unit = {},
    onOpenFamilyMembers: () -> Unit = {},
    onSyncNow: () -> Unit = {},
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetNotificationsEnabled(granted)
    }
    var familyTitle by remember(uiState.preferences.familyTitle) {
        mutableStateOf(uiState.preferences.familyTitle)
    }
    var familyMotto by remember(uiState.preferences.familyMotto) {
        mutableStateOf(uiState.preferences.familyMotto)
    }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 800.dp).testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_cloud_account_card"),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("الحساب السحابي ومساحة العائلة", style = MaterialTheme.typography.titleLarge)
                        }
                        val statusSummary = when {
                            uiState.authSession != null && uiState.activeFamilySpace != null ->
                                "متصل: ${uiState.authSession.displayName} • ${uiState.activeFamilySpace.name} (${uiState.activeMembership?.role?.arabicLabel ?: "عضو"})"
                            uiState.authSession != null ->
                                "متصل بحساب ${uiState.authSession.displayName} • أنشئ مساحة العائلة أو انضم إليها"
                            else ->
                                "الوضع المحلي نشط. يمكنك تسجيل الدخول أو إنشاء مساحة عائلة سحابية مشتركة."
                        }
                        Text(
                            text = statusSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onOpenFamilyAccount,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("open_family_account_button"),
                        ) {
                            Text(if (uiState.authSession != null) "إدارة الحساب ومساحة العائلة" else "تسجيل الدخول / إنشاء مساحة عائلة")
                        }
                        if (uiState.authSession != null &&
                            uiState.activeFamilySpace != null &&
                            uiState.activeFamilySpace.id != com.example.domain.model.MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID
                        ) {
                            OutlinedButton(
                                onClick = onOpenFamilyMembers,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("open_family_members_from_settings"),
                            ) {
                                Text("الدعوات والأعضاء وربط الشجرة")
                            }
                            OutlinedButton(
                                onClick = onSyncNow,
                                enabled = !uiState.isSyncing && uiState.isCloudBackendConfigured,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_sync_now_button"),
                            ) {
                                Text(
                                    if (uiState.isSyncing) {
                                        "جاري المزامنة..."
                                    } else {
                                        "مزامنة الآن (معلق: ${uiState.pendingSyncCount})"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("هوية العائلة", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = familyTitle,
                            onValueChange = { familyTitle = it },
                            label = { Text("اسم العائلة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_family_title"),
                        )
                        OutlinedTextField(
                            value = familyMotto,
                            onValueChange = { familyMotto = it },
                            label = { Text("العبارة التعريفية") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onUpdateFamilyIdentity(familyTitle, familyMotto) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_save_identity"),
                        ) {
                            Text("حفظ هوية العائلة")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("المظهر والتقويم", style = MaterialTheme.typography.titleLarge)
                        Text("سمة العرض", style = MaterialTheme.typography.labelLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.preferences.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = { Text(mode.arabicLabel) },
                                    modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}"),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("عرض التاريخ الهجري بجانب الميلادي", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "إظهار التحويل الهجري التلقائي في السجلات والملفات الشخصية.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = uiState.preferences.showHijriDates,
                                onCheckedChange = onSetShowHijri,
                                modifier = Modifier.testTag("settings_hijri_switch"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تذكيرات المناسبات العائلية", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "إشعار اختياري بأعياد الميلاد وذكرى الزواج.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                                modifier = Modifier.testTag("settings_notifications_switch"),
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الفروع العائلية (${uiState.familyGroups.size})", style = MaterialTheme.typography.titleLarge)
                        uiState.familyGroups.forEach { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.groupName, style = MaterialTheme.typography.titleMedium)
                                    group.description?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteFamilyGroup(group.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الفرع")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text("اسم فرع جديد") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_new_branch_name"),
                        )
                        OutlinedTextField(
                            value = newGroupDesc,
                            onValueChange = { newGroupDesc = it },
                            label = { Text("وصف الفرع (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onAddFamilyGroup(newGroupName, newGroupDesc)
                                    newGroupName = ""
                                    newGroupDesc = ""
                                }
                            },
                            enabled = newGroupName.isNotBlank(),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_add_branch_button"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(" إضافة فرع عائلي")
                        }
                    }
                }
            }

            item {
                SecurityPrivacySectionCard(
                    uiState = uiState,
                    onConfigurePin = onConfigurePin,
                    onDisablePin = onDisablePin,
                    onSetBiometricEnabled = onSetBiometricEnabled,
                    onLockNow = onLockNow,
                )
            }

            item {
                BackupRestoreSectionCard(
                    uiState = uiState,
                    onExportBackupUri = onExportBackupUri,
                    onInspectBackupUri = onInspectBackupUri,
                    onConfirmRestore = onConfirmRestore,
                    onDismissPreview = onDismissBackupPreview,
                )
            }

            item {
                FamilyExportSectionCard(
                    uiState = uiState,
                    onExportPdfUri = onExportPdfUri,
                    onExportTreePngUri = onExportTreePngUri,
                    onExportCsvUri = onExportCsvUri,
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("معلومات التطبيق والخصوصية", style = MaterialTheme.typography.titleMedium)
                        Text("جِذور • الإصدار 0.8.0 (المرحلة الثامنة)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "تُحفظ كافة البيانات والصور محلياً داخل مساحة التطبيق الخاصة دون إرسال أي بيانات لخوادم خارجية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
