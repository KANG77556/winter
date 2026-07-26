package kr.co.modubogi

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.concurrent.thread

/** HWP는 전용 미리보기로, 나머지는 기존 일반·Drive 문서 처리 화면으로 전달한다. */
class MainActivityV4 : Activity() {
    private val requestOpenFile = 4001
    private val blue = Color.rgb(21, 87, 176)
    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = blue
        window.navigationBarColor = Color.WHITE
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::routeUri)
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.getItemAt(0)?.uri
                uri?.let(::routeUri)
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
        root.addView(TextView(this).apply {
            text = "모두보기"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            setBackgroundColor(blue)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(30), dp(28), dp(30))
        }
        body.addView(TextView(this).apply {
            text = "모든 파일을 한곳에서"
            textSize = 25f
            setTextColor(blue)
            gravity = Gravity.CENTER
        })
        body.addView(TextView(this).apply {
            text = "HWP는 내장 미리보기 이미지와 텍스트를 표시하고,\n일반 파일과 Drive 가상 문서는 기존 직접 렌더러로 연결합니다."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(24))
        })
        messageView = TextView(this).apply {
            text = "원본 파일은 읽기 전용으로 유지됩니다."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }
        body.addView(messageView)
        body.addView(Button(this).apply {
            text = "모든 파일 열기"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { launchFilePicker() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        body.addView(Button(this).apply {
            text = "Drive·일반 뷰어 열기"
            textSize = 16f
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivityV4, MainActivityV3::class.java)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(10) })

        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun launchFilePicker() {
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
            routeUri(uri)
        }
    }

    private fun routeUri(uri: Uri) {
        messageView.text = "파일 형식을 확인하고 있습니다…"
        thread(name = "file-route") {
            val name = queryDisplayName(uri)
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            runOnUiThread {
                messageView.text = "원본 파일은 읽기 전용으로 유지됩니다."
                val target = if (extension == "hwp") HwpPreviewActivity::class.java else MainActivityV3::class.java
                startActivity(Intent(this, target).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("선택한 파일", uri)
                })
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "파일"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
