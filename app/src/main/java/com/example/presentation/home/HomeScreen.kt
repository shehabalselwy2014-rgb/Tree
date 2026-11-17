package com.example.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.util.HijriDateConverter
import com.example.presentation.JuthoorUiState
import com.example.presentation.components.JuthoorBrandLogo
import com.example.presentation.components.PersonAvatar
import com.example.ui.theme.JuthoorFemaleAccent
import com.example.ui.theme.JuthoorHeritageGold
import com.example.ui.theme.JuthoorHeroGradientEnd
import com.example.ui.theme.JuthoorHeroGradientStart
import com.example.ui.theme.JuthoorMaleAccent

@Composable
fun HomeScreen(
    uiState: JuthoorUiState,
    onOpenTree: () -> Unit,
    onAddPerson: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenOccasions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonProfile: (String) -> Unit,
) {
    val metrics = uiState.overviewMetrics
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .testTag("home_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Hero Family Identity Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, JuthoorHeritageGold.copy(alpha = 0.45f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(JuthoorHeroGradientStart, JuthoorHeroGradientEnd)
                                )
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            JuthoorBrandLogo(
                                size = 60.dp,
                                onDarkBackground = true,
                                showBadgeContainer = true,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = uiState.preferences.familyTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = uiState.preferences.familyMotto,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HeroPillStat(
                                label = "الأفراد",
                                value = metrics.totalPersons.toString(),
                                modifier = Modifier.weight(1f).testTag("home_total_persons"),
                            )
                            HeroPillStat(
                                label = "الأجيال",
                                value = metrics.generationsCount.toString(),
                                modifier = Modifier.weight(1f).testTag("home_generations"),
                            )
                            HeroPillStat(
                                label = "الأحياء / المتوفون",
                                value = "${metrics.livingCount} / ${metrics.deceasedCount}",
                                modifier = Modifier.weight(1.35f).testTag("home_living_deceased"),
                            )
                        }
                    }
                }
            }

            // 2. Uniform Stats Grid (2x2 on compact phones, 4-across on wide screens)
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isCompactWidth = maxWidth < 500.dp
                    if (isCompactWidth) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MetricCard(
                                    icon = Icons.Filled.Groups,
                                    label = "إجمالي السجل",
                                    value = metrics.totalPersons.toString(),
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                    border = cardBorder,
                                    modifier = Modifier.weight(1f),
                                )
                                MetricCard(
                                    icon = Icons.Filled.Layers,
                                    label = "عدد الأجيال",
                                    value = metrics.generationsCount.toString(),
                                    iconTint = JuthoorHeritageGold,
                                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                    border = cardBorder,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MetricCard(
                                    icon = Icons.Filled.Male,
                                    label = "الذكور",
                                    value = metrics.malesCount.toString(),
                                    iconTint = JuthoorMaleAccent,
                                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                    border = cardBorder,
                                    modifier = Modifier.weight(1f).testTag("home_males_count"),
                                )
                                MetricCard(
                                    icon = Icons.Filled.Female,
                                    label = "الإناث",
                                    value = metrics.femalesCount.toString(),
                                    iconTint = JuthoorFemaleAccent,
                                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                    border = cardBorder,
                                    modifier = Modifier.weight(1f).testTag("home_females_count"),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricCard(
                                icon = Icons.Filled.Groups,
                                label = "إجمالي السجل",
                                value = metrics.totalPersons.toString(),
                                iconTint = MaterialTheme.colorScheme.primary,
                                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                border = cardBorder,
                                modifier = Modifier.weight(1f),
                            )
                            MetricCard(
                                icon = Icons.Filled.Male,
                                label = "الذكور",
                                value = metrics.malesCount.toString(),
                                iconTint = JuthoorMaleAccent,
                                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                border = cardBorder,
                                modifier = Modifier.weight(1f).testTag("home_males_count"),
                            )
                            MetricCard(
                                icon = Icons.Filled.Female,
                                label = "الإناث",
                                value = metrics.femalesCount.toString(),
                                iconTint = JuthoorFemaleAccent,
                                iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                border = cardBorder,
                                modifier = Modifier.weight(1f).testTag("home_females_count"),
                            )
                            MetricCard(
                                icon = Icons.Filled.Layers,
                                label = "عدد الأجيال",
                                value = metrics.generationsCount.toString(),
                                iconTint = JuthoorHeritageGold,
                                iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                border = cardBorder,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 3. Balanced Action Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onOpenTree,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("home_open_tree_button"),
                        ) {
                            Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("شجرة العائلة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = onAddPerson,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("home_add_person_button"),
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إضافة فرد", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenSearch,
                            shape = RoundedCornerShape(12.dp),
                            border = cardBorder,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("home_search_button"),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("البحث", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenOccasions,
                            shape = RoundedCornerShape(12.dp),
                            border = cardBorder,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("home_occasions_button"),
                        ) {
                            Icon(Icons.Filled.Celebration, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("المناسبات", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            border = cardBorder,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("home_settings_button"),
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الإعدادات", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 4. Recently Added Section
            item {
                Text(
                    text = "آخر الأشخاص المضافين",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (uiState.recentPersons.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "لم يُسجل أي فرد بعد. اضغط على «إضافة فرد» للبدء بتوثيق شجرة العائلة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            } else {
                items(uiState.recentPersons, key = { it.id }) { person ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPersonProfile(person.id) }
                            .testTag("home_recent_${person.id}"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PersonAvatar(
                                fullName = person.fullName,
                                firstName = person.firstName,
                                gender = person.gender,
                                imagePath = person.profileImagePath,
                                size = 44.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = person.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val birthFormatted = HijriDateConverter.formatDateForDisplay(
                                    person.birthDate,
                                    person.birthDatePrecision,
                                    uiState.preferences.showHijriDates,
                                )
                                Text(
                                    text = "${person.gender.arabicLabel} • الميلاد: $birthFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "فتح الملف الشخصي",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // 5. Upcoming Occasions Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "المناسبات العائلية القادمة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onOpenOccasions) {
                        Text("عرض الكل")
                    }
                }
            }

            if (uiState.upcomingOccasions.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Celebration,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                text = "لا توجد مناسبات مسجلة خلال الـ 90 يوماً القادمة. تظهر هنا أعياد الميلاد وذكرى الزواج المسجلة بتواريخ دقيقة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(uiState.upcomingOccasions, key = { it.id }) { occasion ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = occasion.relatedPersonId != null) {
                                occasion.relatedPersonId?.let(onOpenPersonProfile)
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(occasion.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${occasion.subtitle} (${occasion.dateLabel})",
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

@Composable
private fun HeroPillStat(label: String, value: String, modifier: Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    iconContainer: Color,
    border: BorderStroke,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.height(92.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
