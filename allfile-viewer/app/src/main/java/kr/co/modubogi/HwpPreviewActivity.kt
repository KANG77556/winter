package kr.co.modubogi

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/** HWP 첫 페이지 미리보기와 본문 전체를 표시하는 읽기 전용 뷰어. */
class HwpPreviewActivity : Activity() {
    private val blue = Color.rgb(21, 87, 176)
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private var previewBitmap: Bitmap? = null
    private var previewText: String? = null
    private var fullPages: List<String> = emptyList()
    private var fullPageIndex = 0
    private var sectionCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = blue
        window.navigationBarColor = Color.WHITE
        val uri = intent?.data
        buildUi(uri)
        if (uri == null) showMessage("HWP 파일 주소가 없습니다.") else loadDocument(uri)
    }

    private fun buildUi(uri: Uri?) {
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

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(blue)
        }
        toolbar.addView(Button(this).apply {
            text = "뒤로"
            isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        toolbar.addView(TextView(this).apply {
            text = uri?.let(::queryFileName) ?: "HWP 문서"
            textSize = 19f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))

        status = TextView(this).apply {
            text = "HWP 전체 내용을 분석하고 있습니다…"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setBackgroundColor(Color.rgb(238, 245, 255))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            maxLines = 2
        }
        content = FrameLayout(this).apply { setBackgroundColor(Color.rgb(231, 233, 237)) }

        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun loadDocument(uri: Uri) {
        showLoading()
        thread(name = "hwp-full-document") {
            val previewResult = runCatching { HwpPreviewExtractor.extract(contentResolver, uri) }.getOrNull()
            val fullResult = runCatching { HwpFullContentExtractor.extract(contentResolver, uri) }

            runOnUiThread {
                previewBitmap = previewResult?.image
                previewText = previewResult?.previewText
                fullResult.onSuccess { result ->
                    fullPages = result.pages
                    sectionCount = result.sectionCount
                    fullPageIndex = 0
                    showFullContentPage()
                }.onFailure { error ->
                    when {
                        previewBitmap != null -> {
                            status.text = "HWP 전체 내용 분석 실패 · 첫 페이지 미리보기 표시 · 읽기 전용"
                            showImageTab()
                        }
                        !previewText.isNullOrBlank() -> {
                            fullPages = listOf(previewText!!)
                            status.text = "HWP 미리보기 텍스트 표시 · 읽기 전용"
                            showFullContentPage()
                        }
                        else -> showMessage(
                            "HWP 문서 내용을 읽지 못했습니다.\n\n" +
                                "${error.javaClass.simpleName}: ${error.message ?: "문서 분석 실패"}\n\n" +
                                "암호 또는 손상된 문서일 수 있습니다. 원본 파일은 변경되지 않았습니다."
                        )
                    }
                }
            }
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(this@HwpPreviewActivity))
            addView(TextView(this@HwpPreviewActivity).apply {
                text = "HWP 본문·표 안의 텍스트를 모두 읽고 있습니다."
                textSize = 16f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(18), dp(24), 0)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun createTabs(showingFull: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.WHITE)
            addView(Button(this@HwpPreviewActivity).apply {
                text = "첫 페이지"
                isAllCaps = false
                isEnabled = previewBitmap != null && showingFull
                setOnClickListener { showImageTab() }
            }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(Button(this@HwpPreviewActivity).apply {
                text = "전체 내용"
                isAllCaps = false
                isEnabled = !showingFull && fullPages.isNotEmpty()
                setOnClickListener { showFullContentPage() }
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        }
    }

    private fun showImageTab() {
        status.text = "HWP 내장 첫 페이지 미리보기 · 읽기 전용"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(45, 45, 45))
        }
        val image = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(45, 45, 45))
            previewBitmap?.let(::setImageBitmap)
        }
        root.addView(createTabs(showingFull = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
        root.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.removeAllViews()
        content.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showFullContentPage() {
        if (fullPages.isEmpty()) {
            showMessage("표시할 전체 내용이 없습니다.")
            return
        }
        fullPageIndex = fullPageIndex.coerceIn(0, fullPages.lastIndex)
        status.text = "HWP 전체 내용 · ${fullPageIndex + 1}/${fullPages.size} · 구역 ${sectionCount.coerceAtLeast(1)}개 · 읽기 전용"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(231, 233, 237))
        }
        root.addView(createTabs(showingFull = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))

        val pageText = TextView(this).apply {
            text = fullPages[fullPageIndex]
            textSize = 16f
            setTextColor(Color.rgb(28, 28, 28))
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.25f)
            setPadding(dp(24), dp(26), dp(24), dp(34))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(205, 208, 214))
            }
        }
        val pageWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(pageText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(this).apply { addView(pageWrap) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        navigation.addView(Button(this).apply {
            text = "이전"
            isAllCaps = false
            isEnabled = fullPageIndex > 0
            setOnClickListener { fullPageIndex--; showFullContentPage() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        navigation.addView(TextView(this).apply {
            text = "${fullPageIndex + 1} / ${fullPages.size}"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        navigation.addView(Button(this).apply {
            text = "다음"
            isAllCaps = false
            isEnabled = fullPageIndex < fullPages.lastIndex
            setOnClickListener { fullPageIndex++; showFullContentPage() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(navigation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))

        root.addView(TextView(this).apply {
            text = "※ 전체 내용 페이지는 모바일 열람용으로 재구성됩니다. 원본의 정확한 페이지 경계·표·도형 위치는 한컴 조판과 다를 수 있습니다."
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.removeAllViews()
        content.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showMessage(message: String) {
        status.text = "HWP 문서를 표시할 수 없습니다. · 읽기 전용"
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = message
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun queryFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "HWP 문서"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    override fun onDestroy() {
        previewBitmap?.recycle()
        previewBitmap = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
