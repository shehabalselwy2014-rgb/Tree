package com.example.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.backup.BackupPreview
import com.example.presentation.JuthoorUiState
import com.example.presentation.security.PinSetupDialog
import com.example.presentation.security.isBiometricHardwareReady
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityPrivacySectionCard(
    uiState: JuthoorUiState,
    onConfigurePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onLockNow: () -> Unit,
) {
    val context = LocalContext.current
    val biometricReady = remember(context) { isBiometricHardwareReady(context) }
    var showPinDialog by remember { mutableStateOf(false) }

    if (showPinDialog) {
        PinSetupDialog(
            isUpdatingExisting = uiState.preferences.pinEnabled,
            onDismiss = { showPinDialog = false },
            onConfirmPin = { newPin ->
                onConfigurePin(newPin)
                showPinDialog = false
            },
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("settings_security_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("الخصوصية وقفل التطبيق", style = MaterialTheme.typography.titleLarge)
            }

            Text(
                text = if (uiState.preferences.pinEnabled) {
                    "قفل التطبيق مفعّل برمز PIN محمي بتجزئة SHA-256 محلية."
                } else {
                    "احمِ سجلات العائلة والصور برمز PIN محلي (مع دعم المصادقة الحيوية اختيارياً)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_setup_pin_button"),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Text(if (uiState.preferences.pinEnabled) " تغيير رمز PIN" else " تفعيل رمز PIN")
                }

                if (uiState.preferences.pinEnabled) {
                    OutlinedButton(
                        onClick = onDisablePin,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_disable_pin_button"),
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null)
                        Text(" إلغاء القفل")
                    }
                }
            }

            if (uiState.preferences.pinEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text("المصادقة الحيوية (البصمة)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (biometricReady) {
                                    "فتح التطبيق بالبصمة كخيار مساعد بجانب رمز PIN."
                                } else {
                                    "غير مهيأة على هذا الجهاز حالياً (يتوفر PIN دائماً)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = uiState.preferences.biometricEnabled,
                        enabled = biometricReady,
                        onCheckedChange = onSetBiometricEnabled,
                        modifier = Modifier.testTag("settings_biometric_switch"),
                    )
                }

                OutlinedButton(
                    onClick = onLockNow,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_lock_now_button"),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Text(" قفل التطبيق الآن")
                }
            }
        }
    }
}

@Composable
fun BackupRestoreSectionCard(
    uiState: JuthoorUiState,
    onExportBackupUri: (Uri) -> Unit,
    onInspectBackupUri: (Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissPreview: () -> Unit,
) {
    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) onExportBackupUri(uri)
    }

    val openZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onInspectBackupUri(uri)
    }

    uiState.pendingBackupPreview?.let { preview ->
        BackupPreviewConfirmationDialog(
            preview = preview,
            isBusy = uiState.isMaintenanceBusy,
            onConfirm = onConfirmRestore,
            onDismiss = onDismissPreview,
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("settings_backup_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("النسخ الاحتياطي والاستعادة (ZIP)", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "إنشاء نسخة احتياطية كاملة تشمل قاعدة البيانات والصور والوثائق بصيغة ZIP مع فحص سلامة SHA-256 قبل الاستعادة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.isMaintenanceBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportZipLauncher.launch("juthoor_backup_$stamp.zip")
                    },
                    enabled = !uiState.isMaintenanceBusy,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_export_zip_button"),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text(" تصدير نسخة ZIP")
                }

                OutlinedButton(
                    onClick = {
                        openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    enabled = !uiState.isMaintenanceBusy,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_restore_zip_button"),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Text(" استعادة من ZIP")
                }
            }
        }
    }
}

@Composable
fun FamilyExportSectionCard(
    uiState: JuthoorUiState,
    onExportPdfUri: (Uri) -> Unit,
    onExportTreePngUri: (Uri) -> Unit,
    onExportCsvUri: (Uri) -> Unit,
) {
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> if (uri != null) onExportPdfUri(uri) }

    val pngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> if (uri != null) onExportTreePngUri(uri) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) onExportCsvUri(uri) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("settings_export_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("تصدير الشجرة والسجلات", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "صدّر سجل العائلة كملف PDF شامل مع الإحصائيات ودليل الأفراد، أو صورة شجرية عالية الدقة PNG، أو جدول بيانات CSV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    pdfLauncher.launch("juthoor_family_report_$stamp.pdf")
                },
                enabled = !uiState.isMaintenanceBusy && uiState.persons.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_export_pdf_button"),
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Text(" تصدير ملف PDF شامل (تقرير + شجرة + دليل)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        pngLauncher.launch("juthoor_tree_$stamp.png")
                    },
                    enabled = !uiState.isMaintenanceBusy && uiState.persons.isNotEmpty(),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_export_png_button"),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Text(" صورة الشجرة PNG")
                }

                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        csvLauncher.launch("juthoor_persons_$stamp.csv")
                    },
                    enabled = !uiState.isMaintenanceBusy && uiState.persons.isNotEmpty(),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_export_csv_button"),
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Text(" جدول CSV")
                }
            }
        }
    }
}

@Composable
private fun BackupPreviewConfirmationDialog(
    preview: BackupPreview,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val manifest = preview.manifest
    val counts = manifest.counts
    val dateLabel = remember(manifest.exportedAtMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(manifest.exportedAtMillis))
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("تأكيد استعادة النسخة الاحتياطية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "تم التحقق من سلامة الملف وبصمة SHA-256 بنجاح.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text("العائلة: ${manifest.familyTitle}", style = MaterialTheme.typography.bodyMedium)
                Text("تاريخ النسخة: $dateLabel", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "المحتويات: ${counts.personsCount} فرد • ${counts.marriagesCount} زواج • ${counts.parentChildCount} رابط نسب • ${counts.photosCount} صورة/وثيقة (${counts.mediaFilesCount} ملف مرفق) • ${counts.eventsCount} حدث",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "تنبيه: ستُستبدل البيانات الحالية بالكامل بمحتوى هذه النسخة ضمن معاملة آمنة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("backup_confirm_restore_button"),
            ) {
                Text("تأكيد الاستعادة")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("إلغاء")
            }
        },
    )
}
