package kr.co.modubogi

import android.content.ContentResolver
import android.net.Uri
import java.util.Locale

enum class ViewerType {
    PDF, IMAGE, TEXT, VIDEO, AUDIO, ZIP, OFFICE, HWP, UNKNOWN
}

data class DetectedFileType(
    val viewerType: ViewerType,
    val description: String
)

object FileTypeDetector {
    private val textExtensions = setOf(
        "txt", "log", "csv", "json", "xml", "yaml", "yml", "md", "ini", "conf",
        "html", "htm", "css", "js", "ts", "kt", "java", "py", "sql", "sh", "bat"
    )
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp")
    private val audioExtensions = setOf("mp3", "aac", "m4a", "wav", "flac", "ogg", "opus")
    private val officeExtensions = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf")
    private val hwpExtensions = setOf("hwp", "hwpx", "hwt", "hwtx")

    fun detect(resolver: ContentResolver, uri: Uri, name: String, mime: String?): DetectedFileType {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

        if (extension in officeExtensions) return DetectedFileType(ViewerType.OFFICE, "Office 문서")
        if (extension in hwpExtensions) return DetectedFileType(ViewerType.HWP, "한글 문서")

        val header = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ByteArray(16).also { stream.read(it) }
            }
        }.getOrNull()

        if (header != null) {
            val ascii = header.toString(Charsets.ISO_8859_1)
            if (ascii.startsWith("%PDF")) return DetectedFileType(ViewerType.PDF, "PDF 문서")
            if (header.startsWithBytes(0x89, 0x50, 0x4E, 0x47)) return DetectedFileType(ViewerType.IMAGE, "PNG 이미지")
            if (header.startsWithBytes(0xFF, 0xD8, 0xFF)) return DetectedFileType(ViewerType.IMAGE, "JPEG 이미지")
            if (ascii.startsWith("GIF8")) return DetectedFileType(ViewerType.IMAGE, "GIF 이미지")
            if (ascii.startsWith("BM")) return DetectedFileType(ViewerType.IMAGE, "BMP 이미지")
            if (ascii.startsWith("PK")) return DetectedFileType(ViewerType.ZIP, "ZIP 압축파일")
        }

        when {
            extension == "pdf" || mime == "application/pdf" -> return DetectedFileType(ViewerType.PDF, "PDF 문서")
            extension in imageExtensions || mime?.startsWith("image/") == true -> return DetectedFileType(ViewerType.IMAGE, "이미지")
            extension in videoExtensions || mime?.startsWith("video/") == true -> return DetectedFileType(ViewerType.VIDEO, "영상")
            extension in audioExtensions || mime?.startsWith("audio/") == true -> return DetectedFileType(ViewerType.AUDIO, "음원")
            extension == "zip" || mime == "application/zip" -> return DetectedFileType(ViewerType.ZIP, "ZIP 압축파일")
            extension in textExtensions || mime?.startsWith("text/") == true -> return DetectedFileType(ViewerType.TEXT, "텍스트")
        }
        return DetectedFileType(ViewerType.UNKNOWN, "알 수 없는 파일")
    }

    private fun ByteArray.startsWithBytes(vararg expected: Int): Boolean {
        if (size < expected.size) return false
        return expected.indices.all { index -> (this[index].toInt() and 0xFF) == expected[index] }
    }
}
