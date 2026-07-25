package kr.co.modubogi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.math.min

class MainActivity : Activity() {
    private val requestOpenFile = 1001
    private val blue = Color.rgb(21, 87, 176)
    private val paleBlue = Color.rgb(238, 245, 255)
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var content: FrameLayout
    private lateinit var recentStore: RecentStore

    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pdfPageIndex = 0
    private var pdfBitmap: Bitmap? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recentStore = RecentStore(this)
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            setBackgroundColor(blue)
        }
        titleView = TextView(this).apply {
            text = "모두보기"
            setTextColor(Color.WHITE)
            textSize = 20f
            maxLines = 1
        }
        toolbar.addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))
        toolbar.addView(toolbarButton("최근") { showRecent() })
        toolbar.addView(toolbarButton("열기") { launchFilePicker() })

        statusView = TextView(this).apply {
            text = "파일을 선택하면 앱 안에서 직접 표시합니다."
            setTextColor(Color.DKGRAY)
            textSize = 14f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(paleBlue)
        }
        content = FrameLayout(this).apply { setBackgroundColor(Color.rgb(245, 247, 250)) }
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showWelcome()
    }

    private fun toolbarButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(blue)
        setBackgroundColor(Color.WHITE)
        isAllCaps = false
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginStart = dp(8) }
    }

    private fun showWelcome() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        box.addView(TextView(this).apply {
            text = "모든 파일을 한곳에서"
            textSize = 25f
            setTextColor(blue)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = "PDF · 이미지 · 텍스트 · 영상 · 음원 · ZIP을 앱 내부에서 열 수 있습니다.\nHWP와 Office 원본 렌더링 엔진은 다음 버전에 추가됩니다."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(24))
        })
        box.addView(Button(this).apply {
            text = "파일 열기"
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { launchFilePicker() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        content.removeAllViews()
        content.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun launchFilePicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, requestOpenFile)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOpenFile && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            openUri(uri)
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::openUri)
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.clipData?.getItemAt(0)?.uri
                uri?.let(::openUri)
            }
        }
    }

    private fun openUri(uri: Uri) {
        closeCurrent()
        val info = queryFileInfo(uri)
        titleView.text = info.name
        statusView.text = "형식을 확인하고 있습니다…"
        showCenteredMessage("파일을 준비하고 있습니다.")
        thread(name = "file-open") {
            val detected = FileTypeDetector.detect(contentResolver, uri, info.name, info.mime)
            runOnUiThread {
                recentStore.add(uri, info.name, detected.description)
                statusView.text = "${detected.description} · ${formatSize(info.size)} · 읽기 전용"
                when (detected.viewerType) {
                    ViewerType.PDF -> showPdf(uri)
                    ViewerType.IMAGE -> showImage(uri)
                    ViewerType.TEXT -> showText(uri)
                    ViewerType.VIDEO -> showVideo(uri)
                    ViewerType.AUDIO -> showAudio(uri)
                    ViewerType.ZIP -> showZip(uri)
                    ViewerType.OFFICE -> showUnsupported("Office 문서는 2차 렌더러에서 직접 표시합니다.", info)
                    ViewerType.HWP -> showUnsupported("HWP/HWPX는 전용 조판 엔진 추가 후 직접 표시합니다.", info)
                    ViewerType.UNKNOWN -> showHexPreview(uri, info)
                }
            }
        }
    }

    private fun showPdf(uri: Uri) {
        runCatching {
            pdfDescriptor = contentResolver.openFileDescriptor(uri, "r")
            pdfRenderer = PdfRenderer(pdfDescriptor!!)
            pdfPageIndex = 0
            renderPdfPage()
        }.onFailure { showError("PDF를 열 수 없습니다.", it) }
    }

    private fun renderPdfPage() {
        val renderer = pdfRenderer ?: return
        if (renderer.pageCount == 0) {
            showCenteredMessage("내용이 없는 PDF입니다.")
            return
        }
        pdfPageIndex = pdfPageIndex.coerceIn(0, renderer.pageCount - 1)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(48, 48, 48))
        }
        val image = ZoomableImageView(this).apply { setBackgroundColor(Color.rgb(48, 48, 48)) }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        controls.addView(navButton("이전") { if (pdfPageIndex > 0) { pdfPageIndex--; renderPdfPage() } })
        controls.addView(TextView(this).apply {
            text = "${pdfPageIndex + 1} / ${renderer.pageCount}"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(navButton("다음") { if (pdfPageIndex < renderer.pageCount - 1) { pdfPageIndex++; renderPdfPage() } })
        container.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
        content.removeAllViews()
        content.addView(container)

        thread(name = "pdf-render") {
            runCatching {
                renderer.openPage(pdfPageIndex).use { page ->
                    val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1080)
                    val scale = min(2.5f, screenWidth.toFloat() / page.width.toFloat())
                    Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.onSuccess { bitmap -> runOnUiThread {
                pdfBitmap?.recycle()
                pdfBitmap = bitmap
                image.setImageBitmap(bitmap)
            }}.onFailure { runOnUiThread { showError("PDF 페이지를 표시할 수 없습니다.", it) } }
        }
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun showImage(uri: Uri) {
        val image = ZoomableImageView(this).apply { setBackgroundColor(Color.BLACK) }
        content.removeAllViews()
        content.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        thread(name = "image-decode") {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                    val maxSide = maxOf(info.size.width, info.size.height)
                    if (maxSide > 8192) {
                        val ratio = 8192f / maxSide
                        decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                    }
                }
            }.onSuccess { drawable -> runOnUiThread {
                image.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }}.onFailure { runOnUiThread { showError("이미지를 표시할 수 없습니다.", it) } }
        }
    }

    private fun showText(uri: Uri) {
        showCenteredMessage("텍스트를 읽고 있습니다…")
        thread(name = "text-read") {
            runCatching {
                val limit = 10 * 1024 * 1024
                val output = ByteArrayOutputStream()
                var truncated = false
                contentResolver.openInputStream(uri)!!.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > limit) {
                            output.write(buffer, 0, limit - output.size())
                            truncated = true
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
                decodeText(output.toByteArray()) + if (truncated) "\n\n[안내] 파일이 커서 앞부분 10MB만 표시했습니다." else ""
            }.onSuccess { runOnUiThread { displayText(it) } }
             .onFailure { runOnUiThread { showError("텍스트를 읽을 수 없습니다.", it) } }
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { bytes.toString(Charset.forName("MS949")) }
    }

    private fun displayText(value: String) {
        val text = TextView(this).apply {
            this.text = value
            setTextColor(Color.rgb(30, 30, 30))
            setBackgroundColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setTextIsSelectable(true)
        }
        val vertical = ScrollView(this).apply { addView(text) }
        val horizontal = HorizontalScrollView(this).apply { addView(vertical) }
        content.removeAllViews()
        content.addView(horizontal, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showVideo(uri: Uri) {
        val video = VideoView(this)
        val controller = MediaController(this)
        controller.setAnchorView(video)
        video.setMediaController(controller)
        video.setVideoURI(uri)
        video.setOnErrorListener { _, _, _ -> showCenteredMessage("이 기기에서 지원하지 않는 영상 코덱입니다."); true }
        content.removeAllViews()
        content.addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        video.start()
    }

    private fun showAudio(uri: Uri) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val play = Button(this).apply { text = "재생"; isAllCaps = false }
        val stop = Button(this).apply { text = "정지"; isAllCaps = false }
        box.addView(TextView(this).apply { text = "음원 파일"; textSize = 24f; setTextColor(blue); gravity = Gravity.CENTER })
        box.addView(play, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(24) })
        box.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(8) })
        content.removeAllViews()
        content.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mediaPlayer = MediaPlayer.create(this, uri)
        if (mediaPlayer == null) {
            showCenteredMessage("이 기기에서 지원하지 않는 음원 형식입니다.")
            return
        }
        play.setOnClickListener {
            val player = mediaPlayer ?: return@setOnClickListener
            if (player.isPlaying) { player.pause(); play.text = "재생" } else { player.start(); play.text = "일시정지" }
        }
        stop.setOnClickListener { mediaPlayer?.pause(); mediaPlayer?.seekTo(0); play.text = "재생" }
    }

    private fun showZip(uri: Uri) {
        showCenteredMessage("압축파일 목록을 읽고 있습니다…")
        thread(name = "zip-list") {
            runCatching {
                val names = ArrayList<String>()
                contentResolver.openInputStream(uri)!!.use { input ->
                    ZipInputStream(input).use { zip ->
                        while (names.size < 10_000) {
                            val entry = zip.nextEntry ?: break
                            val safe = entry.name.replace('\\', '/').removePrefix("/")
                            if (!safe.split('/').contains("..")) names.add((if (entry.isDirectory) "[폴더] " else "[파일] ") + safe)
                            zip.closeEntry()
                        }
                    }
                }
                if (names.isEmpty()) "압축파일에 표시할 항목이 없습니다." else names.joinToString("\n")
            }.onSuccess { runOnUiThread { displayText(it + "\n\n[읽기 전용] 현재 버전은 ZIP 내부 목록을 안전하게 표시합니다.") } }
             .onFailure { runOnUiThread { showError("ZIP 내부 목록을 읽을 수 없습니다.", it) } }
        }
    }

    private fun showHexPreview(uri: Uri, info: FileInfo) {
        showCenteredMessage("지원되지 않는 형식이라 원본 바이트 미리보기를 준비합니다…")
        thread(name = "hex-preview") {
            runCatching {
                val bytes = contentResolver.openInputStream(uri)!!.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() < 64 * 1024) {
                        val read = input.read(buffer, 0, min(buffer.size, 64 * 1024 - output.size()))
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                buildString {
                    append("파일 형식: 직접 렌더러 미등록\n파일명: ${info.name}\n크기: ${formatSize(info.size)}\n\n원본 바이트 미리보기(최대 64KB)\n\n")
                    bytes.asList().chunked(16).forEachIndexed { row, chunk ->
                        append(String.format(Locale.US, "%08X  ", row * 16))
                        chunk.forEach { append(String.format(Locale.US, "%02X ", it.toInt() and 0xFF)) }
                        repeat(16 - chunk.size) { append("   ") }
                        append(" ")
                        chunk.forEach { val c = it.toInt() and 0xFF; append(if (c in 32..126) c.toChar() else '.') }
                        append('\n')
                    }
                }
            }.onSuccess { runOnUiThread { displayText(it) } }
             .onFailure { runOnUiThread { showError("파일 미리보기를 만들 수 없습니다.", it) } }
        }
    }

    private fun showUnsupported(message: String, info: FileInfo) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        box.addView(TextView(this).apply { text = "직접 렌더러 준비 중"; textSize = 24f; setTextColor(blue); gravity = Gravity.CENTER })
        box.addView(TextView(this).apply {
            text = "$message\n\n파일명: ${info.name}\n크기: ${formatSize(info.size)}\n\n원본 파일은 변경되지 않았습니다."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        content.removeAllViews()
        content.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showRecent() {
        val items = recentStore.load()
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setTitle("최근 파일").setMessage("최근에 연 파일이 없습니다.").setPositiveButton("확인", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("최근 파일")
            .setItems(items.map { "${it.name}\n${it.type}" }.toTypedArray()) { _, which -> openUri(items[which].uri) }
            .setNeutralButton("기록 삭제") { _, _ -> recentStore.clear() }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showCenteredMessage(message: String) {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = message
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showError(message: String, error: Throwable) {
        statusView.text = "오류가 발생했습니다. 원본 파일은 변경되지 않았습니다."
        showCenteredMessage("$message\n\n${error.javaClass.simpleName}: ${error.message ?: "알 수 없는 오류"}")
    }

    private data class FileInfo(val name: String, val size: Long, val mime: String?)

    private fun queryFileInfo(uri: Uri): FileInfo {
        var name = uri.lastPathSegment ?: "파일"
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return FileInfo(name, size, contentResolver.getType(uri))
    }

    private fun formatSize(size: Long): String = when {
        size < 0 -> "크기 알 수 없음"
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(Locale.KOREA, "%.1f KB", size / 1024.0)
        size < 1024L * 1024L * 1024L -> String.format(Locale.KOREA, "%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format(Locale.KOREA, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }

    private fun closeCurrent() {
        mediaPlayer?.release(); mediaPlayer = null
        pdfBitmap?.recycle(); pdfBitmap = null
        pdfRenderer?.close(); pdfRenderer = null
        pdfDescriptor?.close(); pdfDescriptor = null
    }

    override fun onDestroy() {
        closeCurrent()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
