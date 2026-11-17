package com.example.presentation.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.presentation.JuthoorUiState
import com.example.ui.theme.JuthoorFemaleAccent
import com.example.ui.theme.JuthoorHeritageGold
import com.example.ui.theme.JuthoorMaleAccent
import java.util.Locale

@Composable
fun StatisticsScreen(
    uiState: JuthoorUiState,
    onSelectScopeRoot: (String?) -> Unit,
    onOpenPersonProfile: (String) -> Unit,
) {
    val report = uiState.statisticsReport
    var showScopeDialog by remember { mutableStateOf(false) }
    val selectedScopePerson = remember(uiState.statsScopeRootId, uiState.persons) {
        uiState.statsScopeRootId?.let { id -> uiState.persons.firstOrNull { it.id == id } }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 860.dp).testTag("stats_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إحصائيات العائلة", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = selectedScopePerson?.let { "نطاق الحساب: فرع ${it.fullName}" }
                                ?: "نطاق الحساب: كامل شجرة العائلة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { showScopeDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("stats_scope_button"),
                    ) {
                        Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" تغيير النطاق")
                    }
                }
            }

            if (report.totalPersons == 0) {
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
                                Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                "لا توجد سجلات أفراد بعد لعرض الإحصائيات.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCounterCard(
                            label = "إجمالي الأفراد",
                            value = report.totalPersons.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_total_persons"),
                        )
                        StatCounterCard(
                            label = "الأجيال",
                            value = report.generationsCount.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_generations"),
                        )
                        StatCounterCard(
                            label = "الزيجات",
                            value = report.totalMarriagesCount.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_marriages"),
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCounterCard(
                            label = "الذكور",
                            value = report.malesCount.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_males"),
                        )
                        StatCounterCard(
                            label = "الإناث",
                            value = report.femalesCount.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_females"),
                        )
                        StatCounterCard(
                            label = "الأحياء",
                            value = report.livingCount.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_living"),
                        )
                        StatCounterCard(
                            label = "المتوفون",
                            value = report.deceasedCount.toString(),
                            modifier = Modifier.weight(1f).testTag("stats_deceased"),
                        )
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("الحالة الاجتماعية", style = MaterialTheme.typography.titleLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MiniPillStat("متزوجون", report.marriedCount, Modifier.weight(1f))
                                MiniPillStat("عزّاب", report.singleCount, Modifier.weight(1f))
                                MiniPillStat("مطلقون", report.divorcedCount, Modifier.weight(1f))
                                MiniPillStat("أرامل", report.widowedCount, Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("متوسط الأعمار والأكبر سناً", style = MaterialTheme.typography.titleLarge)
                            val avgLabel = report.averageAgeYears?.let {
                                String.format(Locale.US, "%.1f سنة", it)
                            } ?: "غير متاح"
                            Text(
                                text = "متوسط العمر: $avgLabel",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("stats_average_age"),
                            )
                            Text(
                                text = report.averageAgeExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            report.oldestLivingPerson?.let { oldest ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenPersonProfile(oldest.person.id) }
                                        .testTag("stats_oldest_living"),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("أكبر الأحياء سناً", style = MaterialTheme.typography.labelMedium)
                                            Text(oldest.person.fullName, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${oldest.estimatedAgeYears} سنة",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "فتح الملف")
                                        }
                                    }
                                }
                            }

                            report.oldestRecordedPerson?.let { oldestAll ->
                                if (oldestAll.person.id != report.oldestLivingPerson?.person?.id) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenPersonProfile(oldestAll.person.id) },
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("أكبر عمر مسجل تاريخياً", style = MaterialTheme.typography.labelMedium)
                                                Text(oldestAll.person.fullName, style = MaterialTheme.typography.titleMedium)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${oldestAll.estimatedAgeYears} سنة", style = MaterialTheme.typography.titleMedium)
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "فتح الملف")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (report.generationDistribution.isNotEmpty()) {
                    item {
                        DistributionBarChartCard(
                            title = "توزيع الأفراد حسب الأجيال",
                            items = report.generationDistribution.map { "الجيل ${it.generation}" to it.count },
                            barColor = JuthoorMaleAccent,
                        )
                    }
                }

                if (report.birthDecadeDistribution.isNotEmpty()) {
                    item {
                        DistributionBarChartCard(
                            title = "التوزيع الزمني حسب عقود الميلاد",
                            items = report.birthDecadeDistribution.map { "عقد ${it.startYear}" to it.count },
                            barColor = JuthoorFemaleAccent,
                        )
                    }
                }

                if (report.branchDistribution.isNotEmpty()) {
                    item {
                        DistributionBarChartCard(
                            title = "التوزيع حسب الفروع العائلية",
                            items = report.branchDistribution.map { it.groupName to it.count },
                            barColor = JuthoorHeritageGold,
                        )
                    }
                }
            }
        }
    }

    if (showScopeDialog) {
        AlertDialog(
            onDismissRequest = { showScopeDialog = false },
            title = { Text("اختيار نطاق الإحصائيات") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    items(uiState.persons, key = { it.id }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectScopeRoot(person.id)
                                    showScopeDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text("فرع: ${person.fullName}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectScopeRoot(null)
                    showScopeDialog = false
                }) {
                    Text("كل العائلة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScopeDialog = false }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun StatCounterCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniPillStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DistributionBarChartCard(
    title: String,
    items: List<Pair<String, Int>>,
    barColor: androidx.compose.ui.graphics.Color,
) {
    val maxCount = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            items.forEach { (label, count) ->
                val ratio = (count.toFloat() / maxCount.toFloat()).coerceIn(0.05f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text("$count فرد", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.small)
                                .background(barColor),
                        )
                    }
                }
            }
        }
    }
}
