package com.cnrtflm.pixelpicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 正方形图片裁剪控件，仿微信/系统相册体验。
 * 用法：在布局中直接使用，调用 setImageBitmap 设置图片，getCropBitmap 获取裁剪结果。
 */
public class CropImageView extends View {

    private static final float CORNER_TOUCH_RADIUS_DP = 28f;
    private static final float MIN_CROP_SIZE_DP = 80f;
    private float cornerTouchRadius;
    private float minCropSize;

    private Bitmap sourceBitmap;
    private Matrix imageMatrix = new Matrix();
    private RectF cropRect = new RectF();
    private RectF imageRect = new RectF();

    private Paint bitmapPaint;
    private Paint dimPaint;
    private Paint borderPaint;
    private Paint cornerPaint;

    private int viewWidth, viewHeight;
    private boolean initialized;

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE_CROP = 1;
    private static final int MODE_SCALE_CROP = 2;
    private static final int MODE_MOVE_IMAGE = 3;
    private static final int MODE_SCALE_IMAGE = 4;

    private int currentMode = MODE_NONE;
    private int activeCorner = -1;
    private float anchorX, anchorY;

    private float lastX, lastY;
    private float lastXImg, lastYImg;
    private float startDist;
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
        this.sourceBitmap = bitmap;
        initialized = false;
        if (viewWidth > 0 && viewHeight > 0 && bitmap != null) {
            setupInitial();
            initialized = true;
        }
        invalidate();
    }

    public Bitmap getCropBitmap() {
        if (sourceBitmap == null || cropRect.isEmpty()) return null;
        Matrix inverse = new Matrix();
        imageMatrix.invert(inverse);
        RectF srcRect = new RectF(cropRect);
        inverse.mapRect(srcRect);
        int left = Math.max(0, Math.round(srcRect.left));
        int top = Math.max(0, Math.round(srcRect.top));
        int right = Math.min(sourceBitmap.getWidth(), Math.round(srcRect.right));
        int bottom = Math.min(sourceBitmap.getHeight(), Math.round(srcRect.bottom));
        int width = right - left;
        int height = bottom - top;
        int size = Math.min(width, height);
        if (size <= 0) return null;
        left = Math.min(left, sourceBitmap.getWidth() - size);
        top = Math.min(top, sourceBitmap.getHeight() - size);
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
        if (scaledH < cropSize) {
            scale = cropSize / bmH;
        }

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

        float l = cropRect.left, t = cropRect.top, r = cropRect.right, b = cropRect.bottom;
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
                // 第二根手指按下，切换到图片缩放模式（无论手指位置）
                currentMode = MODE_SCALE_IMAGE;
                startDist = spacing(event);
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
                    float newDist = spacing(event);
                    if (startDist > 5f) {
                        float scaleFactor = newDist / startDist;
                        scaleImage(scaleFactor);
                        startDist = newDist;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentMode = MODE_NONE;
                activePointerId = -1;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (currentMode == MODE_SCALE_IMAGE) {
                    currentMode = MODE_NONE;
                } else if (pointerId == activePointerId) {
                    int newIndex = pointerIndex == 0 ? 1 : 0;
                    if (event.getPointerCount() > 1) {
                        activePointerId = event.getPointerId(newIndex);
                        float nx = event.getX(newIndex);
                        float ny = event.getY(newIndex);
                        if (currentMode == MODE_MOVE_CROP) {
                            lastX = nx;
                            lastY = ny;
                        } else if (currentMode == MODE_MOVE_IMAGE) {
                            lastXImg = nx;
                            lastYImg = ny;
                        } else if (currentMode == MODE_SCALE_CROP) {
                            currentMode = MODE_NONE;
                        }
                    } else {
                        currentMode = MODE_NONE;
                    }
                }
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

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.hypot(x, y);
    }

    private float getCurrentScale() {
        float[] values = new float[9];
        imageMatrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void moveCrop(float dx, float dy) {
        RectF newCrop = new RectF(cropRect);
        newCrop.offset(dx, dy);
        clampCropInView(newCrop);
        clampCropInsideImage(newCrop);
        cropRect.set(newCrop);
        invalidate();
    }

    /**
     * 拖动角调整裁剪框（始终保持正方形，且不超出屏幕和图片）
     */
    private void dragCorner(float fingerX, float fingerY) {
        // 根据锚点和手指位置计算期望边长
        float side = Math.max(Math.abs(fingerX - anchorX), Math.abs(fingerY - anchorY));
        if (side < minCropSize) side = minCropSize;

        // 计算在屏幕和图片双重限制下允许的最大边长
        float maxSide = getMaxSquareSide(anchorX, anchorY, imageRect, viewWidth, viewHeight);
        if (side > maxSide) side = maxSide;
        if (side < minCropSize) side = minCropSize;

        // 构建正方形矩形
        RectF newCrop = buildSquareFromAnchor(anchorX, anchorY, side);

        // 强制限制在屏幕内（防御性，理论上已满足）
        clampCropInView(newCrop);

        cropRect.set(newCrop);
        invalidate();
    }

    /**
     * 计算以(anchorX, anchorY)为固定角时，正方形能取到的最大边长，
     * 该正方形必须完全位于图片矩形和View屏幕内。
     */
    private float getMaxSquareSide(float anchorX, float anchorY, RectF imgBounds, int vw, int vh) {
        float maxSide = Float.MAX_VALUE;

        switch (activeCorner) {
            case 0: // 拖左上，固定右下，正方形向左上扩展
                maxSide = Math.min(maxSide, anchorX - Math.max(imgBounds.left, 0));
                maxSide = Math.min(maxSide, anchorY - Math.max(imgBounds.top, 0));
                break;
            case 1: // 拖右上，固定左下，正方形向右上扩展
                maxSide = Math.min(maxSide, Math.min(imgBounds.right, vw) - anchorX);
                maxSide = Math.min(maxSide, anchorY - Math.max(imgBounds.top, 0));
                break;
            case 2: // 拖左下，固定右上，正方形向左下扩展
                maxSide = Math.min(maxSide, anchorX - Math.max(imgBounds.left, 0));
                maxSide = Math.min(maxSide, Math.min(imgBounds.bottom, vh) - anchorY);
                break;
            case 3: // 拖右下，固定左上，正方形向右下扩展
                maxSide = Math.min(maxSide, Math.min(imgBounds.right, vw) - anchorX);
                maxSide = Math.min(maxSide, Math.min(imgBounds.bottom, vh) - anchorY);
                break;
        }
        return maxSide;
    }

    private RectF buildSquareFromAnchor(float anchorX, float anchorY, float side) {
        RectF rect = new RectF();
        switch (activeCorner) {
            case 0: rect.set(anchorX - side, anchorY - side, anchorX, anchorY); break;
            case 1: rect.set(anchorX, anchorY - side, anchorX + side, anchorY); break;
            case 2: rect.set(anchorX - side, anchorY, anchorX, anchorY + side); break;
            case 3: rect.set(anchorX, anchorY, anchorX + side, anchorY + side); break;
        }
        return rect;
    }

    private void moveImage(float dx, float dy) {
        imageMatrix.postTranslate(dx, dy);
        updateImageRect();
        clampImageToCoverCrop();
        invalidate();
    }

    private void scaleImage(float scaleFactor) {
        float currentScale = getCurrentScale();
        float minScale = getMinScale();
        if (currentScale * scaleFactor < minScale) {
            scaleFactor = minScale / currentScale;
        }
        imageMatrix.postScale(scaleFactor, scaleFactor,
                cropRect.centerX(), cropRect.centerY());
        updateImageRect();
        clampImageToCoverCrop();
        invalidate();
    }

    // ---------- 边界限制 ----------
    private void clampCropInView(RectF rect) {
        if (rect.left < 0) {
            rect.right += -rect.left;
            rect.left = 0;
        }
        if (rect.top < 0) {
            rect.bottom += -rect.top;
            rect.top = 0;
        }
        if (rect.right > viewWidth) {
            rect.left -= (rect.right - viewWidth);
            rect.right = viewWidth;
        }
        if (rect.bottom > viewHeight) {
            rect.top -= (rect.bottom - viewHeight);
            rect.bottom = viewHeight;
        }
    }

    private void clampCropInsideImage(RectF crop) {
        if (crop.left < imageRect.left) crop.offset(imageRect.left - crop.left, 0);
        if (crop.right > imageRect.right) crop.offset(imageRect.right - crop.right, 0);
        if (crop.top < imageRect.top) crop.offset(0, imageRect.top - crop.top);
        if (crop.bottom > imageRect.bottom) crop.offset(0, imageRect.bottom - crop.bottom);
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

    private float getMinScale() {
        if (sourceBitmap == null) return 1f;
        return Math.max(cropRect.width() / sourceBitmap.getWidth(),
                cropRect.height() / sourceBitmap.getHeight());
    }
}