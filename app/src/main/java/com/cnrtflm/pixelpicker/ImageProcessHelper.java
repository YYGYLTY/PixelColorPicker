package com.cnrtflm.pixelpicker;

import android.graphics.Bitmap;
import android.graphics.Matrix;


public class ImageProcessHelper {


    /**
     * 中心裁剪成正方形
     * 防止图片缩小导致主体变小
     */
    public static Bitmap centerCropSquare(Bitmap source){


        int width =
                source.getWidth();


        int height =
                source.getHeight();



        int size =
                Math.min(
                        width,
                        height
                );



        int x =
                (width - size) / 2;


        int y =
                (height - size) / 2;



        return Bitmap.createBitmap(
                source,
                x,
                y,
                size,
                size
        );


    }





    /**
     * 生成16×16数据
     */
    public static Bitmap resizeTo16(Bitmap bitmap){


        return Bitmap.createScaledBitmap(
                bitmap,
                16,
                16,
                false
        );


    }



}