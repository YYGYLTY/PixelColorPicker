package com.cnrtflm.pixelpicker;

import android.graphics.Bitmap;


public class PixelCropper {


    /**
     * 将图片中心裁剪为正方形
     */
    public static Bitmap cropSquare(Bitmap source) {


        int width = source.getWidth();

        int height = source.getHeight();


        int size = Math.min(width, height);



        int left = (width - size) / 2;

        int top = (height - size) / 2;



        return Bitmap.createBitmap(
                source,
                left,
                top,
                size,
                size
        );

    }


}
