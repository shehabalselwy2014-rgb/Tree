package com.example.presentation.tree

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.tree.TreeLayoutEngine
import com.example.domain.tree.TreeLayoutResult
import com.example.presentation.JuthoorUiState
import com.example.presentation.components.PersonAvatar
import com.example.ui.theme.JuthoorHeritageGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun InteractiveFamilyTreeScreen(
    uiState: JuthoorUiState,
    focusPersonId: String? = null,
    onOpenPersonProfile: (String) -> Unit,
    onExportTreePngUri: (Uri) -> Unit = {},
    onExportPdfUri: (Uri) -> Unit = {},
    onShareTreePng: (() -> Unit)? = null,
    onShareTreePdf: (() -> Unit)? = null,
) {
    var selectedRootId by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsedPersonIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(focusPersonId) }
    var layoutResult by remember { mutableStateOf<TreeLayoutResult?>(null) }
    var isComputing by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(0.92f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var initializedViewport by remember { mutableStateOf(false) }
    var pendingCenterPersonId by remember(focusPersonId) { mutableStateOf(focusPersonId) }

    var showRootPickerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(focusPersonId, uiState.parentChildLinks) {
        if (!focusPersonId.isNullOrBlank()) {
            val ancestorPath = TreeLayoutEngine.findAncestorPathToRoot(
                targetPersonId = focusPersonId,
                links = uiState.parentChildLinks,
            )
            if (ancestorPath.isNotEmpty()) {
                collapsedPersonIds = collapsedPersonIds - ancestorPath
            }
            selectedPersonId = focusPersonId
            pendingCenterPersonId = focusPersonId
        }
    }

    LaunchedEffect(
        uiState.persons,
        uiState.marriages,
        uiState.parentChildLinks,
        selectedRootId,
        collapsedPersonIds,
    ) {
        if (uiState.persons.size <= 300) {
            layoutResult = TreeLayoutEngine.buildLayout(
                persons = uiState.persons,
                marriages = uiState.marriages,
                links = uiState.parentChildLinks,
                selectedRootId = selectedRootId,
                collapsedPersonIds = collapsedPersonIds,
            )
            isComputing = false
        } else {
            isComputing = true
            val computed = withContext(Dispatchers.Default) {
                TreeLayoutEngine.buildLayout(
                    persons = uiState.persons,
                    marriages = uiState.marriages,
                    links = uiState.parentChildLinks,
                    selectedRootId = selectedRootId,
                    collapsedPersonIds = collapsedPersonIds,
                )
            }
            layoutResult = computed
            isComputing = false
        }
    }

    val currentLayout = layoutResult
    val personsById = remember(uiState.persons) { uiState.persons.associateBy { it.id } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        fun centerOnWorldPoint(worldX: Float, worldY: Float, targetScale: Float = scale) {
            val clampedScale = targetScale.coerceIn(0.20f, 2.2f)
            val xPx = with(density) { worldX.dp.toPx() }
            val yPx = with(density) { worldY.dp.toPx() }
            scale = clampedScale
            panOffset = Offset(
                x = viewportWidthPx / 2f - xPx * clampedScale,
                y = viewportHeightPx / 2.4f - yPx * clampedScale,
            )
        }

        fun fitTreeToViewport(layout: TreeLayoutResult) {
            if (layout.personNodes.isEmpty()) return
            val minX = layout.personNodes.minOf { it.x } - 36f
            val minY = layout.personNodes.minOf { it.y } - 36f
            val maxX = layout.personNodes.maxOf { it.x + TreeLayoutEngine.PERSON_CARD_WIDTH } + 36f
            val maxY = layout.personNodes.maxOf { it.y + TreeLayoutEngine.PERSON_CARD_HEIGHT } + 36f

            val boundsW = with(density) { (maxX - minX).dp.toPx() }.coerceAtLeast(1f)
            val boundsH = with(density) { (maxY - minY).dp.toPx() }.coerceAtLeast(1f)
            val topReservePx = with(density) { 96.dp.toPx() }
            val bottomReservePx = with(density) { 56.dp.toPx() }
            val availW = (viewportWidthPx - with(density) { 24.dp.toPx() }).coerceAtLeast(120f)
            val availH = (viewportHeightPx - topReservePx - bottomReservePx).coerceAtLeast(120f)

            val fitScale = minOf(availW / boundsW, availH / boundsH).coerceIn(0.15f, 1.0f)
            val worldCenterX = with(density) { ((minX + maxX) / 2f).dp.toPx() }
            val worldCenterY = with(density) { ((minY + maxY) / 2f).dp.toPx() }

            scale = fitScale
            panOffset = Offset(
                x = viewportWidthPx / 2f - worldCenterX * fitScale,
                y = topReservePx + availH / 2f - worldCenterY * fitScale,
            )
        }

        LaunchedEffect(currentLayout, pendingCenterPersonId, viewportWidthPx, viewportHeightPx) {
            val layout = currentLayout ?: return@LaunchedEffect
            if (layout.personNodes.isEmpty()) return@LaunchedEffect

            val targetId = pendingCenterPersonId
            if (targetId != null) {
                val targetNode = layout.personNodes.firstOrNull { it.person.id == targetId }
                if (targetNode != null) {
                    centerOnWorldPoint(
                        worldX = targetNode.x + TreeLayoutEngine.PERSON_CARD_WIDTH / 2f,
                        worldY = targetNode.y + TreeLayoutEngine.PERSON_CARD_HEIGHT / 2f,
                        targetScale = 0.96f,
                    )
                    pendingCenterPersonId = null
                    initializedViewport = true
                    return@LaunchedEffect
                }
            }

            if (!initializedViewport) {
                if (layout.personNodes.size <= 35) {
                    fitTreeToViewport(layout)
                } else {
                    val rootNode = layout.personNodes.firstOrNull { it.person.id == layout.activeRootId }
                        ?: layout.personNodes.first()
                    centerOnWorldPoint(
                        worldX = rootNode.x + TreeLayoutEngine.PERSON_CARD_WIDTH / 2f,
                        worldY = rootNode.y + TreeLayoutEngine.PERSON_CARD_HEIGHT / 2f,
                        targetScale = 0.88f,
                    )
                }
                initializedViewport = true
            }
        }

        if (uiState.persons.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                        Text("شجرة العائلة فارغة حالياً", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "أضف أول فرد من الصفحة الرئيسية أو قائمة الأفراد ليظهر هنا ضمن التسلسل الهرمي.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (currentLayout != null) {
            val pxPerDp = density.density.coerceAtLeast(1f)
            val worldLeft = ((-panOffset.x) / (scale * pxPerDp)) - 280f
            val worldTop = ((-panOffset.y) / (scale * pxPerDp)) - 240f
            val worldRight = ((viewportWidthPx - panOffset.x) / (scale * pxPerDp)) + 280f
            val worldBottom = ((viewportHeightPx - panOffset.y) / (scale * pxPerDp)) + 240f

            val visibleNodes = remember(currentLayout, worldLeft, worldTop, worldRight, worldBottom) {
                currentLayout.personNodes.filter { node ->
                    node.x + TreeLayoutEngine.PERSON_CARD_WIDTH >= worldLeft &&
                        node.x <= worldRight &&
                        node.y + TreeLayoutEngine.PERSON_CARD_HEIGHT >= worldTop &&
                        node.y <= worldBottom
                }
            }
            val visibleUnions = remember(currentLayout, worldLeft, worldTop, worldRight, worldBottom) {
                currentLayout.unionNodes.filter { u ->
                    u.x + 120f >= worldLeft && u.x - 120f <= worldRight &&
                        u.y + 80f >= worldTop && u.y - 80f <= worldBottom
                }
            }
            val visibleEdges = remember(currentLayout, worldLeft, worldTop, worldRight, worldBottom) {
                currentLayout.edges.filter { e ->
                    maxOf(e.startX, e.endX) >= worldLeft &&
                        minOf(e.startX, e.endX) <= worldRight &&
                        maxOf(e.startY, e.endY) >= worldTop &&
                        minOf(e.startY, e.endY) <= worldBottom
                }
            }

            val edgeColorPrimary = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
            val edgeColorMuted = MaterialTheme.colorScheme.outline
            val edgeColorGold = JuthoorHeritageGold
            val isSimplifiedLod = scale < 0.52f && currentLayout.personNodes.size > 40

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("interactive_tree_canvas")
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (oldScale * zoom).coerceIn(0.20f, 2.2f)
                                val zoomRatio = newScale / oldScale
                                val newOffsetX = centroid.x - (centroid.x - panOffset.x) * zoomRatio + pan.x
                                val newOffsetY = centroid.y - (centroid.y - panOffset.y) * zoomRatio + pan.y
                                scale = newScale
                                panOffset = Offset(newOffsetX, newOffsetY)
                            }
                        },
                ) {
                    // Screen-space full-bleed Canvas transformed in world space: lines NEVER clip!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dashPattern = PathEffect.dashPathEffect(floatArrayOf(11f, 7f), 0f)
                        val strokePx = 2.2.dp.toPx()
                        val portRadius = 3.2.dp.toPx()

                        withTransform({
                            translate(panOffset.x, panOffset.y)
                            scale(scale, scale, Offset.Zero)
                        }) {
                            visibleEdges.forEach { edge ->
                                val sx = edge.startX.dp.toPx()
                                val sy = edge.startY.dp.toPx()
                                val ex = edge.endX.dp.toPx()
                                val ey = edge.endY.dp.toPx()
                                val railY = edge.railY?.dp?.toPx() ?: ((sy + ey) / 2f)

                                val path = Path().apply {
                                    moveTo(sx, sy)
                                    lineTo(sx, railY)
                                    lineTo(ex, railY)
                                    lineTo(ex, ey)
                                }

                                if (edge.isMarriageEdge) {
                                    val isActive = edge.marriageStatus == MarriageRecordStatus.ACTIVE
                                    val color = if (isActive) edgeColorGold else edgeColorMuted
                                    val effect = if (isActive) null else dashPattern
                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(
                                            width = strokePx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                            pathEffect = effect,
                                        ),
                                    )
                                    drawCircle(color = color, radius = portRadius, center = Offset(sx, sy))
                                } else {
                                    val color = if (edge.isUnconfirmedOrKafala) edgeColorGold else edgeColorPrimary
                                    val effect = if (edge.isUnconfirmedOrKafala) dashPattern else null
                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(
                                            width = strokePx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                            pathEffect = effect,
                                        ),
                                    )
                                    drawCircle(color = color, radius = portRadius, center = Offset(ex, ey))
                                }
                            }
                        }
                    }

                    // Unbounded world container ensures cards & union badges outside viewport bounds are never clipped
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                translationX = panOffset.x,
                                translationY = panOffset.y,
                                scaleX = scale,
                                scaleY = scale,
                                transformOrigin = TransformOrigin(0f, 0f),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                                .requiredSize(currentLayout.totalWidth.dp, currentLayout.totalHeight.dp),
                        ) {
                            visibleUnions.forEach { union ->
                                UnionHubBadge(
                                    union = union,
                                    modifier = Modifier.absoluteOffset {
                                        IntOffset(
                                            x = (union.x - 24f).dp.toPx().roundToInt(),
                                            y = (union.y - 14f).dp.toPx().roundToInt(),
                                        )
                                    },
                                )
                            }

                            visibleNodes.forEach { node ->
                                Box(
                                    modifier = Modifier.absoluteOffset {
                                        IntOffset(
                                            x = node.x.dp.toPx().roundToInt(),
                                            y = node.y.dp.toPx().roundToInt(),
                                        )
                                    },
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                        val isNodeSelected = selectedPersonId == node.person.id
                                        TreePersonCard(
                                            node = node,
                                            isSelected = isNodeSelected,
                                            isActiveRoot = node.person.id == currentLayout.activeRootId,
                                            isSimplifiedLod = isSimplifiedLod,
                                            onClick = {
                                                if (isNodeSelected) {
                                                    onOpenPersonProfile(node.person.id)
                                                } else {
                                                    selectedPersonId = node.person.id
                                                }
                                            },
                                            onToggleCollapse = {
                                                collapsedPersonIds = if (node.isCollapsed) {
                                                    collapsedPersonIds - node.person.id
                                                } else {
                                                    collapsedPersonIds + node.person.id
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top floating HUD: branch info, search, root picker, fit tree
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val rootPerson = currentLayout.activeRootId?.let { personsById[it] }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedRootId != null && rootPerson != null) {
                                    "فرع: ${rootPerson.firstName}"
                                } else {
                                    rootPerson?.let { "شجرة العائلة • ${it.firstName}" } ?: "شجرة العائلة"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${currentLayout.personNodes.size} فرد ظاهر • ${currentLayout.maxGeneration} جيل • ${(scale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { showSearchOverlay = !showSearchOverlay },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_search_button"),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "بحث داخل الشجرة")
                            }
                            IconButton(
                                onClick = { showRootPickerDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_branch_filter_button"),
                            ) {
                                Icon(Icons.Filled.FilterList, contentDescription = "عرض فرع محدد")
                            }
                            IconButton(
                                onClick = { fitTreeToViewport(currentLayout) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_reset_root_button"),
                            ) {
                                Icon(Icons.Filled.FitScreen, contentDescription = "ملاءمة وإعادة توسيط الشجرة")
                            }
                            IconButton(
                                onClick = { showExportDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_export_button"),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "تصدير ومشاركة الشجرة")
                            }
                        }
                    }
                }

                if (selectedRootId != null || collapsedPersonIds.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedRootId != null) {
                            AssistChip(
                                onClick = {
                                    selectedRootId = null
                                    initializedViewport = false
                                },
                                label = { Text("عرض كامل الشجرة") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "إلغاء عزل الفرع",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.testTag("tree_clear_branch_chip"),
                            )
                        }
                        if (collapsedPersonIds.isNotEmpty()) {
                            AssistChip(
                                onClick = { collapsedPersonIds = emptySet() },
                                label = { Text("توسيع كل الفروع (${collapsedPersonIds.size})") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.UnfoldMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.testTag("tree_expand_all_chip"),
                            )
                        }
                    }
                }

                if (showSearchOverlay) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("ابحث عن فرد لتمركزه في الشجرة") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showSearchOverlay = false
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "إغلاق البحث")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tree_search_input"),
                            )
                            val matches = remember(searchQuery, uiState.persons) {
                                if (searchQuery.isBlank()) {
                                    emptyList()
                                } else {
                                    uiState.persons.filter {
                                        it.fullName.contains(searchQuery.trim(), ignoreCase = true)
                                    }.take(6)
                                }
                            }
                            matches.forEach { target ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val ancestorPath = TreeLayoutEngine.findAncestorPathToRoot(
                                                targetPersonId = target.id,
                                                links = uiState.parentChildLinks,
                                            )
                                            collapsedPersonIds = collapsedPersonIds - ancestorPath
                                            if (selectedRootId != null && target.id !in currentLayout.personNodes.map { it.person.id }) {
                                                selectedRootId = null
                                            }
                                            selectedPersonId = target.id
                                            pendingCenterPersonId = target.id
                                            showSearchOverlay = false
                                            searchQuery = ""
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PersonAvatar(
                                        fullName = target.fullName,
                                        firstName = target.firstName,
                                        gender = target.gender,
                                        imagePath = target.profileImagePath,
                                        size = 34.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(target.fullName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "الجيل ${uiState.generationByPerson[target.id] ?: 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom zoom controls (shifted cleanly above SelectedPersonBar when visible)
            val selectedPerson = selectedPersonId?.let { personsById[it] }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 14.dp,
                        bottom = if (selectedPerson != null) 128.dp else 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (isComputing) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                SmallFloatingActionButton(
                    onClick = { scale = (scale * 1.22f).coerceAtMost(2.2f) },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("tree_zoom_in_button"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "تكبير الشجرة")
                }
                SmallFloatingActionButton(
                    onClick = { scale = (scale * 0.82f).coerceAtLeast(0.28f) },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("tree_zoom_out_button"),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "تصغير الشجرة")
                }
            }

            // Bottom Selected Person Quick-Action Bar (open profile, isolate branch, center)
            if (selectedPerson != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.2.dp, JuthoorHeritageGold.copy(alpha = 0.65f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(12.dp)
                        .testTag("tree_selected_action_bar"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PersonAvatar(
                                fullName = selectedPerson.fullName,
                                firstName = selectedPerson.firstName,
                                gender = selectedPerson.gender,
                                imagePath = selectedPerson.profileImagePath,
                                size = 36.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedPerson.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "الجيل ${uiState.generationByPerson[selectedPerson.id] ?: 1} • ${selectedPerson.currentMaritalStatus.arabicLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { selectedPersonId = null },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "إغلاق الشريط", modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onOpenPersonProfile(selectedPerson.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_open_profile_button"),
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" الملف الشخصي", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = {
                                    selectedRootId = selectedPerson.id
                                    initializedViewport = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_isolate_branch_button"),
                            ) {
                                Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" فرعه فقط", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    val node = currentLayout.personNodes.firstOrNull { it.person.id == selectedPerson.id }
                                    if (node != null) {
                                        centerOnWorldPoint(
                                            node.x + TreeLayoutEngine.PERSON_CARD_WIDTH / 2f,
                                            node.y + TreeLayoutEngine.PERSON_CARD_HEIGHT / 2f,
                                            targetScale = 0.96f,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tree_center_person_button"),
                            ) {
                                Icon(Icons.Filled.CenterFocusStrong, contentDescription = "توسيط", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRootPickerDialog) {
        val candidateRoots = remember(uiState.persons, uiState.parentChildLinks) {
            TreeLayoutEngine.detectCandidateRoots(uiState.persons, uiState.parentChildLinks)
        }
        AlertDialog(
            onDismissRequest = { showRootPickerDialog = false },
            title = { Text("اختيار جذر أو فرع العرض") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        Text(
                            "الجذور العليا المكتشفة:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(candidateRoots, key = { "root_${it.id}" }) { root ->
                        AssistChip(
                            onClick = {
                                selectedRootId = root.id
                                initializedViewport = false
                                showRootPickerDialog = false
                            },
                            label = { Text("الفرع المؤسس: ${root.fullName}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            "أو عرض فرع فرد محدد فقط:",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(uiState.persons, key = { "branch_${it.id}" }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedRootId = person.id
                                    initializedViewport = false
                                    showRootPickerDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PersonAvatar(
                                fullName = person.fullName,
                                firstName = person.firstName,
                                gender = person.gender,
                                imagePath = person.profileImagePath,
                                size = 32.dp,
                            )
                            Text(person.fullName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedRootId = null
                    initializedViewport = false
                    showRootPickerDialog = false
                }) {
                    Text("إظهار الشجرة الكاملة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRootPickerDialog = false }) { Text("إغلاق") }
            },
        )
    }

    if (showExportDialog) {
        TreeExportShareDialog(
            familyTitle = uiState.preferences.familyTitle,
            isBusy = uiState.isMaintenanceBusy,
            onDismiss = { showExportDialog = false },
            onExportTreePngUri = onExportTreePngUri,
            onExportPdfUri = onExportPdfUri,
            onShareTreePng = onShareTreePng,
            onShareTreePdf = onShareTreePdf,
        )
    }
}
