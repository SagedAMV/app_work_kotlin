package com.majarra.galaxy.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * نظام التصدير — بدون أي مكتبة خارجية تتطلب إنترنت:
 * - CSV: نص مع بادئة UTF-8 BOM ليُفتح صحيحًا في إكسل.
 * - Excel: صيغة SpreadsheetML (تفتح في إكسل مباشرة).
 * - PDF: عبر PdfDocument المدمج في أندرويد.
 */

/** بناء ملف CSV من رؤوس وصفوف */
object CsvExporter {
    fun build(headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder("\uFEFF") // BOM لدعم العربية في إكسل
        sb.appendLine(headers.joinToString(",") { escape(it) })
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escape(it) })
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n')
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
        sb.appendLine(" <Worksheet ss:Name=\"${xml(sheetName)}\"><Table>")
        sb.appendLine(rowXml(headers))
        rows.forEach { sb.appendLine(rowXml(it)) }
        sb.appendLine(" </Table></Worksheet>")
        sb.appendLine("</Workbook>")
        return sb.toString()
    }

    private fun rowXml(cells: List<String>): String =
        "  <Row>" + cells.joinToString("") {
            "<Cell><Data ss:Type=\"String\">${xml(it)}</Data></Cell>"
        } + "</Row>"

    private fun xml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

/** بناء تقرير PDF بصفحات A4 مع دعم التفاف النصوص ومحاذاة يمينًا (RTL) */
object PdfReportExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f
    private const val ROW_HEIGHT = 20f

    fun build(title: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 56f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val headerBg = Paint().apply { color = Color.parseColor("#0B3B54") }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(title, PAGE_W - MARGIN, y, titlePaint)
        y += 30f

        val colCount = headers.size.coerceAtLeast(1)
        val colWidth = (PAGE_W - MARGIN * 2) / colCount

        fun drawHeaderRow() {
            canvas.drawRect(MARGIN, y - 14f, PAGE_W - MARGIN, y + 4f, headerBg)
            headers.forEachIndexed { i, h ->
                // الرسم من اليمين إلى اليسار: أول عمود في أقصى اليمين
                val cx = PAGE_W - MARGIN - colWidth * (i + 0.5f)
                canvas.drawText(truncate(h, 22), cx, y, headerPaint)
            }
            y += ROW_HEIGHT + 8f
        }

        drawHeaderRow()
        for (row in rows) {
            if (y > PAGE_H - MARGIN) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 56f
                drawHeaderRow()
            }
            row.forEachIndexed { i, cell ->
                val cx = PAGE_W - MARGIN - colWidth * (i + 0.5f)
                canvas.drawText(truncate(cell, 26), cx, y, bodyPaint)
            }
            y += ROW_HEIGHT
        }

        document.finishPage(page)
        val out = java.io.ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"
}
