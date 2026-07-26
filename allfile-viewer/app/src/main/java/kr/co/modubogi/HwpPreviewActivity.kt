package kr.co.modubogi

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/** HWP 내장 미리보기를 표시하는 읽기 전용 1차 뷰어. */
class HwpPreviewActivity : Activity() {
    private val blue = Color.rgb(21, 87, 176)
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private var previewBitmap: Bitmap? = null
    private var previewText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = blue
        window.navigationBarColor = Color.WHITE
        val uri = intent?.data
        buildUi(uri)
        if (uri == null) {
            showMessage("HWP 파일 주소가 없습니다.")
        } else {
            loadPreview(uri)
        }
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
            text = "HWP 내장 미리보기를 준비하고 있습니다…"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setBackgroundColor(Color.rgb(238, 245, 255))
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        content = FrameLayout(this).apply { setBackgroundColor(Color.rgb(245, 247, 250)) }

        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun loadPreview(uri: Uri) {
        showLoading()
        thread(name = "hwp-preview") {
            runCatching { HwpPreviewExtractor.extract(contentResolver, uri) }
                .onSuccess { result ->
                    runOnUiThread {
                        previewBitmap = result.image
                        previewText = result.previewText
                        when {
                            result.image != null -> showImageTab()
                            !result.previewText.isNullOrBlank() -> showTextTab()
                            else -> showMessage(
                                "이 HWP 파일에는 앱에서 읽을 수 있는 내장 미리보기가 없습니다.\n\n" +
                                    "원본 파일은 변경되지 않았습니다. 전체 페이지 조판 엔진은 다음 단계에서 추가합니다."
                            )
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        status.text = "HWP 미리보기를 읽지 못했습니다. · 읽기 전용"
                        showMessage(
                            "이 파일은 구형 HWP이거나 암호·배포용 문서일 수 있습니다.\n\n" +
                                "${error.javaClass.simpleName}: ${error.message ?: "미리보기 정보 없음"}\n\n" +
                                "원본 파일은 변경되지 않았습니다."
                        )
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
                text = "HWP 내부의 미리보기 이미지와 텍스트를 읽고 있습니다."
                textSize = 16f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(18), dp(24), 0)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showImageTab() {
        status.text = "HWP 내장 미리보기 · 첫 페이지 중심 · 읽기 전용"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(45, 45, 45))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        controls.addView(Button(this).apply {
            text = "문서 미리보기"
            isAllCaps = false
            isEnabled = false
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        controls.addView(Button(this).apply {
            text = "미리보기 텍스트"
            isAllCaps = false
            isEnabled = !previewText.isNullOrBlank()
            setOnClickListener { showTextTab() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })

        val image = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(45, 45, 45))
            previewBitmap?.let(::setImageBitmap)
        }
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
        root.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.removeAllViews()
        content.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showTextTab() {
        status.text = "HWP 내장 미리보기 텍스트 · 읽기 전용"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        controls.addView(Button(this).apply {
            text = "문서 미리보기"
            isAllCaps = false
            isEnabled = previewBitmap != null
            setOnClickListener { showImageTab() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        controls.addView(Button(this).apply {
            text = "미리보기 텍스트"
            isAllCaps = false
            isEnabled = false
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })

        val text = TextView(this).apply {
            this.text = previewText ?: "미리보기 텍스트가 없습니다."
            textSize = 16f
            setTextColor(Color.rgb(30, 30, 30))
            setTextIsSelectable(true)
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        val scroll = ScrollView(this).apply { addView(text) }
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.removeAllViews()
        content.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showMessage(message: String) {
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
