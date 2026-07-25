package kr.co.modubogi

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class XlsxWorkbook(val sheets: List<XlsxSheet>)

data class XlsxSheet(
    val name: String,
    val cells: Map<CellAddress, String>,
    val maxRow: Int,
    val maxColumn: Int,
    val horizontalMerges: Map<CellAddress, Int>
)

data class CellAddress(val row: Int, val column: Int)

object XlsxParser {
    private const val MAX_TOTAL_BYTES = 40 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRIES = 2_000

    fun parse(resolver: ContentResolver, uri: Uri): XlsxWorkbook {
        val entries = readEntries(resolver, uri)
        val workbookXml = entries["xl/workbook.xml"]
            ?: error("XLSX 통합문서 정보를 찾을 수 없습니다.")
        val relationXml = entries["xl/_rels/workbook.xml.rels"]
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val relationships = relationXml?.let(::parseRelationships).orEmpty()
        val sheetDefinitions = parseWorkbook(workbookXml)

        val sheets = sheetDefinitions.mapIndexedNotNull { index, definition ->
            val relatedTarget = relationships[definition.relationshipId]
            val normalizedPath = normalizeWorksheetPath(relatedTarget)
            val fallbackPath = "xl/worksheets/sheet${index + 1}.xml"
            val sheetBytes = entries[normalizedPath] ?: entries[fallbackPath] ?: return@mapIndexedNotNull null
            parseSheet(definition.name, sheetBytes, sharedStrings)
        }

        if (sheets.isEmpty()) error("표시할 수 있는 시트를 찾지 못했습니다.")
        return XlsxWorkbook(sheets)
    }

    private fun readEntries(resolver: ContentResolver, uri: Uri): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0
        var entryCount = 0

        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) error("XLSX 내부 항목이 너무 많습니다.")
                    val safeName = entry.name.replace('\\', '/').removePrefix("/")
                    if (safeName.split('/').contains("..")) error("안전하지 않은 내부 경로가 포함되어 있습니다.")

                    if (!entry.isDirectory && shouldRead(safeName)) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8_192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (output.size() + read > MAX_ENTRY_BYTES) error("XLSX 내부 파일이 너무 큽니다: $safeName")
                            output.write(buffer, 0, read)
                        }
                        val bytes = output.toByteArray()
                        totalBytes += bytes.size
                        if (totalBytes > MAX_TOTAL_BYTES) error("XLSX 분석 데이터가 허용 크기를 초과했습니다.")
                        result[safeName] = bytes
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("파일을 읽을 수 없습니다.")
        return result
    }

    private fun shouldRead(name: String): Boolean =
        name == "xl/workbook.xml" ||
            name == "xl/_rels/workbook.xml.rels" ||
            name == "xl/sharedStrings.xml" ||
            name.startsWith("xl/worksheets/") && name.endsWith(".xml")

    private data class SheetDefinition(val name: String, val relationshipId: String)

    private fun parseWorkbook(bytes: ByteArray): List<SheetDefinition> {
        val parser = newParser(bytes)
        val result = ArrayList<SheetDefinition>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "시트 ${result.size + 1}"
                var relationshipId = ""
                for (index in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(index) == "id") {
                        relationshipId = parser.getAttributeValue(index)
                        break
                    }
                }
                result.add(SheetDefinition(name, relationshipId))
            }
            parser.next()
        }
        return result
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val parser = newParser(bytes)
        val result = HashMap<String, String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) result[id] = target
            }
            parser.next()
        }
        return result
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val result = ArrayList<String>()
        var insideItem = false
        var insideText = false
        var builder = StringBuilder()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        insideItem = true
                        builder = StringBuilder()
                    }
                    "t" -> if (insideItem) insideText = true
                }
                XmlPullParser.TEXT -> if (insideItem && insideText) builder.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> insideText = false
                    "si" -> {
                        result.add(builder.toString())
                        insideItem = false
                    }
                }
            }
            parser.next()
        }
        return result
    }

    private fun parseSheet(name: String, bytes: ByteArray, sharedStrings: List<String>): XlsxSheet {
        val parser = newParser(bytes)
        val cells = LinkedHashMap<CellAddress, String>()
        val horizontalMerges = HashMap<CellAddress, Int>()

        var currentReference: String? = null
        var currentType: String? = null
        var currentRawValue: String? = null
        var currentInlineValue = StringBuilder()
        var currentFormula: String? = null
        var insideValue = false
        var insideInlineText = false
        var insideFormula = false
        var maxRow = 0
        var maxColumn = 0

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        currentReference = parser.getAttributeValue(null, "r")
                        currentType = parser.getAttributeValue(null, "t")
                        currentRawValue = null
                        currentInlineValue = StringBuilder()
                        currentFormula = null
                    }
                    "v" -> insideValue = true
                    "t" -> if (currentReference != null) insideInlineText = true
                    "f" -> if (currentReference != null) insideFormula = true
                    "mergeCell" -> {
                        val range = parser.getAttributeValue(null, "ref")
                        parseHorizontalMerge(range)?.let { (address, span) -> horizontalMerges[address] = span }
                    }
                }
                XmlPullParser.TEXT -> when {
                    insideValue -> currentRawValue = (currentRawValue ?: "") + parser.text
                    insideInlineText -> currentInlineValue.append(parser.text)
                    insideFormula -> currentFormula = (currentFormula ?: "") + parser.text
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> insideValue = false
                    "t" -> insideInlineText = false
                    "f" -> insideFormula = false
                    "c" -> {
                        val address = currentReference?.let(::parseCellAddress)
                        if (address != null) {
                            val value = resolveCellValue(currentType, currentRawValue, currentInlineValue.toString(), currentFormula, sharedStrings)
                            if (value.isNotEmpty()) cells[address] = value
                            maxRow = maxOf(maxRow, address.row)
                            maxColumn = maxOf(maxColumn, address.column)
                        }
                        currentReference = null
                    }
                }
            }
            parser.next()
        }

        horizontalMerges.forEach { (address, span) ->
            maxRow = maxOf(maxRow, address.row)
            maxColumn = maxOf(maxColumn, address.column + span - 1)
        }

        return XlsxSheet(
            name = name,
            cells = cells,
            maxRow = maxOf(1, maxRow),
            maxColumn = maxOf(1, maxColumn),
            horizontalMerges = horizontalMerges
        )
    }

    private fun resolveCellValue(
        type: String?,
        raw: String?,
        inline: String,
        formula: String?,
        sharedStrings: List<String>
    ): String {
        return when (type) {
            "s" -> raw?.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
            "inlineStr" -> inline
            "b" -> if (raw == "1") "TRUE" else "FALSE"
            "e" -> raw?.let { "#$it" }.orEmpty()
            "str" -> raw.orEmpty()
            else -> when {
                !raw.isNullOrBlank() -> raw
                !inline.isBlank() -> inline
                !formula.isNullOrBlank() -> "=$formula"
                else -> ""
            }
        }
    }

    private fun parseCellAddress(reference: String): CellAddress? {
        val letters = reference.takeWhile { it.isLetter() }
        val numbers = reference.dropWhile { it.isLetter() }.takeWhile { it.isDigit() }
        if (letters.isBlank() || numbers.isBlank()) return null
        var column = 0
        letters.uppercase(Locale.ROOT).forEach { char -> column = column * 26 + (char - 'A' + 1) }
        val row = numbers.toIntOrNull() ?: return null
        return CellAddress(row, column)
    }

    private fun parseHorizontalMerge(range: String?): Pair<CellAddress, Int>? {
        if (range.isNullOrBlank() || ':' !in range) return null
        val start = parseCellAddress(range.substringBefore(':')) ?: return null
        val end = parseCellAddress(range.substringAfter(':')) ?: return null
        if (start.row != end.row || end.column < start.column) return null
        return start to (end.column - start.column + 1)
    }

    private fun normalizeWorksheetPath(target: String?): String {
        if (target.isNullOrBlank()) return ""
        val clean = target.replace('\\', '/').removePrefix("/")
        return when {
            clean.startsWith("xl/") -> clean
            clean.startsWith("../") -> clean.removePrefix("../")
            else -> "xl/$clean"
        }
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return parser
    }
}
