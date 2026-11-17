package com.example.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JuthoorBurgundy
import com.example.ui.theme.JuthoorEmerald
import com.example.ui.theme.JuthoorHeritageGold
import com.example.ui.theme.JuthoorHeroGradientEnd
import com.example.ui.theme.JuthoorHeroGradientStart

/**
 * Vector architectural brand mark for Juthoor (جِذور):
 * - Adapts automatically between dark (Emerald/dark surface) and light (White/Ivory surface) contexts.
 * - Represents deep roots (gold arcs + burgundy origin seed) expanding into multi-generation branches.
 */
@Composable
fun JuthoorBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onDarkBackground: Boolean = true,
    showBadgeContainer: Boolean = true,
) {
    val branchColor = if (onDarkBackground) Color(0xFFFAF8F4) else JuthoorEmerald
    val rootGoldColor = if (onDarkBackground) Color(0xFFD4AF37) else JuthoorHeritageGold
    val originBurgundyColor = if (onDarkBackground) Color(0xFF9E2A35) else JuthoorBurgundy
    val nodeStrokeColor = if (onDarkBackground) Color(0xFFFAF8F4) else Color.White

    val containerModifier = if (showBadgeContainer) {
        val bgBrush = if (onDarkBackground) {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))
            )
        } else {
            Brush.linearGradient(listOf(JuthoorHeroGradientStart, JuthoorHeroGradientEnd))
        }
        val effectiveBranch = Color(0xFFFAF8F4)
        val effectiveGold = Color(0xFFD4AF37)
        val effectiveBurgundy = Color(0xFF9E2A35)
        val effectiveNodeRing = Color(0xFFFAF8F4)

        return Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(bgBrush)
                .border(
                    width = 1.dp,
                    color = effectiveGold.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(size * 0.28f),
                )
                .semantics { contentDescription = "شعار تطبيق جِذور" }
                .testTag("juthoor_brand_logo"),
            contentAlignment = Alignment.Center,
        ) {
            JuthoorVectorMarkCanvas(
                modifier = Modifier.fillMaxSize().padding(size * 0.14f),
                branchColor = effectiveBranch,
                rootGoldColor = effectiveGold,
                originBurgundyColor = effectiveBurgundy,
                nodeStrokeColor = effectiveNodeRing,
            )
        }
    } else {
        modifier
            .size(size)
            .semantics { contentDescription = "شعار تطبيق جِذور" }
            .testTag("juthoor_brand_logo")
    }

    JuthoorVectorMarkCanvas(
        modifier = containerModifier,
        branchColor = branchColor,
        rootGoldColor = rootGoldColor,
        originBurgundyColor = originBurgundyColor,
        nodeStrokeColor = nodeStrokeColor,
    )
}

@Composable
fun JuthoorWordmarkLockup(
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier,
    logoSize: Dp = 52.dp,
    subtitle: String? = "توثيق الأصول وامتداد الأجيال",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JuthoorBrandLogo(
            size = logoSize,
            onDarkBackground = onDarkBackground,
            showBadgeContainer = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "جِذور",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (onDarkBackground) Color.White else MaterialTheme.colorScheme.primary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onDarkBackground) {
                        Color.White.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun JuthoorVectorMarkCanvas(
    modifier: Modifier,
    branchColor: Color,
    rootGoldColor: Color,
    originBurgundyColor: Color,
    nodeStrokeColor: Color,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(normX: Float) = normX * w
        fun py(normY: Float) = normY * h
        val strokeMain = (w * 0.072f).coerceAtLeast(1.8f)
        val strokeThin = (w * 0.052f).coerceAtLeast(1.4f)

        // 1. Primary sweeping roots (Golden heritage arc)
        val rootsPath = Path().apply {
            moveTo(px(0.50f), py(0.72f))
            cubicTo(px(0.40f), py(0.84f), px(0.24f), py(0.88f), px(0.11f), py(0.82f))
            moveTo(px(0.50f), py(0.72f))
            cubicTo(px(0.60f), py(0.84f), px(0.76f), py(0.88f), px(0.89f), py(0.82f))
        }
        drawPath(
            path = rootsPath,
            color = rootGoldColor,
            style = Stroke(width = strokeMain, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 2. Deep foundation arc
        val deepArc = Path().apply {
            moveTo(px(0.26f), py(0.92f))
            cubicTo(px(0.40f), py(0.98f), px(0.60f), py(0.98f), px(0.74f), py(0.92f))
        }
        drawPath(
            path = deepArc,
            color = rootGoldColor.copy(alpha = 0.72f),
            style = Stroke(width = strokeThin, cap = StrokeCap.Round),
        )

        // 3. Central lineage trunk & symmetric branches
        val treePath = Path().apply {
            moveTo(px(0.50f), py(0.72f))
            lineTo(px(0.50f), py(0.18f))

            // Outer left & right branches
            moveTo(px(0.50f), py(0.50f))
            cubicTo(px(0.38f), py(0.49f), px(0.26f), py(0.40f), px(0.18f), py(0.30f))
            moveTo(px(0.50f), py(0.50f))
            cubicTo(px(0.62f), py(0.49f), px(0.74f), py(0.40f), px(0.82f), py(0.30f))

            // Upper crown forks
            moveTo(px(0.50f), py(0.34f))
            cubicTo(px(0.41f), py(0.30f), px(0.36f), py(0.23f), px(0.33f), py(0.14f))
            moveTo(px(0.50f), py(0.34f))
            cubicTo(px(0.59f), py(0.30f), px(0.64f), py(0.23f), px(0.67f), py(0.14f))
        }
        drawPath(
            path = treePath,
            color = branchColor,
            style = Stroke(width = strokeMain, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 4. Burgundy Origin Seed at Trunk-Root Junction
        val seedRadius = w * 0.082f
        drawCircle(
            color = originBurgundyColor,
            radius = seedRadius,
            center = Offset(px(0.50f), py(0.72f)),
        )
        drawCircle(
            color = nodeStrokeColor,
            radius = seedRadius,
            center = Offset(px(0.50f), py(0.72f)),
            style = Stroke(width = strokeThin * 0.65f),
        )

        // 5. Apex Generation Node (Muted Gold)
        val apexRadius = w * 0.075f
        drawCircle(
            color = rootGoldColor,
            radius = apexRadius,
            center = Offset(px(0.50f), py(0.15f)),
        )
        drawCircle(
            color = nodeStrokeColor,
            radius = apexRadius,
            center = Offset(px(0.50f), py(0.15f)),
            style = Stroke(width = strokeThin * 0.6f),
        )

        // 6. Sibling & Crown Nodes
        val sideRadius = w * 0.058f
        drawCircle(color = branchColor, radius = sideRadius, center = Offset(px(0.17f), py(0.29f)))
        drawCircle(color = branchColor, radius = sideRadius, center = Offset(px(0.83f), py(0.29f)))

        val crownSubRadius = w * 0.048f
        drawCircle(color = rootGoldColor, radius = crownSubRadius, center = Offset(px(0.33f), py(0.13f)))
        drawCircle(color = rootGoldColor, radius = crownSubRadius, center = Offset(px(0.67f), py(0.13f)))
    }
}
