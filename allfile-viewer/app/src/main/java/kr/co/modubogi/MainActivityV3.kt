package kr.co.modubogi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 모든 일반 파일과 Google Drive 가상 문서를 선택하는 진입 화면.
 * 가상 문서는 제공자가 지원하는 PDF/XLSX 등의 대체 스트림으로 변환한 뒤 기존 내부 렌더러에 전달한다.
 */
class MainActivityV3 : Activity() {
    private val requestOpenFile = 3001
    private val blue = Color.rgb(21, 87, 176)
    private lateinit var messageView: TextView
    private lateinit var openButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = blue
        window.navigationBarColor = Color.WHITE
        buildUi()
        cleanupOldVirtualFiles()

        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::prepareSelectedUri)
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.getItemAt(0)?.uri
                uri?.let(::prepareSelectedUri)
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setOnApplyWindowInsetsListener { view, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    top = bars.top
                    bottom = bars.bottom
                } else {
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                view.setPadding(0, top, 0, bottom)
                insets
            }
        }

        val title = TextView(this).apply {
            text = "모두보기"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            setBackgroundColor(blue)
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(30), dp(28), dp(30))
        }
        body.addView(TextView(this).apply {
            text = "일반 파일과 Drive 문서를 한곳에서"
            textSize = 24f
            setTextColor(blue)
            gravity = Gravity.CENTER
        })
        body.addView(TextView(this).apply {
            text = "PDF · 이미지 · 텍스트 · XLSX · 영상 · 음원 · ZIP뿐 아니라\nGoogle 문서·스프레드시트·슬라이드도 선택할 수 있습니다."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(20))
        })

        progressBar = ProgressBar(this).apply { visibility = ProgressBar.GONE }
        body.addView(progressBar, LinearLayout.LayoutParams(dp(48), dp(48)))

        messageView = TextView(this).apply {
            text = "가상 문서는 Drive가 제공하는 PDF 또는 Office 형식으로 읽기 전용 변환됩니다."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(22))
        }
        body.addView(messageView)

        openButton = Button(this).apply {
            text = "모든 파일 열기"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { launchFilePicker() }
        }
        body.addView(openButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

        body.addView(Button(this).apply {
            text = "기존 뷰어 열기"
            textSize = 16f
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivityV3, MainActivityV2::class.java)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(10) })

        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun launchFilePicker() {
        // CATEGORY_OPENABLE을 넣지 않아야 FLAG_VIRTUAL_DOCUMENT 항목도 선택할 수 있다.
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, requestOpenFile)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOpenFile && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prepareSelectedUri(uri)
        }
    }

    private fun prepareSelectedUri(uri: Uri) {
        setBusy(true, "파일 정보를 확인하고 있습니다…")
        thread(name = "virtual-document-check") {
            runCatching {
                if (isVirtualDocument(uri)) materializeVirtualDocument(uri) else PreparedDocument(uri, null)
            }.onSuccess { prepared ->
                runOnUiThread {
                    setBusy(false, prepared.note ?: "파일을 내부 뷰어로 엽니다.")
                    openViewer(prepared.uri)
                }
            }.onFailure { error ->
                runOnUiThread {
                    setBusy(false, "파일을 열지 못했습니다.")
                    AlertDialog.Builder(this)
                        .setTitle("파일 열기 실패")
                        .setMessage("${error.message ?: "대체 파일 형식을 가져올 수 없습니다."}\n\n원본 파일은 변경되지 않았습니다.")
                        .setPositiveButton("확인", null)
                        .show()
                }
            }
        }
    }

    private fun openViewer(uri: Uri) {
        startActivity(Intent(this, MainActivityV2::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("선택한 파일", uri)
        })
    }

    private fun isVirtualDocument(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                if (index < 0) false
                else cursor.getInt(index) and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0
            } ?: false
        }.getOrDefault(false)
    }

    private fun materializeVirtualDocument(uri: Uri): PreparedDocument {
        val originalName = queryDisplayName(uri)
        val originalMime = contentResolver.getType(uri)
        val available = contentResolver.getStreamTypes(uri, "*/*")?.toList().orEmpty()
        if (available.isEmpty()) {
            throw IllegalStateException("이 Drive 문서는 앱에서 읽을 수 있는 대체 형식을 제공하지 않습니다.")
        }

        val selectedMime = chooseMime(originalMime, available)
        val extension = extensionFor(selectedMime)
        val baseName = sanitizeFileName(originalName.substringBeforeLast('.', originalName))
        val directory = File(cacheDir, "virtual_documents").apply { mkdirs() }
        val target = uniqueTarget(directory, "$baseName.$extension")

        contentResolver.openTypedAssetFileDescriptor(uri, selectedMime, null)?.use { descriptor ->
            descriptor.createInputStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 250L * 1024L * 1024L) {
                            throw IllegalStateException("가상 문서 변환 결과가 250MB를 초과해 중단했습니다.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } ?: throw IllegalStateException("Drive에서 대체 파일 스트림을 열 수 없습니다.")

        if (target.length() == 0L) {
            target.delete()
            throw IllegalStateException("Drive가 빈 변환 파일을 반환했습니다.")
        }

        val description = when (extension) {
            "pdf" -> "PDF"
            "xlsx" -> "XLSX"
            "docx" -> "DOCX"
            "pptx" -> "PPTX"
            else -> extension.uppercase(Locale.ROOT)
        }
        return PreparedDocument(
            CacheDocumentProvider.uriFor(target),
            "Drive 가상 문서를 $description 형식으로 읽기 전용 변환했습니다."
        )
    }

    private fun chooseMime(originalMime: String?, available: List<String>): String {
        fun firstMatching(vararg candidates: String): String? = candidates.firstOrNull { it in available }
        return when {
            originalMime?.contains("spreadsheet") == true -> firstMatching(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/pdf",
                "text/csv"
            )
            originalMime?.contains("presentation") == true -> firstMatching(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )
            originalMime?.contains("document") == true -> firstMatching(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
            )
            else -> null
        } ?: firstMatching(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "image/png",
            "image/jpeg"
        ) ?: available.first()
    }

    private fun extensionFor(mime: String): String = when (mime) {
        "application/pdf" -> "pdf"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "text/csv" -> "csv"
        "text/plain" -> "txt"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        else -> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "Drive 문서"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80)
        return cleaned.ifBlank { "Drive_문서" }
    }

    private fun uniqueTarget(directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var target = File(directory, name)
        var index = 1
        while (target.exists()) {
            target = File(directory, "${base}_${index++}.$extension")
        }
        return target
    }

    private fun cleanupOldVirtualFiles() {
        thread(name = "virtual-cache-clean") {
            val directory = File(cacheDir, "virtual_documents")
            if (!directory.isDirectory) return@thread
            val files = directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
            val sevenDays = 7L * 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            files.forEachIndexed { index, file ->
                if (index >= 20 || now - file.lastModified() > sevenDays) file.delete()
            }
        }
    }

    private fun setBusy(busy: Boolean, message: String) {
        openButton.isEnabled = !busy
        progressBar.visibility = if (busy) ProgressBar.VISIBLE else ProgressBar.GONE
        messageView.text = message
    }

    private data class PreparedDocument(val uri: Uri, val note: String?)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
