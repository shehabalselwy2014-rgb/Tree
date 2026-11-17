package com.example.presentation.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.SyncConflict
import com.example.presentation.JuthoorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudSyncStatusAndActionsCard(
    uiState: JuthoorUiState,
    onSyncNow: () -> Unit,
) {
    val formattedLastSync = remember(uiState.lastSyncedAtMillis) {
        uiState.lastSyncedAtMillis?.let { millis ->
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
        } ?: "لم تُجرَ مزامنة سحابية بعد"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("cloud_sync_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("المزامنة السحابية والوسائط", style = MaterialTheme.typography.titleLarge)
                }
                AssistChip(
                    onClick = {},
                    label = { Text("معلق: ${uiState.pendingSyncCount}") },
                )
            }

            Text(
                text = "آخر مزامنة: $formattedLastSync",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.unresolvedSyncConflicts.isNotEmpty()) {
                Text(
                    text = "يوجد ${uiState.unresolvedSyncConflicts.size} تعارض تزامن يتطلب اختيارك أدناه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Button(
                onClick = onSyncNow,
                enabled = !uiState.isSyncing && !uiState.isAuthBusy && uiState.isCloudBackendConfigured,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sync_now_button"),
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("جاري المزامنة...")
                } else {
                    Icon(Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("مزامنة الآن")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncConflictsCard(
    conflicts: List<SyncConflict>,
    isSyncing: Boolean,
    onResolveConflict: (String, ConflictResolutionChoice) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("sync_conflicts_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("تعارضات التزامن (${conflicts.size})", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "تم تعديل السجلات التالية على جهاز آخر بالتزامن مع تعديلك المحلي. اختر النسخة التي تريد اعتمادها:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            conflicts.forEachIndexed { index, conflict ->
                if (index > 0) HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${conflict.entityType.arabicLabel} • نسختك v${conflict.localVersion} مقابل الخادم v${conflict.serverVersion}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = conflict.conflictReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onResolveConflict(conflict.id, ConflictResolutionChoice.KEEP_LOCAL) },
                            enabled = !isSyncing,
                            modifier = Modifier.minimumInteractiveComponentSize()
                                .testTag("resolve_conflict_keep_local_${conflict.id}"),
                        ) {
                            Text(ConflictResolutionChoice.KEEP_LOCAL.arabicLabel)
                        }
                        OutlinedButton(
                            onClick = { onResolveConflict(conflict.id, ConflictResolutionChoice.USE_SERVER) },
                            enabled = !isSyncing,
                            modifier = Modifier.minimumInteractiveComponentSize()
                                .testTag("resolve_conflict_use_server_${conflict.id}"),
                        ) {
                            Text(ConflictResolutionChoice.USE_SERVER.arabicLabel)
                        }
                    }
                }
            }
        }
    }
}
