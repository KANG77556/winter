package kr.co.modubogi

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/** 앱 내부 캐시의 가상 문서 변환 결과를 읽기 전용으로 제공한다. */
class CacheDocumentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "kr.co.modubogi.cache"

        fun uriFor(file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath("virtual")
            .appendPath(file.name)
            .build()
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "pdf" -> "application/pdf"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/octet-stream"
            }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = resolveFile(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode.contains('w')) throw FileNotFoundException("읽기 전용 파일입니다.")
        val file = resolveFile(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolveFile(uri: Uri): File {
        val context = context ?: throw FileNotFoundException("앱 컨텍스트가 없습니다.")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("파일명이 없습니다.")
        val directory = File(context.cacheDir, "virtual_documents").canonicalFile
        val file = File(directory, name).canonicalFile
        if (!file.path.startsWith(directory.path + File.separator) || !file.isFile) {
            throw FileNotFoundException("파일을 찾을 수 없습니다.")
        }
        return file
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
