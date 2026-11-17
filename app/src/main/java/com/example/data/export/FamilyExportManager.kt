package com.example.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import com.example.domain.model.FamilyGroup
import com.example.domain.model.Gender
import com.example.domain.model.Marriage
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.ParentChildLink
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.Person
import com.example.domain.stats.StatisticsEngine
import com.example.domain.tree.TreeLayoutEngine
import com.example.domain.tree.TreeLayoutResult
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.domain.util.HijriDateConverter
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FamilyExportManager(private val context: Context) {

    companion object {
        private const val EMERALD = 0xFF163C2B.toInt()
        private const val EMERALD_DARK = 0xFF102C1F.toInt()
        private const val EMERALD_TINT = 0xFFE6EFEA.toInt()
        private const val ROSEWOOD_FEMALE = 0xFF8B333C.toInt()
        private const val ROSEWOOD_TINT = 0xFFFAF1F2.toInt()
        private const val GOLD_ACCENT = 0xFFB58D36.toInt()
        private const val TEXT_DARK = 0xFF18221D.toInt()
        private const val TEXT_MUTED = 0xFF5D6560.toInt()
        private const val OUTLINE = 0xFFE0DDD4.toInt()
        private const val A4_WIDTH = 595
        private const val A4_HEIGHT = 842
    }

    suspend fun exportTreeToShareUri(
        asPdf: Boolean,
        familyTitle: String,
        familyMotto: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        familyGroups: List<FamilyGroup>,
        showHijri: Boolean,
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val ext = if (asPdf) "pdf" else "png"
            val mimeType = if (asPdf) "application/pdf" else "image/png"
            val outFile = File(exportsDir, "juthoor_tree_$stamp.$ext")
            FileOutputStream(outFile).use { stream ->
                if (asPdf) {
                    exportPdfToStream(
                        outputStream = stream,
                        familyTitle = familyTitle,
                        familyMotto = familyMotto,
                        persons = persons,
                        marriages = marriages,
                        links = links,
                        familyGroups = familyGroups,
                        showHijri = showHijri,
                    ).getOrThrow()
                } else {
                    exportTreePngToStream(
                        outputStream = stream,
                        familyTitle = familyTitle,
                        persons = persons,
                        marriages = marriages,
                        links = links,
                        showHijri = showHijri,
                    ).getOrThrow()
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile,
            )
            uri to mimeType
        }
    }

    suspend fun exportCsvToUri(
        uri: Uri,
        familyTitle: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        familyGroups: List<FamilyGroup>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("تعذر فتح ملف التصدير CSV.")
            out.use { stream ->
                exportCsvToStream(stream, familyTitle, persons, marriages, links, familyGroups).getOrThrow()
            }
        }
    }

    suspend fun exportCsvToStream(
        outputStream: OutputStream,
        familyTitle: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        familyGroups: List<FamilyGroup>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val personsById = persons.associateBy { it.id }
            val groupsById = familyGroups.associateBy { it.id }
            val generationMap = GenealogyMetricsCalculator.computeGenerationByPerson(persons, links)
            val linksByChild = links.groupBy { it.childId }
            val linksByParent = links.groupBy { it.parentId }

            val sb = StringBuilder()
            // UTF-8 BOM for proper Arabic rendering in Excel and spreadsheet apps
            sb.append('\uFEFF')
            sb.append(
                listOf(
                    "العائلة",
                    "الاسم الكامل",
                    "الاسم الأول",
                    "الجنس",
                    "الجيل",
                    "تاريخ الميلاد",
                    "مكان الميلاد",
                    "الحالة الاجتماعية",
                    "المهنة",
                    "تاريخ الوفاة",
                    "الفرع العائلي",
                    "الأب",
                    "الأم",
                    "عدد الزيجات",
                    "عدد الأبناء",
                ).joinToString(",") { escapeCsv(it) }
            ).append("\r\n")

            val sorted = persons.sortedWith(
                compareBy<Person>({ generationMap[it.id] ?: 1 }, { it.fullName })
            )
            for (person in sorted) {
                val childLinks = linksByChild[person.id].orEmpty()
                val fatherName = childLinks.firstOrNull {
                    it.relationshipType == ParentRelationshipType.BIOLOGICAL_FATHER ||
                        it.relationshipType == ParentRelationshipType.UNCONFIRMED_FATHER
                }?.let { personsById[it.parentId]?.fullName }.orEmpty()
                val motherName = childLinks.firstOrNull {
                    it.relationshipType == ParentRelationshipType.BIOLOGICAL_MOTHER ||
                        it.relationshipType == ParentRelationshipType.UNCONFIRMED_MOTHER
                }?.let { personsById[it.parentId]?.fullName }.orEmpty()
                val marriagesCount = marriages.count { it.involves(person.id) }
                val childrenCount = linksByParent[person.id].orEmpty().map { it.childId }.distinct().size
                val branchName = person.familyGroupId?.let { groupsById[it]?.groupName }.orEmpty()

                sb.append(
                    listOf(
                        familyTitle,
                        person.fullName,
                        person.firstName,
                        person.gender.arabicLabel,
                        (generationMap[person.id] ?: 1).toString(),
                        person.birthDate.orEmpty(),
                        person.birthPlace.orEmpty(),
                        person.currentMaritalStatus.arabicLabel,
                        person.occupation.orEmpty(),
                        person.deathDate.orEmpty(),
                        branchName,
                        fatherName,
                        motherName,
                        marriagesCount.toString(),
                        childrenCount.toString(),
                    ).joinToString(",") { escapeCsv(it) }
                ).append("\r\n")
            }

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            sorted.size
        }
    }

    suspend fun exportTreePngToUri(
        uri: Uri,
        familyTitle: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        showHijri: Boolean,
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("تعذر فتح ملف تصدير الصورة PNG.")
            out.use { stream ->
                exportTreePngToStream(stream, familyTitle, persons, marriages, links, showHijri).getOrThrow()
            }
        }
    }

    suspend fun exportTreePngToStream(
        outputStream: OutputStream,
        familyTitle: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        showHijri: Boolean,
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val layout = TreeLayoutEngine.buildLayout(persons, marriages, links)
            val headerHeight = 140f
            val baseW = maxOf(layout.totalWidth, 900f)
            val baseH = maxOf(layout.totalHeight + headerHeight, 600f)
            val maxDim = 3600f
            val scale = minOf(1.6f, maxDim / maxOf(baseW, baseH))
            val bmpW = (baseW * scale).toInt().coerceIn(800, 3600)
            val bmpH = (baseH * scale).toInt().coerceIn(540, 3600)

            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.scale(scale, scale)

                // Deep Emerald top banner
                val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMERALD_DARK }
                canvas.drawRect(0f, 0f, baseW, headerHeight, bannerPaint)

                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 36f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFE6EFEA.toInt()
                    textSize = 20f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("شجرة $familyTitle", baseW / 2f, 58f, titlePaint)
                canvas.drawText(
                    "عدد الأفراد: ${persons.size} • الأجيال: ${layout.maxGeneration} • ${formatExportTimestamp()}",
                    baseW / 2f,
                    102f,
                    subPaint,
                )

                canvas.translate(0f, headerHeight)
                drawTreeLayoutOnCanvas(canvas, layout, showHijri)
                canvas.restore()

                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    "تعذر ضغط صورة الشجرة بصيغة PNG."
                }
                outputStream.flush()
                bmpW to bmpH
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun exportPdfToUri(
        uri: Uri,
        familyTitle: String,
        familyMotto: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        familyGroups: List<FamilyGroup>,
        showHijri: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("تعذر فتح ملف التصدير PDF.")
            out.use { stream ->
                exportPdfToStream(
                    stream,
                    familyTitle,
                    familyMotto,
                    persons,
                    marriages,
                    links,
                    familyGroups,
                    showHijri,
                ).getOrThrow()
            }
        }
    }

    suspend fun exportPdfToStream(
        outputStream: OutputStream,
        familyTitle: String,
        familyMotto: String,
        persons: List<Person>,
        marriages: List<Marriage>,
        links: List<ParentChildLink>,
        familyGroups: List<FamilyGroup>,
        showHijri: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stats = StatisticsEngine.compute(persons, marriages, links, familyGroups)
            val layout = TreeLayoutEngine.buildLayout(persons, marriages, links)
            val generationMap = GenealogyMetricsCalculator.computeGenerationByPerson(persons, links)
            val personsById = persons.associateBy { it.id }
            val groupsById = familyGroups.associateBy { it.id }
            val sortedPersons = persons.sortedWith(
                compareBy<Person>({ generationMap[it.id] ?: 1 }, { it.fullName })
            )
            val rowsPerPage = 10
            val chunks = if (sortedPersons.isEmpty()) listOf(emptyList()) else sortedPersons.chunked(rowsPerPage)

            val pageDrawers: List<(Canvas) -> Unit> = buildList {
                add { canvas -> drawPdfCoverAndStats(canvas, familyTitle, familyMotto, stats) }
                add { canvas -> drawPdfTreePage(canvas, familyTitle, layout, showHijri) }
                chunks.forEachIndexed { idx, chunk ->
                    add { canvas ->
                        drawPdfDirectoryPage(
                            canvas = canvas,
                            familyTitle = familyTitle,
                            personsChunk = chunk,
                            personsById = personsById,
                            links = links,
                            groupsById = groupsById,
                            generationMap = generationMap,
                            showHijri = showHijri,
                            directoryPageIndex = idx + 1,
                            directoryTotalPages = chunks.size,
                        )
                    }
                }
            }

            val nativeResult = runCatching {
                val document = PdfDocument()
                try {
                    pageDrawers.forEachIndexed { index, drawPage ->
                        val info = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                        val page = document.startPage(info)
                        drawPage(page.canvas)
                        document.finishPage(page)
                    }
                    document.writeTo(outputStream)
                    outputStream.flush()
                    pageDrawers.size
                } finally {
                    runCatching { document.close() }
                }
            }

            if (nativeResult.isSuccess) {
                nativeResult.getOrThrow()
            } else {
                val err = nativeResult.exceptionOrNull()
                if (err is IllegalStateException || err is UnsatisfiedLinkError) {
                    writeBitmapBackedPdf14(outputStream, pageDrawers)
                    pageDrawers.size
                } else {
                    throw err!!
                }
            }
        }
    }

    private fun writeBitmapBackedPdf14(
        outputStream: OutputStream,
        pageDrawers: List<(Canvas) -> Unit>,
    ) {
        val jpegScale = 2
        val imgW = A4_WIDTH * jpegScale
        val imgH = A4_HEIGHT * jpegScale
        val pageJpegs = pageDrawers.map { drawPage ->
            val bmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bmp)
                canvas.scale(jpegScale.toFloat(), jpegScale.toFloat())
                drawPage(canvas)
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                baos.toByteArray()
            } finally {
                bmp.recycle()
            }
        }

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

        val offsets = mutableListOf<Int>()
        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        val totalPages = pageJpegs.size
        // Object layout:
        // 1: Catalog, 2: Pages, then for each page i (0..N-1):
        //   3 + i*3: Page object
        //   4 + i*3: Contents stream
        //   5 + i*3: Image XObject
        val pageObjIds = (0 until totalPages).map { 3 + it * 3 }

        offsets.add(out.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(out.size())
        val kids = pageObjIds.joinToString(" ") { "$it 0 R" }
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $totalPages >>\nendobj\n")

        pageJpegs.forEachIndexed { idx, jpegBytes ->
            val pageId = 3 + idx * 3
            val contentId = pageId + 1
            val imageId = pageId + 2

            offsets.add(out.size())
            writeAscii(
                "$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $A4_WIDTH $A4_HEIGHT] " +
                    "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
            )

            val contentStream = "q\n$A4_WIDTH 0 0 $A4_HEIGHT 0 0 cm\n/Im0 Do\nQ\n"
            val contentBytes = contentStream.toByteArray(Charsets.US_ASCII)
            offsets.add(out.size())
            writeAscii("$contentId 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
            out.write(contentBytes)
            writeAscii("\nendstream\nendobj\n")

            offsets.add(out.size())
            writeAscii(
                "$imageId 0 obj\n<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
            )
            out.write(jpegBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = out.size()
        val objCount = offsets.size + 1
        writeAscii("xref\n0 $objCount\n0000000000 65535 f \n")
        offsets.forEach { offset ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        writeAscii("trailer\n<< /Size $objCount /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        outputStream.write(out.toByteArray())
        outputStream.flush()
    }

    private fun drawPdfCoverAndStats(
        canvas: Canvas,
        familyTitle: String,
        familyMotto: String,
        stats: com.example.domain.stats.FamilyStatisticsReport,
    ) {
        canvas.drawColor(Color.WHITE)

        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMERALD_DARK }
        canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 170f, heroPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE6EFEA.toInt()
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("سجل شجرة $familyTitle", A4_WIDTH - 36f, 70f, titlePaint)
        canvas.drawText(familyMotto, A4_WIDTH - 36f, 102f, subtitlePaint)
        canvas.drawText("تاريخ الإصدار: ${formatExportTimestamp()}", A4_WIDTH - 36f, 134f, subtitlePaint)

        val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الملخص الإحصائي العام", A4_WIDTH - 36f, 215f, sectionTitlePaint)

        val metricRows = listOf(
            "إجمالي الأفراد المسجلين" to stats.totalPersons.toString(),
            "الذكور / الإناث" to "${stats.malesCount} ذكر • ${stats.femalesCount} أنثى",
            "الأحياء / المتوفون" to "${stats.livingCount} على قيد الحياة • ${stats.deceasedCount} متوفى",
            "عدد الأجيال" to stats.generationsCount.toString(),
            "إجمالي الزيجات الموثقة" to stats.totalMarriagesCount.toString(),
            "متوسط الأعمار التقديري" to (stats.averageAgeYears?.let {
                "${String.format(Locale.US, "%.1f", it)} سنة"
            } ?: "غير متاح"),
            "أكبر المعمرين الأحياء" to (stats.oldestLivingPerson?.let {
                "${it.person.fullName} (${it.estimatedAgeYears} سنة)"
            } ?: "غير محدد"),
        )

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMERALD_TINT }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        var y = 235f
        metricRows.forEach { (label, value) ->
            val rect = RectF(36f, y, A4_WIDTH - 36f, y + 40f)
            canvas.drawRoundRect(rect, 8f, 8f, cardBg)
            canvas.drawRoundRect(rect, 8f, 8f, cardBorder)
            canvas.drawText(label, A4_WIDTH - 52f, y + 25f, labelPaint)
            canvas.drawText(value, 52f, y + 25f, valuePaint)
            y += 50f
        }

        y += 18f
        canvas.drawText("التوزيع حسب الأجيال", A4_WIDTH - 36f, y, sectionTitlePaint)
        y += 22f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMERALD }
        val maxGenCount = stats.generationDistribution.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        stats.generationDistribution.take(6).forEach { bucket ->
            canvas.drawText("الجيل ${bucket.generation}", A4_WIDTH - 36f, y + 14f, labelPaint)
            val barMaxW = 300f
            val barW = (bucket.count.toFloat() / maxGenCount) * barMaxW
            canvas.drawRoundRect(
                RectF(A4_WIDTH - 120f - barW, y + 2f, A4_WIDTH - 120f, y + 18f),
                4f,
                4f,
                barPaint,
            )
            canvas.drawText("${bucket.count} فرد", 52f, y + 14f, valuePaint)
            y += 28f
        }
    }

    private fun drawPdfTreePage(
        canvas: Canvas,
        familyTitle: String,
        layout: TreeLayoutResult,
        showHijri: Boolean,
    ) {
        canvas.drawColor(Color.WHITE)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMERALD }
        canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 66f, headerPaint)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("المخطط الشجري — $familyTitle", A4_WIDTH - 28f, 42f, titlePaint)

        if (layout.personNodes.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_MUTED
                textSize = 14f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد بيانات كافية لرسم الشجرة.", A4_WIDTH / 2f, 220f, emptyPaint)
            return
        }

        val availW = A4_WIDTH - 32f
        val availH = A4_HEIGHT - 96f
        val scale = minOf(availW / layout.totalWidth, availH / layout.totalHeight)
        val offsetX = (A4_WIDTH - layout.totalWidth * scale) / 2f
        val offsetY = 80f + (availH - layout.totalHeight * scale) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        drawTreeLayoutOnCanvas(canvas, layout, showHijri)
        canvas.restore()
    }

    private fun drawPdfDirectoryPage(
        canvas: Canvas,
        familyTitle: String,
        personsChunk: List<Person>,
        personsById: Map<String, Person>,
        links: List<ParentChildLink>,
        groupsById: Map<String, FamilyGroup>,
        generationMap: Map<String, Int>,
        showHijri: Boolean,
        directoryPageIndex: Int,
        directoryTotalPages: Int,
    ) {
        canvas.drawColor(Color.WHITE)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMERALD }
        canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 64f, headerPaint)
        val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "دليل أفراد $familyTitle ($directoryPageIndex/$directoryTotalPages)",
            A4_WIDTH - 28f,
            40f,
            headerTextPaint,
        )

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAF8F4.toInt() }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD_DARK
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK
            textSize = 10.5f
            textAlign = Paint.Align.RIGHT
        }
        val linksByChild = links.groupBy { it.childId }

        var y = 84f
        personsChunk.forEach { person ->
            val rect = RectF(28f, y, A4_WIDTH - 28f, y + 66f)
            canvas.drawRoundRect(rect, 8f, 8f, cardBg)
            canvas.drawRoundRect(rect, 8f, 8f, cardBorder)

            val gen = generationMap[person.id] ?: 1
            val birth = HijriDateConverter.formatDateForDisplay(
                person.birthDate,
                person.birthDatePrecision,
                showHijri,
            )
            val branch = person.familyGroupId?.let { groupsById[it]?.groupName } ?: "بدون فرع"
            val parentsText = linksByChild[person.id].orEmpty()
                .mapNotNull { personsById[it.parentId]?.firstName }
                .take(2)
                .joinToString(" و ")
                .ifBlank { "غير مرتبط" }

            canvas.drawText(
                "${person.fullName} — (${person.gender.arabicLabel} • الجيل $gen)",
                A4_WIDTH - 40f,
                y + 22f,
                namePaint,
            )
            canvas.drawText(
                "الميلاد: $birth • الحالة: ${person.currentMaritalStatus.arabicLabel} • الفرع: $branch",
                A4_WIDTH - 40f,
                y + 40f,
                detailPaint,
            )
            canvas.drawText(
                "الوالدان: $parentsText • المهنة: ${person.occupation ?: "غير محددة"}",
                A4_WIDTH - 40f,
                y + 56f,
                detailPaint,
            )
            y += 74f
        }
    }

    private fun drawTreeLayoutOnCanvas(
        canvas: Canvas,
        layout: TreeLayoutResult,
        showHijri: Boolean,
    ) {
        val solidEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val marriageEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD_ACCENT
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val dashedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            strokeWidth = 2.6f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        }

        layout.edges.forEach { edge ->
            val paint = when {
                edge.isMarriageEdge && edge.marriageStatus == MarriageRecordStatus.ACTIVE -> marriageEdgePaint
                edge.isUnconfirmedOrKafala || edge.isMarriageEdge -> dashedEdgePaint
                else -> solidEdgePaint
            }
            val midY = edge.railY ?: ((edge.startY + edge.endY) / 2f)
            canvas.drawLine(edge.startX, edge.startY, edge.startX, midY, paint)
            canvas.drawLine(edge.startX, midY, edge.endX, midY, paint)
            canvas.drawLine(edge.endX, midY, edge.endX, edge.endY, paint)
        }

        val unionDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD_ACCENT }
        layout.unionNodes.forEach { union ->
            canvas.drawCircle(union.x, union.y, 7f, unionDotPaint)
        }

        val maleCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val femaleCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSEWOOD_TINT }
        val maleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val femaleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROSEWOOD_FEMALE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }

        layout.personNodes.forEach { node ->
            val rect = RectF(
                node.x,
                node.y,
                node.x + TreeLayoutEngine.PERSON_CARD_WIDTH,
                node.y + TreeLayoutEngine.PERSON_CARD_HEIGHT,
            )
            val fill = if (node.person.gender == Gender.MALE) maleCardPaint else femaleCardPaint
            val stroke = if (node.person.gender == Gender.MALE) maleStrokePaint else femaleStrokePaint
            canvas.drawRoundRect(rect, 16f, 16f, fill)
            canvas.drawRoundRect(rect, 16f, 16f, stroke)

            val centerX = rect.centerX()
            val clippedName = TextUtils.ellipsize(
                node.person.fullName,
                namePaint,
                TreeLayoutEngine.PERSON_CARD_WIDTH - 20f,
                TextUtils.TruncateAt.END,
            ).toString()
            canvas.drawText(clippedName, centerX, rect.top + 38f, namePaint)

            val birthText = HijriDateConverter.formatDateForDisplay(
                node.person.birthDate,
                node.person.birthDatePrecision,
                includeHijri = showHijri,
            )
            val clippedMeta = TextUtils.ellipsize(
                "ج${node.generation} • $birthText",
                metaPaint,
                TreeLayoutEngine.PERSON_CARD_WIDTH - 20f,
                TextUtils.TruncateAt.END,
            ).toString()
            canvas.drawText(clippedMeta, centerX, rect.top + 68f, metaPaint)

            if (node.person.isDeceased) {
                canvas.drawText("رحمه الله", centerX, rect.top + 90f, metaPaint)
            }
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun formatExportTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    }
}
