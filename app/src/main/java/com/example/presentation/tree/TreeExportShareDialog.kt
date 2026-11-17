package com.example.presentation.tree

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TreeExportShareDialog(
    familyTitle: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onExportTreePngUri: (Uri) -> Unit,
    onExportPdfUri: (Uri) -> Unit,
    onShareTreePng: (() -> Unit)? = null,
    onShareTreePdf: (() -> Unit)? = null,
) {
    val pngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            onExportTreePngUri(uri)
            onDismiss()
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            onExportPdfUri(uri)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصدير ومشاركة شجرة العائلة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "صدّر مخطط $familyTitle بصيغة صورة PNG عالية الدقة أو مستند PDF شامل للمشاركة والطباعة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        pngLauncher.launch("juthoor_tree_$stamp.png")
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tree_export_save_png_button"),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ الشجرة كصورة (PNG)")
                }

                FilledTonalButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        pdfLauncher.launch("juthoor_family_report_$stamp.pdf")
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tree_export_save_pdf_button"),
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ الشجرة والسجل كمستند (PDF)")
                }

                if (onShareTreePng != null || onShareTreePdf != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }

                if (onShareTreePng != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShareTreePng()
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("tree_share_png_button"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("مشاركة صورة الشجرة مباشرة (PNG)")
                    }
                }

                if (onShareTreePdf != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShareTreePdf()
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("tree_share_pdf_button"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("مشاركة مستند الشجرة مباشرة (PDF)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}
