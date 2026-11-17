package com.example.domain.tree

import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.Person

data class TreePersonNode(
    val person: Person,
    val x: Float,
    val y: Float,
    val generation: Int,
    val hasChildren: Boolean,
    val totalDescendantsCount: Int,
    val isCollapsed: Boolean,
)

data class TreeUnionNode(
    val marriage: Marriage,
    val primaryParentId: String,
    val spouse: Person?,
    val x: Float,
    val y: Float,
    val spouseX: Float,
    val spouseY: Float,
    val childrenCount: Int,
)

data class TreeEdge(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val railY: Float? = null,
    val isMarriageEdge: Boolean = false,
    val marriageStatus: MarriageRecordStatus? = null,
    val isUnconfirmedOrKafala: Boolean = false,
)

data class TreeLayoutResult(
    val rootIds: List<String>,
    val activeRootId: String?,
    val personNodes: List<TreePersonNode>,
    val unionNodes: List<TreeUnionNode>,
    val edges: List<TreeEdge>,
    val totalWidth: Float,
    val totalHeight: Float,
    val maxGeneration: Int,
)

object TreeLayoutEngine {

    const val PERSON_CARD_WIDTH = 204f
    const val PERSON_CARD_HEIGHT = 108f
    private const val HORIZONTAL_GAP = 36f
    private const val SPOUSE_GAP = 32f
    private const val LEVEL_VERTICAL_STEP = 236f
    private const val UNION_OFFSET_Y = 158f
    private const val PADDING = 80f
    private const val COMPONENT_GAP = 96f

    fun detectCandidateRoots(
        persons: List<Person>,
        links: List<ParentChildLink>,
    ): List<Person> {
        if (persons.isEmpty()) return emptyList()
        val childIds = links.map { it.childId }.toSet()
        val roots = persons.filter { it.id !in childIds }
        return if (roots.isNotEmpty()) {
            roots.sortedWith(
                compareBy<Person>(
                    { it.gender != Gender.MALE },
                    { it.birthDate ?: "9999" },
                    { it.createdAt },
                )
            )
        } else {
            listOf(persons.first())
        }
    }

    fun findAncestorPathToRoot(
        targetPersonId: String,
        links: List<ParentChildLink>,
    ): Set<String> {
        val parentsByChild = links.groupBy { it.childId }
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(targetPersonId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            parentsByChild[current].orEmpty().forEach { link ->
                if (link.parentId !in visited) {
                    queue.add(link.parentId)
                }
            }
        }
        return visited
    }

    fun buildLayout(
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        selectedRootId: String? = null,
        collapsedPersonIds: Set<String> = emptySet(),
    ): TreeLayoutResult {
        if (persons.isEmpty()) {
            return TreeLayoutResult(
                rootIds = emptyList(),
                activeRootId = null,
                personNodes = emptyList(),
                unionNodes = emptyList(),
                edges = emptyList(),
                totalWidth = 0f,
                totalHeight = 0f,
                maxGeneration = 0,
            )
        }

        val personsById = persons.associateBy { it.id }
        val candidateRoots = detectCandidateRoots(persons, links)
        val isolatedRoot = selectedRootId?.takeIf { it in personsById }
        val activeRoot = isolatedRoot ?: candidateRoots.first().id

        val marriagesByPerson = HashMap<String, MutableList<Marriage>>()
        for (m in marriages) {
            if (m.partnerOneId in personsById && m.partnerTwoId in personsById) {
                marriagesByPerson.getOrPut(m.partnerOneId) { mutableListOf() }.add(m)
                marriagesByPerson.getOrPut(m.partnerTwoId) { mutableListOf() }.add(m)
            }
        }

        val linksByParent = links
            .filter { it.parentId in personsById && it.childId in personsById }
            .groupBy { it.parentId }

        val placedPersonIds = HashSet<String>()
        val placedNodesById = HashMap<String, TreePersonNode>()
        val personNodes = mutableListOf<TreePersonNode>()
        val unionNodes = mutableListOf<TreeUnionNode>()
        val edges = mutableListOf<TreeEdge>()

        val descendantsCache = HashMap<String, Int>()
        val visitingDescendants = HashSet<String>()

        fun collectChildrenPartition(
            personId: String,
            personMarriages: List<Marriage>,
        ): ChildrenPartition {
            val validMarriageIds = personMarriages.map { it.id }.toSet()
            val primaryLinks = linksByParent[personId].orEmpty()
            val claimedChildren = HashSet<String>()

            val byMarriage = LinkedHashMap<String, List<ParentChildLink>>()
            for (m in personMarriages) {
                val spouseId = m.otherPartnerId(personId)
                val spouseLinks = spouseId?.let { linksByParent[it] }.orEmpty()
                val spouseSingleMarriage = spouseId != null && marriagesByPerson[spouseId].orEmpty().size == 1

                val combined = buildList {
                    primaryLinks.filter { it.marriageId == m.id }.forEach { add(it) }
                    spouseLinks.filter {
                        it.marriageId == m.id || (it.marriageId == null && spouseSingleMarriage)
                    }.forEach { add(it) }
                }.distinctBy { it.childId }
                    .filter { claimedChildren.add(it.childId) }

                byMarriage[m.id] = combined
            }

            val unlinked = primaryLinks
                .filter { it.marriageId == null || it.marriageId !in validMarriageIds }
                .distinctBy { it.childId }
                .filter { claimedChildren.add(it.childId) }

            return ChildrenPartition(
                byMarriageId = byMarriage,
                unlinkedLinks = unlinked,
            )
        }

        fun countTotalDescendants(personId: String): Int {
            descendantsCache[personId]?.let { return it }
            if (!visitingDescendants.add(personId)) return 0
            val personMarriages = marriagesByPerson[personId].orEmpty().sortedBy { it.createdAt }
            val partition = collectChildrenPartition(personId, personMarriages)
            val allChildIds = (partition.byMarriageId.values.flatten() + partition.unlinkedLinks)
                .map { it.childId }
                .distinct()
            var total = 0
            for (cid in allChildIds) {
                total += 1 + countTotalDescendants(cid)
            }
            visitingDescendants.remove(personId)
            descendantsCache[personId] = total
            return total
        }

        val widthCache = HashMap<String, Float>()
        val visitingMeasure = HashSet<String>()

        fun measureChildrenRowWidth(childrenLinks: List<ParentChildLink>): Float {
            if (childrenLinks.isEmpty()) return 0f
            var total = 0f
            childrenLinks.forEachIndexed { idx, link ->
                total += measureSubtreeWidthInternal(
                    personId = link.childId,
                    marriagesByPerson = marriagesByPerson,
                    collectPartition = ::collectChildrenPartition,
                    collapsedPersonIds = collapsedPersonIds,
                    widthCache = widthCache,
                    visitingMeasure = visitingMeasure,
                )
                if (idx < childrenLinks.lastIndex) total += HORIZONTAL_GAP
            }
            return total
        }

        fun measureSubtreeWidth(personId: String): Float {
            widthCache[personId]?.let { return it }
            if (!visitingMeasure.add(personId)) return PERSON_CARD_WIDTH

            val personMarriages = marriagesByPerson[personId].orEmpty().sortedBy { it.createdAt }
            val partition = collectChildrenPartition(personId, personMarriages)
            val isCollapsed = personId in collapsedPersonIds

            val total = when {
                personMarriages.isEmpty() -> {
                    val childrenW = if (isCollapsed) 0f else measureChildrenRowWidth(partition.unlinkedLinks)
                    maxOf(PERSON_CARD_WIDTH, childrenW)
                }

                personMarriages.size == 1 && partition.unlinkedLinks.isEmpty() -> {
                    val pairW = PERSON_CARD_WIDTH * 2f + SPOUSE_GAP
                    val mLinks = partition.byMarriageId[personMarriages.first().id].orEmpty()
                    val childrenW = if (isCollapsed) 0f else measureChildrenRowWidth(mLinks)
                    maxOf(pairW, childrenW)
                }

                else -> {
                    // Polygyny or mixed unlinked children: husband gets Slot 0, each wife gets her own non-overlapping column Slot 1..k
                    val unlinkedW = if (isCollapsed) 0f else measureChildrenRowWidth(partition.unlinkedLinks)
                    val slot0W = maxOf(PERSON_CARD_WIDTH, unlinkedW)
                    var sumW = slot0W
                    for (m in personMarriages) {
                        val mLinks = partition.byMarriageId[m.id].orEmpty()
                        val mChildrenW = if (isCollapsed) 0f else measureChildrenRowWidth(mLinks)
                        val slotW = maxOf(PERSON_CARD_WIDTH, mChildrenW)
                        sumW += HORIZONTAL_GAP + slotW
                    }
                    sumW
                }
            }

            visitingMeasure.remove(personId)
            widthCache[personId] = total
            return total
        }

        var maxGenReached = 1
        var maxX = 0f
        var maxY = 0f

        fun recordNode(node: TreePersonNode) {
            personNodes.add(node)
            placedNodesById[node.person.id] = node
            maxX = maxOf(maxX, node.x + PERSON_CARD_WIDTH + PADDING)
            maxY = maxOf(maxY, node.y + PERSON_CARD_HEIGHT + PADDING)
        }

        fun placeSubtree(
            personId: String,
            leftX: Float,
            generation: Int,
        ) {
            if (!placedPersonIds.add(personId)) return
            val person = personsById[personId] ?: return
            maxGenReached = maxOf(maxGenReached, generation)

            val subtreeWidth = measureSubtreeWidth(personId)
            val y = PADDING + (generation - 1) * LEVEL_VERTICAL_STEP
            val personMarriages = marriagesByPerson[personId].orEmpty().sortedBy { it.createdAt }
            val partition = collectChildrenPartition(personId, personMarriages)
            val isCollapsed = personId in collapsedPersonIds
            val directAndNestedDescendants = countTotalDescendants(personId)

            fun placeChildRow(
                childrenLinks: List<ParentChildLink>,
                rowCenterX: Float,
                anchorX: Float,
                anchorY: Float,
                railY: Float,
            ) {
                if (isCollapsed || childrenLinks.isEmpty()) return
                val childWidths = childrenLinks.map { measureSubtreeWidth(it.childId) }
                val rowWidth = childWidths.sum() + (childrenLinks.size - 1) * HORIZONTAL_GAP
                var cursorX = rowCenterX - rowWidth / 2f
                val childTopY = PADDING + generation * LEVEL_VERTICAL_STEP

                childrenLinks.forEachIndexed { idx, link ->
                    val cw = childWidths[idx]
                    val existing = placedNodesById[link.childId]
                    val targetCenterX: Float
                    val targetTopY: Float
                    if (existing != null) {
                        targetCenterX = existing.x + PERSON_CARD_WIDTH / 2f
                        targetTopY = existing.y
                    } else {
                        targetCenterX = cursorX + cw / 2f
                        targetTopY = childTopY
                    }

                    edges.add(
                        TreeEdge(
                            startX = anchorX,
                            startY = anchorY,
                            endX = targetCenterX,
                            endY = targetTopY,
                            railY = railY,
                            isMarriageEdge = false,
                            isUnconfirmedOrKafala = !link.relationshipType.isBiological || !link.relationshipType.isConfirmed,
                        )
                    )

                    if (existing == null) {
                        placeSubtree(
                            personId = link.childId,
                            leftX = cursorX,
                            generation = generation + 1,
                        )
                    }
                    cursorX += cw + HORIZONTAL_GAP
                }
            }

            when {
                // Case 1: Single parent (0 marriages)
                personMarriages.isEmpty() -> {
                    val primaryX = leftX + (subtreeWidth - PERSON_CARD_WIDTH) / 2f
                    recordNode(
                        TreePersonNode(
                            person = person,
                            x = primaryX,
                            y = y,
                            generation = generation,
                            hasChildren = directAndNestedDescendants > 0,
                            totalDescendantsCount = directAndNestedDescendants,
                            isCollapsed = isCollapsed,
                        )
                    )
                    val primaryCenterX = primaryX + PERSON_CARD_WIDTH / 2f
                    val primaryBottomY = y + PERSON_CARD_HEIGHT
                    val childRailY = primaryBottomY + (LEVEL_VERTICAL_STEP - PERSON_CARD_HEIGHT) * 0.48f
                    placeChildRow(
                        childrenLinks = partition.unlinkedLinks,
                        rowCenterX = leftX + subtreeWidth / 2f,
                        anchorX = primaryCenterX,
                        anchorY = primaryBottomY,
                        railY = childRailY,
                    )
                }

                // Case 2: Standard couple (1 marriage & no unlinked children)
                personMarriages.size == 1 && partition.unlinkedLinks.isEmpty() -> {
                    val marriage = personMarriages.first()
                    val pairW = PERSON_CARD_WIDTH * 2f + SPOUSE_GAP
                    val pairStartX = leftX + (subtreeWidth - pairW) / 2f
                    val primaryX = pairStartX
                    val spouseX = pairStartX + PERSON_CARD_WIDTH + SPOUSE_GAP

                    recordNode(
                        TreePersonNode(
                            person = person,
                            x = primaryX,
                            y = y,
                            generation = generation,
                            hasChildren = directAndNestedDescendants > 0,
                            totalDescendantsCount = directAndNestedDescendants,
                            isCollapsed = isCollapsed,
                        )
                    )

                    val spouseId = marriage.otherPartnerId(personId)
                    val spouse = spouseId?.let { personsById[it] }
                    if (spouse != null && placedPersonIds.add(spouse.id)) {
                        recordNode(
                            TreePersonNode(
                                person = spouse,
                                x = spouseX,
                                y = y,
                                generation = generation,
                                hasChildren = false,
                                totalDescendantsCount = 0,
                                isCollapsed = false,
                            )
                        )
                    }

                    val unionX = pairStartX + pairW / 2f
                    val unionY = y + UNION_OFFSET_Y
                    val marriageRailY = y + PERSON_CARD_HEIGHT + 22f

                    edges.add(
                        TreeEdge(
                            startX = primaryX + PERSON_CARD_WIDTH / 2f,
                            startY = y + PERSON_CARD_HEIGHT,
                            endX = unionX,
                            endY = unionY,
                            railY = marriageRailY,
                            isMarriageEdge = true,
                            marriageStatus = marriage.marriageStatus,
                        )
                    )
                    edges.add(
                        TreeEdge(
                            startX = spouseX + PERSON_CARD_WIDTH / 2f,
                            startY = y + PERSON_CARD_HEIGHT,
                            endX = unionX,
                            endY = unionY,
                            railY = marriageRailY,
                            isMarriageEdge = true,
                            marriageStatus = marriage.marriageStatus,
                        )
                    )

                    val mLinks = partition.byMarriageId[marriage.id].orEmpty()
                    unionNodes.add(
                        TreeUnionNode(
                            marriage = marriage,
                            primaryParentId = personId,
                            spouse = spouse,
                            x = unionX,
                            y = unionY,
                            spouseX = spouseX,
                            spouseY = y,
                            childrenCount = mLinks.size,
                        )
                    )

                    placeChildRow(
                        childrenLinks = mLinks,
                        rowCenterX = leftX + subtreeWidth / 2f,
                        anchorX = unionX,
                        anchorY = unionY + 14f,
                        railY = unionY + 42f,
                    )
                }

                // Case 3: Polygyny (2+ marriages) or parent with unlinked children + marriage
                // Each wife receives her own horizontally isolated slot above HER children -> zero overlaps & zero crossings
                else -> {
                    val unlinkedW = if (isCollapsed) 0f else measureChildrenRowWidth(partition.unlinkedLinks)
                    val slot0W = maxOf(PERSON_CARD_WIDTH, unlinkedW)
                    val primaryX = leftX + (slot0W - PERSON_CARD_WIDTH) / 2f
                    val primaryCenterX = primaryX + PERSON_CARD_WIDTH / 2f
                    val primaryBottomY = y + PERSON_CARD_HEIGHT

                    recordNode(
                        TreePersonNode(
                            person = person,
                            x = primaryX,
                            y = y,
                            generation = generation,
                            hasChildren = directAndNestedDescendants > 0,
                            totalDescendantsCount = directAndNestedDescendants,
                            isCollapsed = isCollapsed,
                        )
                    )

                    if (partition.unlinkedLinks.isNotEmpty()) {
                        placeChildRow(
                            childrenLinks = partition.unlinkedLinks,
                            rowCenterX = leftX + slot0W / 2f,
                            anchorX = primaryCenterX,
                            anchorY = primaryBottomY,
                            railY = y + UNION_OFFSET_Y + 42f,
                        )
                    }

                    var slotCursorX = leftX + slot0W + HORIZONTAL_GAP
                    val railSpacing = if (personMarriages.size <= 3) 9f else (28f / personMarriages.size.coerceAtLeast(1))

                    personMarriages.forEachIndexed { idx, marriage ->
                        val mLinks = partition.byMarriageId[marriage.id].orEmpty()
                        val mChildrenW = if (isCollapsed) 0f else measureChildrenRowWidth(mLinks)
                        val slotW = maxOf(PERSON_CARD_WIDTH, mChildrenW)
                        val spouseX = slotCursorX + (slotW - PERSON_CARD_WIDTH) / 2f
                        val spouseCenterX = spouseX + PERSON_CARD_WIDTH / 2f

                        val spouseId = marriage.otherPartnerId(personId)
                        val spouse = spouseId?.let { personsById[it] }
                        if (spouse != null && placedPersonIds.add(spouse.id)) {
                            recordNode(
                                TreePersonNode(
                                    person = spouse,
                                    x = spouseX,
                                    y = y,
                                    generation = generation,
                                    hasChildren = false,
                                    totalDescendantsCount = 0,
                                    isCollapsed = false,
                                )
                            )
                        }

                        // Union hub sits cleanly underneath Wife/Spouse slot above her children
                        val unionX = spouseCenterX
                        val unionY = y + UNION_OFFSET_Y
                        val staggeredRailY = y + PERSON_CARD_HEIGHT + 12f + idx * railSpacing

                        edges.add(
                            TreeEdge(
                                startX = primaryCenterX,
                                startY = primaryBottomY,
                                endX = unionX,
                                endY = unionY,
                                railY = staggeredRailY,
                                isMarriageEdge = true,
                                marriageStatus = marriage.marriageStatus,
                            )
                        )
                        edges.add(
                            TreeEdge(
                                startX = spouseCenterX,
                                startY = primaryBottomY,
                                endX = unionX,
                                endY = unionY,
                                railY = staggeredRailY,
                                isMarriageEdge = true,
                                marriageStatus = marriage.marriageStatus,
                            )
                        )

                        unionNodes.add(
                            TreeUnionNode(
                                marriage = marriage,
                                primaryParentId = personId,
                                spouse = spouse,
                                x = unionX,
                                y = unionY,
                                spouseX = spouseX,
                                spouseY = y,
                                childrenCount = mLinks.size,
                            )
                        )

                        placeChildRow(
                            childrenLinks = mLinks,
                            rowCenterX = slotCursorX + slotW / 2f,
                            anchorX = unionX,
                            anchorY = unionY + 14f,
                            railY = unionY + 42f,
                        )

                        slotCursorX += slotW + HORIZONTAL_GAP
                    }
                }
            }
        }

        // Primary root placement
        placeSubtree(
            personId = activeRoot,
            leftX = PADDING,
            generation = 1,
        )

        // When viewing the full family tree (no explicit branch filter), place any remaining unconnected components
        // side-by-side so no family members ever vanish from the tree.
        if (isolatedRoot == null) {
            for (candidate in candidateRoots) {
                if (candidate.id !in placedPersonIds) {
                    val componentLeftX = if (maxX > 0f) maxX - PADDING + COMPONENT_GAP else PADDING
                    placeSubtree(
                        personId = candidate.id,
                        leftX = componentLeftX,
                        generation = 1,
                    )
                }
            }
        }

        return TreeLayoutResult(
            rootIds = candidateRoots.map { it.id },
            activeRootId = activeRoot,
            personNodes = personNodes,
            unionNodes = unionNodes,
            edges = edges,
            totalWidth = maxOf(maxX, 600f),
            totalHeight = maxOf(maxY, 500f),
            maxGeneration = maxGenReached,
        )
    }

    private data class ChildrenPartition(
        val byMarriageId: Map<String, List<ParentChildLink>>,
        val unlinkedLinks: List<ParentChildLink>,
    )

    private fun measureSubtreeWidthInternal(
        personId: String,
        marriagesByPerson: Map<String, List<Marriage>>,
        collectPartition: (String, List<Marriage>) -> ChildrenPartition,
        collapsedPersonIds: Set<String>,
        widthCache: MutableMap<String, Float>,
        visitingMeasure: MutableSet<String>,
    ): Float {
        widthCache[personId]?.let { return it }
        if (!visitingMeasure.add(personId)) return PERSON_CARD_WIDTH

        val personMarriages = marriagesByPerson[personId].orEmpty().sortedBy { it.createdAt }
        val partition = collectPartition(personId, personMarriages)
        val isCollapsed = personId in collapsedPersonIds

        fun measureRow(linksList: List<ParentChildLink>): Float {
            if (linksList.isEmpty()) return 0f
            var total = 0f
            linksList.forEachIndexed { idx, link ->
                total += measureSubtreeWidthInternal(
                    personId = link.childId,
                    marriagesByPerson = marriagesByPerson,
                    collectPartition = collectPartition,
                    collapsedPersonIds = collapsedPersonIds,
                    widthCache = widthCache,
                    visitingMeasure = visitingMeasure,
                )
                if (idx < linksList.lastIndex) total += HORIZONTAL_GAP
            }
            return total
        }

        val total = when {
            personMarriages.isEmpty() -> {
                val childrenW = if (isCollapsed) 0f else measureRow(partition.unlinkedLinks)
                maxOf(PERSON_CARD_WIDTH, childrenW)
            }

            personMarriages.size == 1 && partition.unlinkedLinks.isEmpty() -> {
                val pairW = PERSON_CARD_WIDTH * 2f + SPOUSE_GAP
                val mLinks = partition.byMarriageId[personMarriages.first().id].orEmpty()
                val childrenW = if (isCollapsed) 0f else measureRow(mLinks)
                maxOf(pairW, childrenW)
            }

            else -> {
                val unlinkedW = if (isCollapsed) 0f else measureRow(partition.unlinkedLinks)
                var sumW = maxOf(PERSON_CARD_WIDTH, unlinkedW)
                for (m in personMarriages) {
                    val mLinks = partition.byMarriageId[m.id].orEmpty()
                    val mChildrenW = if (isCollapsed) 0f else measureRow(mLinks)
                    sumW += HORIZONTAL_GAP + maxOf(PERSON_CARD_WIDTH, mChildrenW)
                }
                sumW
            }
        }

        visitingMeasure.remove(personId)
        widthCache[personId] = total
        return total
    }
}
