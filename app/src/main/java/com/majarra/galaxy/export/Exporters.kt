package com.majarra.galaxy.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/**
 * نظام التصدير — بدون أي مكتبة خارجية تتطلب إنترنت:
 * - CSV: نص مع بادئة UTF-8 BOM ليُفتح صحيحًا في إكسل.
 * - Excel: صيغة SpreadsheetML (تفتح في إكسل مباشرة).
 * - PDF: عبر PdfDocument المدمج في أندرويد.
 */

/** إزالة محارف التحكم غير المسموح بها في XML/CSV (تسبب فشل الفتح في إكسل) */
private fun String.stripControlChars(): String =
    this.filter { ch -> ch == '\n' || ch == '\t' || (ch >= ' ' && ch != '\u007F') }

object CsvExporter {
    fun build(headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder("\uFEFF") // BOM لدعم العربية في إكسل
        sb.appendLine(encodeRow(headers))
        rows.forEach { sb.appendLine(encodeRow(it)) }
        return sb.toString()
    }

    private fun encodeRow(cells: List<String>): String =
        cells.joinToString(",") { escape(it.stripControlChars().replace("\n", " ")) }

    private fun escape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains(';') ||
            value.startsWith(" ") || value.endsWith(" ")
        return if (needs) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}

/** بناء ملف بصيغة SpreadsheetML يفتح في إكسل */
object ExcelXmlExporter {
    fun build(sheetName: String, headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
        sb.appendLine(
            "<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" " +
                "xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">"
        )
        sb.appendLine(
            " <Styles><Style ss:ID=\"hdr\"><Font ss:Bold=\"1\"/>" +
                "<Interior ss:Color=\"#0B3B54\" ss:Pattern=\"Solid\"/>" +
                "<Font ss:Color=\"#FFFFFF\" ss:Bold=\"1\"/></Style></Styles>"
        )
        sb.appendLine(" <Worksheet ss:Name=\"${xml(sheetName)}\"><Table>")
        repeat(headers.size.coerceAtLeast(1)) { sb.appendLine("  <Column ss:Width=\"140\"/>") }
        sb.appendLine(rowXml(headers, header = true))
        rows.forEach { sb.appendLine(rowXml(it)) }
        sb.appendLine(" </Table></Worksheet>")
        sb.appendLine("</Workbook>")
        return sb.toString()
    }

    private fun rowXml(cells: List<String>, header: Boolean = false): String {
        val style = if (header) " ss:StyleID=\"hdr\"" else ""
        return "  <Row>" + cells.joinToString("") {
            "<Cell$style><Data ss:Type=\"String\">${xml(it)}</Data></Cell>"
        } + "</Row>"
    }

    private fun xml(s: String): String = s.stripControlChars()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

/**
 * بناء تقرير PDF بصفحات A4 بدعم عربي حقيقي.
 *
 * تصليب جلسة الفحص: الرسم السابق كان يستخدم `canvas.drawText` مباشرة، وهي
 * لا تُشكّل الحروف العربية ولا تعالج الاتجاه ثنائي الاتجاه، فكان النص العربي
 * يظهر مفككًا ومعكوسًا. الحل: StaticLayout + TextDirectionHeuristics.RTL الذي
 * يمرّ على محرك تشكيل النصوص ويطبّق التفاف النص تلقائيًا. كذلك أُضيف ترقيم
 * الصفحات وإعادة رسم الترويسة عند استمرار الجدول على عدة صفحات.
 */
object PdfReportExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 30f
    private const val CELL_PAD = 5f
    private const val MIN_ROW_H = 24f
    private const val HEADER_BG = "#0B3B54"
    private const val ZEBRA_BG = "#F1F5F9"
    private const val GRID = "#CBD5E1"

    fun build(title: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        val document = PdfDocument()
        val colCount = headers.size.coerceAtLeast(1)
        val tableWidth = PAGE_W - MARGIN * 2
        val colWidth = tableWidth / colCount

        val titlePaint = textPaint(16f, bold = true, color = Color.BLACK)
        val headerPaint = textPaint(10f, bold = true, color = Color.WHITE)
        val bodyPaint = textPaint(9.5f, color = Color.BLACK)
        val footerPaint = textPaint(8f, color = Color.parseColor("#64748B"))
        val headerBg = Paint().apply { color = Color.parseColor(HEADER_BG) }
        val zebraBg = Paint().apply { color = Color.parseColor(ZEBRA_BG) }
        val gridPaint = Paint().apply {
            color = Color.parseColor(GRID)
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas
        var y = MARGIN + 4f

        fun drawFooter() {
            val layout = rtlLayout("مجرة — صفحة $pageNumber", footerPaint, tableWidth.toInt())
            canvas.save()
            canvas.translate(MARGIN, PAGE_H - MARGIN + 2f)
            layout.draw(canvas)
            canvas.restore()
        }

        fun newPage() {
            drawFooter()
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            y = MARGIN + 4f
        }

        fun drawRow(cells: List<String>, paint: TextPaint, background: Paint?, isHeader: Boolean) {
            val layouts = cells.map { cell ->
                rtlLayout(cell.stripControlChars(), paint, (colWidth - CELL_PAD * 2).toInt())
            }
            val rowHeight = (layouts.maxOf { it.height.toFloat() } + CELL_PAD * 2)
                .coerceAtLeast(MIN_ROW_H)
            if (y + rowHeight > PAGE_H - MARGIN - 14f) {
                newPage()
                if (!isHeader) drawRow(headers, headerPaint, headerBg, isHeader = true)
            }
            background?.let { canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowHeight, it) }
            canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowHeight, gridPaint)
            layouts.forEachIndexed { index, layout ->
                // الرسم من اليمين إلى اليسار: أول عمود في أقصى اليمين
                canvas.save()
                canvas.translate(PAGE_W - MARGIN - colWidth * (index + 1) + CELL_PAD, y + CELL_PAD)
                layout.draw(canvas)
                canvas.restore()
                val dividerX = PAGE_W - MARGIN - colWidth * (index + 1)
                canvas.drawLine(dividerX, y, dividerX, y + rowHeight, gridPaint)
            }
            y += rowHeight
        }

        val titleLayout = rtlLayout(title, titlePaint, tableWidth.toInt())
        canvas.save()
        canvas.translate(MARGIN, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 10f

        drawRow(headers, headerPaint, headerBg, isHeader = true)
        rows.forEachIndexed { index, row ->
            drawRow(
                List(colCount) { i -> row.getOrElse(i) { "" } },
                bodyPaint,
                if (index % 2 == 1) zebraBg else null,
                isHeader = false
            )
        }

        drawFooter()
        document.finishPage(page)

        val out = java.io.ByteArrayOutputStream()
        try {
            document.writeTo(out)
        } finally {
            document.close()
        }
        return out.toByteArray()
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** تخطيط نص عربي RTL مع التفاف تلقائي داخل عرض محدد */
    private fun rtlLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(16))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setLineSpacing(1.15f, 1f)
            .build()
}
