package com.cnrtflm.pixelpicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class CropImageView extends View {

    private static final float CORNER_TOUCH_RADIUS_DP = 28f;
    private static final float MIN_CROP_SIZE_DP = 80f;

    private float cornerTouchRadius;
    private float minCropSize;

    private Bitmap sourceBitmap;

    private final Matrix imageMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();

    private final RectF cropRect = new RectF();
    private final RectF imageRect = new RectF();

    private final float[] matrixValues = new float[9];

    private Paint bitmapPaint;
    private Paint dimPaint;
    private Paint borderPaint;
    private Paint cornerPaint;

    private int viewWidth;
    private int viewHeight;

    private boolean initialized;

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE_CROP = 1;
    private static final int MODE_SCALE_CROP = 2;
    private static final int MODE_MOVE_IMAGE = 3;
    private static final int MODE_SCALE_IMAGE = 4;

    private int currentMode = MODE_NONE;
    private int activeCorner = -1;

    private float anchorX;
    private float anchorY;

    private float lastX;
    private float lastY;

    private float lastXImg;
    private float lastYImg;

    private float startDist;
    // 缩放焦点
    private final PointF scaleFocus = new PointF();

    private int activePointerId = -1;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cornerTouchRadius = CORNER_TOUCH_RADIUS_DP * density;
        minCropSize = MIN_CROP_SIZE_DP * density;

        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimPaint.setColor(0xBB000000);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(5f * density);
    }

    public void setImageBitmap(Bitmap bitmap) {
        sourceBitmap = bitmap;
        initialized = false;
        if (viewWidth > 0 && viewHeight > 0 && bitmap != null) {
            setupInitial();
            initialized = true;
        }
        invalidate();
    }

    public Bitmap getCropBitmap() {
        if (sourceBitmap == null || cropRect.isEmpty()) return null;

        imageMatrix.invert(inverseMatrix);
        RectF srcRect = new RectF(cropRect);
        inverseMatrix.mapRect(srcRect);

        int left = Math.max(0, (int) srcRect.left);
        int top = Math.max(0, (int) srcRect.top);
        int right = Math.min(sourceBitmap.getWidth(), (int) srcRect.right);
        int bottom = Math.min(sourceBitmap.getHeight(), (int) srcRect.bottom);

        int width = right - left;
        int height = bottom - top;
        int size = Math.min(width, height);
        if (size <= 0) return null;

        if (left + size > sourceBitmap.getWidth())
            left = sourceBitmap.getWidth() - size;
        if (top + size > sourceBitmap.getHeight())
            top = sourceBitmap.getHeight() - size;

        return Bitmap.createBitmap(sourceBitmap, left, top, size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (sourceBitmap != null && !initialized) {
            setupInitial();
            initialized = true;
        }
    }

    private void setupInitial() {
        int bmW = sourceBitmap.getWidth();
        int bmH = sourceBitmap.getHeight();

        float scale = (float) viewWidth / bmW;
        float scaledH = bmH * scale;

        float cropSize = Math.min(viewWidth, Math.min(viewHeight, scaledH));
        if (cropSize < minCropSize) cropSize = minCropSize;
        if (scaledH < cropSize) scale = cropSize / bmH;

        imageMatrix.reset();
        imageMatrix.setScale(scale, scale);

        float imgW = bmW * scale;
        float imgH = bmH * scale;

        float cropLeft = (viewWidth - cropSize) / 2f;
        float cropTop = (viewHeight - cropSize) / 2f;

        cropRect.set(cropLeft, cropTop, cropLeft + cropSize, cropTop + cropSize);

        float dx = cropRect.centerX() - imgW / 2f;
        float dy = cropRect.centerY() - imgH / 2f;
        imageMatrix.postTranslate(dx, dy);

        updateImageRect();
    }

    private void updateImageRect() {
        if (sourceBitmap == null) return;
        imageRect.set(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight());
        imageMatrix.mapRect(imageRect);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sourceBitmap == null) return;

        canvas.drawBitmap(sourceBitmap, imageMatrix, bitmapPaint);

        canvas.save();
        canvas.clipOutRect(cropRect);
        canvas.drawRect(0, 0, viewWidth, viewHeight, dimPaint);
        canvas.restore();

        canvas.drawRect(cropRect, borderPaint);

        float l = cropRect.left;
        float t = cropRect.top;
        float r = cropRect.right;
        float b = cropRect.bottom;
        float len = cornerTouchRadius;

        canvas.drawLine(l, t, l + len, t, cornerPaint);
        canvas.drawLine(l, t, l, t + len, cornerPaint);
        canvas.drawLine(r, t, r - len, t, cornerPaint);
        canvas.drawLine(r, t, r, t + len, cornerPaint);
        canvas.drawLine(l, b, l + len, b, cornerPaint);
        canvas.drawLine(l, b, l, b - len, cornerPaint);
        canvas.drawLine(r, b, r - len, b, cornerPaint);
        canvas.drawLine(r, b, r, b - len, cornerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                float x = event.getX();
                float y = event.getY();
                int corner = getTouchedCorner(x, y);
                if (corner != -1) {
                    currentMode = MODE_SCALE_CROP;
                    activeCorner = corner;
                    setAnchorByCorner(corner, cropRect);
                } else if (cropRect.contains(x, y)) {
                    currentMode = MODE_MOVE_CROP;
                    lastX = x;
                    lastY = y;
                } else {
                    currentMode = MODE_MOVE_IMAGE;
                    lastXImg = x;
                    lastYImg = y;
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    currentMode = MODE_SCALE_IMAGE;
                    startDist = spacing(event);
                    // 记录初始双指中点
                    scaleFocus.set(
                            (event.getX(0) + event.getX(1)) / 2f,
                            (event.getY(0) + event.getY(1)) / 2f
                    );
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentMode == MODE_MOVE_CROP) {
                    int index = event.findPointerIndex(activePointerId);
                    if (index >= 0) {
                        float dx = event.getX(index) - lastX;
                        float dy = event.getY(index) - lastY;
                        moveCrop(dx, dy);
                        lastX = event.getX(index);
                        lastY = event.getY(index);
                    }
                } else if (currentMode == MODE_SCALE_CROP) {
                    int index = event.findPointerIndex(activePointerId);
                    if (index >= 0) {
                        dragCorner(event.getX(index), event.getY(index));
                    }
                } else if (currentMode == MODE_MOVE_IMAGE) {
                    int index = event.findPointerIndex(activePointerId);
                    if (index >= 0) {
                        float dx = event.getX(index) - lastXImg;
                        float dy = event.getY(index) - lastYImg;
                        moveImage(dx, dy);
                        lastXImg = event.getX(index);
                        lastYImg = event.getY(index);
                    }
                } else if (currentMode == MODE_SCALE_IMAGE) {
                    if (event.getPointerCount() >= 2) {
                        float newDist = spacing(event);
                        if (startDist > 5f) {
                            float factor = newDist / startDist;
                            // 更新缩放焦点为当前双指中点
                            scaleFocus.set(
                                    (event.getX(0) + event.getX(1)) / 2f,
                                    (event.getY(0) + event.getY(1)) / 2f
                            );
                            scaleImage(factor, scaleFocus.x, scaleFocus.y);
                            startDist = newDist;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentMode = MODE_NONE;
                activePointerId = -1;
                break;
        }
        return true;
    }

    private int getTouchedCorner(float x, float y) {
        float r = cornerTouchRadius * 1.5f;
        if (Math.hypot(x - cropRect.left, y - cropRect.top) < r) return 0;
        if (Math.hypot(x - cropRect.right, y - cropRect.top) < r) return 1;
        if (Math.hypot(x - cropRect.left, y - cropRect.bottom) < r) return 2;
        if (Math.hypot(x - cropRect.right, y - cropRect.bottom) < r) return 3;
        return -1;
    }

    private void setAnchorByCorner(int corner, RectF rect) {
        switch (corner) {
            case 0: anchorX = rect.right; anchorY = rect.bottom; break;
            case 1: anchorX = rect.left;  anchorY = rect.bottom; break;
            case 2: anchorX = rect.right; anchorY = rect.top;    break;
            case 3: anchorX = rect.left;  anchorY = rect.top;    break;
        }
    }

    private float spacing(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0;
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.hypot(x, y);
    }

    private void moveCrop(float dx, float dy) {
        RectF r = new RectF(cropRect);
        r.offset(dx, dy);
        clampCropInView(r);
        clampCropInsideImage(r);
        cropRect.set(r);
        invalidate();
    }

    private void dragCorner(float x, float y) {
        float side = Math.max(Math.abs(x - anchorX), Math.abs(y - anchorY));
        if (side < minCropSize) side = minCropSize;
        float max = getMaxSquareSide();
        if (side > max) side = max;
        RectF r = buildSquareFromAnchor(anchorX, anchorY, side);
        clampCropInView(r);
        cropRect.set(r);
        invalidate();
    }

    private float getMaxSquareSide() {
        float max = Float.MAX_VALUE;
        switch (activeCorner) {
            case 0: max = Math.min(anchorX - imageRect.left, anchorY - imageRect.top); break;
            case 1: max = Math.min(imageRect.right - anchorX, anchorY - imageRect.top); break;
            case 2: max = Math.min(anchorX - imageRect.left, imageRect.bottom - anchorY); break;
            case 3: max = Math.min(imageRect.right - anchorX, imageRect.bottom - anchorY); break;
        }
        return max;
    }

    private RectF buildSquareFromAnchor(float x, float y, float size) {
        RectF r = new RectF();
        switch (activeCorner) {
            case 0: r.set(x - size, y - size, x, y); break;
            case 1: r.set(x, y - size, x + size, y); break;
            case 2: r.set(x - size, y, x, y + size); break;
            case 3: r.set(x, y, x + size, y + size); break;
        }
        return r;
    }

    private void moveImage(float dx, float dy) {
        imageMatrix.postTranslate(dx, dy);
        updateImageRect();
        clampImageToCoverCrop();
        invalidate();
    }

    private float getCurrentScale() {
        imageMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    // 修改：接受缩放焦点坐标
    private void scaleImage(float factor, float focusX, float focusY) {
        float current = getCurrentScale();
        float min = getMinScale();
        if (current * factor < min) factor = min / current;
        // 以手指中心为焦点缩放
        imageMatrix.postScale(factor, factor, focusX, focusY);
        updateImageRect();
        clampImageToCoverCrop();
        invalidate();
    }

    private float getMinScale() {
        return Math.max(
                cropRect.width() / sourceBitmap.getWidth(),
                cropRect.height() / sourceBitmap.getHeight()
        );
    }

    private void clampCropInView(RectF r) {
        if (r.left < 0) r.offset(-r.left, 0);
        if (r.top < 0) r.offset(0, -r.top);
        if (r.right > viewWidth) r.offset(viewWidth - r.right, 0);
        if (r.bottom > viewHeight) r.offset(0, viewHeight - r.bottom);
    }

    private void clampCropInsideImage(RectF r) {
        if (r.left < imageRect.left) r.offset(imageRect.left - r.left, 0);
        if (r.right > imageRect.right) r.offset(imageRect.right - r.right, 0);
        if (r.top < imageRect.top) r.offset(0, imageRect.top - r.top);
        if (r.bottom > imageRect.bottom) r.offset(0, imageRect.bottom - r.bottom);
    }

    private void clampImageToCoverCrop() {
        float dx = 0, dy = 0;
        if (imageRect.left > cropRect.left) dx = cropRect.left - imageRect.left;
        else if (imageRect.right < cropRect.right) dx = cropRect.right - imageRect.right;
        if (imageRect.top > cropRect.top) dy = cropRect.top - imageRect.top;
        else if (imageRect.bottom < cropRect.bottom) dy = cropRect.bottom - imageRect.bottom;
        if (dx != 0 || dy != 0) {
            imageMatrix.postTranslate(dx, dy);
            updateImageRect();
        }
    }
}