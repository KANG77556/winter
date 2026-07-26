package kr.co.modubogi

import android.content.ContentResolver
import android.net.Uri
import kr.dogfoot.hwplib.reader.HWPReader
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor

/** HWP 본문 전체를 읽고 모바일 화면용 내용 페이지로 나눈다. */
object HwpFullContentExtractor {
    data class Result(
        val pages: List<String>,
        val sectionCount: Int,
        val characterCount: Int
    )

    fun extract(resolver: ContentResolver, uri: Uri): Result {
        val hwpFile = resolver.openInputStream(uri)?.use { input ->
            HWPReader.fromInputStream(input)
        } ?: throw IllegalStateException("HWP 파일 스트림을 열 수 없습니다.")

        val extracted = TextExtractor.extract(
            hwpFile,
            TextExtractMethod.InsertControlTextBetweenParagraphText
        )
        val normalized = normalize(extracted)
        val pages = paginate(normalized)
        return Result(
            pages = pages.ifEmpty { listOf("추출할 수 있는 본문 내용이 없습니다.") },
            sectionCount = hwpFile.bodyText.sectionList.size,
            characterCount = normalized.length
        )
    }

    private fun normalize(value: String): String {
        return value
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{4,}"), "\n\n\n")
            .trim()
    }

    /**
     * 실제 한컴 조판 페이지가 아니라 모바일 열람을 위한 재구성 페이지다.
     * 문단을 가능한 한 쪼개지 않되 지나치게 긴 문단은 안전하게 분할한다.
     */
    private fun paginate(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val maxCharacters = 1800
        val maxLines = 42
        val pages = ArrayList<String>()
        val page = StringBuilder()
        var lineCount = 0

        fun flush() {
            val content = page.toString().trim()
            if (content.isNotEmpty()) pages.add(content)
            page.clear()
            lineCount = 0
        }

        text.split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd()
            val estimatedLines = maxOf(1, (line.length + 36) / 37)
            if (page.isNotEmpty() && (page.length + line.length + 1 > maxCharacters || lineCount + estimatedLines > maxLines)) {
                flush()
            }

            if (line.length <= maxCharacters) {
                page.append(line).append('\n')
                lineCount += estimatedLines
            } else {
                var start = 0
                while (start < line.length) {
                    val remaining = line.length - start
                    val take = minOf(maxCharacters, remaining)
                    if (page.isNotEmpty()) flush()
                    page.append(line.substring(start, start + take))
                    lineCount = maxLines
                    flush()
                    start += take
                }
            }
        }
        if (page.isNotEmpty()) flush()
        return pages
    }
}
