package com.example.presentation.tree

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Gender
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.tree.TreeLayoutEngine
import com.example.domain.tree.TreePersonNode
import com.example.domain.tree.TreeUnionNode
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.presentation.components.PersonAvatar
import com.example.ui.theme.JuthoorBurgundyContainer
import com.example.ui.theme.JuthoorEmeraldContainer
import com.example.ui.theme.JuthoorFemaleAccent
import com.example.ui.theme.JuthoorHeritageGold
import com.example.ui.theme.JuthoorMaleAccent

@Composable
fun UnionHubBadge(
    union: TreeUnionNode,
    modifier: Modifier = Modifier,
) {
    val active = union.marriage.marriageStatus == MarriageRecordStatus.ACTIVE
    val bgColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
    val iconColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = 0.dp,
        modifier = modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = union.marriage.marriageStatus.arabicLabel,
                tint = iconColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = if (union.childrenCount > 0) "${union.childrenCount}" else union.marriage.marriageStatus.arabicLabel.take(5),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TreePersonCard(
    node: TreePersonNode,
    isSelected: Boolean,
    isActiveRoot: Boolean,
    isSimplifiedLod: Boolean,
    onClick: () -> Unit,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val person = node.person
    val isMale = person.gender == Gender.MALE
    val accentColor = if (isMale) JuthoorMaleAccent else JuthoorFemaleAccent
    val cardContainerColor = if (isMale) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val borderColor = when {
        isSelected -> JuthoorHeritageGold
        isActiveRoot -> MaterialTheme.colorScheme.primary
        else -> accentColor.copy(alpha = 0.45f)
    }
    val borderWidth = if (isSelected || isActiveRoot) 2.dp else 1.25.dp
    val birthYear = GenealogyMetricsCalculator.extractBirthYear(person.birthDate)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
        modifier = modifier
            .width(TreeLayoutEngine.PERSON_CARD_WIDTH.dp)
            .height(TreeLayoutEngine.PERSON_CARD_HEIGHT.dp)
            .clickable(onClick = onClick)
            .testTag("tree_node_${person.id}"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Subtle top lineage bar identifying gender & branch role cleanly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(if (isSelected) JuthoorHeritageGold else accentColor),
            )

            if (isSimplifiedLod) {
                // Fast LOD rendering for deeply zoomed-out massive trees
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                    Text(
                        text = person.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        PersonAvatar(
                            fullName = person.fullName,
                            firstName = person.firstName,
                            gender = person.gender,
                            imagePath = person.profileImagePath,
                            size = 40.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = person.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isActiveRoot) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = "جذر العرض",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val badgeBg = if (isMale) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                        val badgeFg = if (isMale) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "ج${node.generation}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            val metaText = buildString {
                                if (birthYear != null) append(birthYear) else append(person.gender.arabicLabel)
                                if (person.isDeceased) append(" • رحمه الله")
                            }
                            Text(
                                text = metaText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (node.hasChildren) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (node.isCollapsed) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (node.isCollapsed) JuthoorHeritageGold else MaterialTheme.colorScheme.outline,
                                ),
                                modifier = Modifier
                                    .clickable(onClick = onToggleCollapse)
                                    .testTag("tree_collapse_${person.id}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    if (node.isCollapsed && node.totalDescendantsCount > 0) {
                                        Text(
                                            text = "+${node.totalDescendantsCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                    Icon(
                                        imageVector = if (node.isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                        contentDescription = if (node.isCollapsed) "توسيع الأبناء" else "طي الأبناء",
                                        tint = if (node.isCollapsed) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp),
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
