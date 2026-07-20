package com.cnrtflm.pixelpicker;

import android.graphics.Bitmap;


public class PixelConverter {


    /**
     * 生成真正16×16像素图
     * 用于色板读取
     */
    public static Bitmap convertToPixelArt(Bitmap source) {


        return Bitmap.createScaledBitmap(
                source,
                16,
                16,
                false
        );


    }



    /**
     * 放大像素画用于界面显示
     * 不改变原16×16数据
     */
    public static Bitmap scalePixelPreview(Bitmap pixelBitmap) {


        return Bitmap.createScaledBitmap(
                pixelBitmap,
                256,
                256,
                false
        );


    }


}