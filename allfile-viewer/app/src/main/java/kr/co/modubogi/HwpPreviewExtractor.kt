package kr.co.modubogi

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.apache.poi.poifs.filesystem.DocumentEntry
import org.apache.poi.poifs.filesystem.DocumentInputStream
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** HWP 5.x OLE 컨테이너에 저장된 내장 미리보기 이미지와 텍스트를 읽는다. */
object HwpPreviewExtractor {
    data class Result(
        val image: Bitmap?,
        val previewText: String?,
        val hasFileHeader: Boolean
    )

    fun extract(resolver: ContentResolver, uri: Uri): Result {
        resolver.openInputStream(uri)?.use { input ->
            POIFSFileSystem(input).use { fileSystem ->
                val root = fileSystem.root
                val imageBytes = readDocument(root, "PrvImage")
                val textBytes = readDocument(root, "PrvText")
                val image = imageBytes?.let(::decodePreviewBitmap)
                val text = textBytes?.let(::decodePreviewText)?.takeIf { it.isNotBlank() }
                return Result(
                    image = image,
                    previewText = text,
                    hasFileHeader = root.hasEntry("FileHeader")
                )
            }
        }
        throw IllegalStateException("HWP 파일 스트림을 열 수 없습니다.")
    }

    private fun readDocument(directory: org.apache.poi.poifs.filesystem.DirectoryEntry, name: String): ByteArray? {
        if (!directory.hasEntry(name)) return null
        val entry = directory.getEntry(name) as? DocumentEntry ?: return null
        return DocumentInputStream(entry).use { stream -> stream.readBytes() }
    }

    private fun decodePreviewText(bytes: ByteArray): String {
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            else -> bytes.toString(Charsets.UTF_16LE)
        }
        return text.replace("\u0000", "").trim()
    }

    private fun decodePreviewBitmap(bytes: ByteArray): Bitmap? {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
        val bmpBytes = addBmpFileHeaderIfNeeded(bytes) ?: return null
        return BitmapFactory.decodeStream(ByteArrayInputStream(bmpBytes))
    }

    /** 일부 HWP는 BMP 파일 헤더 없이 DIB 데이터만 저장하므로 14바이트 헤더를 보완한다. */
    private fun addBmpFileHeaderIfNeeded(bytes: ByteArray): ByteArray? {
        if (bytes.size < 40) return null
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return bytes

        val dib = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val dibHeaderSize = dib.getInt(0)
        if (dibHeaderSize !in 40..124 || bytes.size < dibHeaderSize) return null

        val bitCount = dib.getShort(14).toInt() and 0xFFFF
        val colorsUsed = if (bytes.size >= 36) dib.getInt(32).coerceAtLeast(0) else 0
        val paletteEntries = when {
            colorsUsed > 0 -> colorsUsed
            bitCount in 1..8 -> 1 shl bitCount
            else -> 0
        }
        val pixelOffset = 14 + dibHeaderSize + paletteEntries * 4
        if (pixelOffset > bytes.size + 14) return null

        val output = ByteArray(bytes.size + 14)
        output[0] = 'B'.code.toByte()
        output[1] = 'M'.code.toByte()
        val header = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(2, output.size)
        header.putInt(10, pixelOffset)
        bytes.copyInto(output, destinationOffset = 14)
        return output
    }
}
