package kr.co.modubogi

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {
    private val imageMatrixInternal = Matrix()
    private val last = PointF()
    private var scale = 1f
    private val minScale = 1f
    private val maxScale = 6f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scale
                scale = min(maxScale, max(minScale, scale * detector.scaleFactor))
                val factor = scale / oldScale
                imageMatrixInternal.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = imageMatrixInternal
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { fitCenter() }
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { fitCenter() }
    }

    private fun fitCenter() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        imageMatrixInternal.reset()
        val fit = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        val dx = (width - d.intrinsicWidth * fit) / 2f
        val dy = (height - d.intrinsicHeight * fit) / 2f
        imageMatrixInternal.postScale(fit, fit)
        imageMatrixInternal.postTranslate(dx, dy)
        imageMatrix = imageMatrixInternal
        scale = 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> last.set(event.x, event.y)
            MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress && scale > 1f) {
                val dx = event.x - last.x
                val dy = event.y - last.y
                imageMatrixInternal.postTranslate(dx, dy)
                imageMatrix = imageMatrixInternal
                last.set(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
