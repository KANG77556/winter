package kr.co.vintagecolor.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * 사진 전체 화면 보기에서 확대·축소·드래그·두 번 누르기를 처리합니다.
 * 외부 라이브러리 없이 Android 기본 Matrix를 사용합니다.
 */
public class ZoomImageView extends ImageView {
    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    private final Matrix workingMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnSwipeListener swipeListener;
    private float minScale = 1f;
    private float maxScale = 5f;
    private float currentScale = 1f;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public ZoomImageView(Context context) {
        super(context);
        initialize(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(Context context) {
        setScaleType(ScaleType.MATRIX);
        setBackgroundColor(0xFF000000);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (getDrawable() == null) return false;
                float factor = detector.getScaleFactor();
                float target = currentScale * factor;
                if (target < minScale) factor = minScale / currentScale;
                if (target > maxScale) factor = maxScale / currentScale;
                currentScale *= factor;
                workingMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                constrainMatrix();
                setImageMatrix(workingMatrix);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }

            @Override public boolean onDoubleTap(MotionEvent event) {
                if (getDrawable() == null) return false;
                float target = currentScale > minScale * 1.4f ? minScale : Math.min(maxScale, minScale * 2.5f);
                float factor = target / currentScale;
                currentScale = target;
                workingMatrix.postScale(factor, factor, event.getX(), event.getY());
                constrainMatrix();
                setImageMatrix(workingMatrix);
                return true;
            }

            @Override public boolean onFling(MotionEvent first, MotionEvent second,
                    float velocityX, float velocityY) {
                if (swipeListener == null || currentScale > minScale * 1.08f) return false;
                float dx = second.getX() - first.getX();
                float dy = second.getY() - first.getY();
                if (Math.abs(dx) < 120f || Math.abs(dx) < Math.abs(dy) * 1.3f) return false;
                if (dx < 0) swipeListener.onSwipeLeft();
                else swipeListener.onSwipeRight();
                return true;
            }
        });
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        swipeListener = listener;
    }

    @Override public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        post(new Runnable() {
            @Override public void run() { fitImageToView(); }
        });
    }

    public void resetZoom() {
        fitImageToView();
    }

    private void fitImageToView() {
        if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;
        float imageWidth = getDrawable().getIntrinsicWidth();
        float imageHeight = getDrawable().getIntrinsicHeight();
        if (imageWidth <= 0f || imageHeight <= 0f) return;
        float scale = Math.min(getWidth() / imageWidth, getHeight() / imageHeight);
        float dx = (getWidth() - imageWidth * scale) / 2f;
        float dy = (getHeight() - imageHeight * scale) / 2f;
        workingMatrix.reset();
        workingMatrix.postScale(scale, scale);
        workingMatrix.postTranslate(dx, dy);
        minScale = scale;
        maxScale = Math.max(scale * 5f, 5f);
        currentScale = scale;
        setImageMatrix(workingMatrix);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) post(new Runnable() {
            @Override public void run() { fitImageToView(); }
        });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && currentScale > minScale * 1.01f) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    if (Math.abs(dx) > 1f || Math.abs(dy) > 1f) dragging = true;
                    workingMatrix.postTranslate(dx, dy);
                    constrainMatrix();
                    setImageMatrix(workingMatrix);
                }
                lastX = event.getX();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) performClick();
                dragging = false;
                break;
            default:
                break;
        }
        return scaleHandled || gestureHandled || true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void constrainMatrix() {
        if (getDrawable() == null) return;
        RectF rect = new RectF(0, 0,
                getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        workingMatrix.mapRect(rect);
        float dx = 0f;
        float dy = 0f;
        if (rect.width() <= getWidth()) dx = getWidth() / 2f - rect.centerX();
        else if (rect.left > 0f) dx = -rect.left;
        else if (rect.right < getWidth()) dx = getWidth() - rect.right;
        if (rect.height() <= getHeight()) dy = getHeight() / 2f - rect.centerY();
        else if (rect.top > 0f) dy = -rect.top;
        else if (rect.bottom < getHeight()) dy = getHeight() - rect.bottom;
        workingMatrix.postTranslate(dx, dy);
        workingMatrix.getValues(matrixValues);
    }
}
