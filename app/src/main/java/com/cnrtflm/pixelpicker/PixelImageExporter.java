package com.cnrtflm.pixelpicker;


import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.OutputStream;



public class PixelImageExporter {



    public static void export(
            Context context,
            Bitmap pixelBitmap
    ){



        if(pixelBitmap == null){


            Toast.makeText(
                    context,
                    "请先生成像素画",
                    Toast.LENGTH_SHORT
            ).show();


            return;


        }






        // =========================
        // 最近邻放大
        // =========================


        int scale = 16;



        Bitmap exportBitmap =

                Bitmap.createBitmap(

                        pixelBitmap.getWidth() * scale,

                        pixelBitmap.getHeight() * scale,

                        Bitmap.Config.ARGB_8888

                );





        Canvas canvas =

                new Canvas(
                        exportBitmap
                );





        Paint paint =

                new Paint();



        // 保留像素硬边

        paint.setAntiAlias(
                false
        );


        paint.setFilterBitmap(
                false
        );





        Rect src =

                new Rect(

                        0,

                        0,

                        pixelBitmap.getWidth(),

                        pixelBitmap.getHeight()

                );





        Rect dst =

                new Rect(

                        0,

                        0,

                        exportBitmap.getWidth(),

                        exportBitmap.getHeight()

                );





        canvas.drawBitmap(

                pixelBitmap,

                src,

                dst,

                paint

        );







        // =========================
        // 保存相册
        // =========================



        ContentValues values =

                new ContentValues();



        values.put(

                MediaStore.Images.Media.DISPLAY_NAME,

                "PixelArt_16x16.png"

        );



        values.put(

                MediaStore.Images.Media.MIME_TYPE,

                "image/png"

        );





        Uri uri =

                context.getContentResolver()
                        .insert(

                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,

                                values

                        );







        try{


            if(uri != null){



                OutputStream outputStream =

                        context.getContentResolver()
                                .openOutputStream(
                                        uri
                                );




                if(outputStream != null){



                    exportBitmap.compress(

                            Bitmap.CompressFormat.PNG,

                            100,

                            outputStream

                    );



                    outputStream.flush();


                    outputStream.close();



                }





                Toast.makeText(

                        context,

                        "像素图片已导出\n256×256 PNG",

                        Toast.LENGTH_SHORT

                ).show();



            }



        }catch(Exception e){



            e.printStackTrace();



            Toast.makeText(

                    context,

                    "导出失败",

                    Toast.LENGTH_SHORT

            ).show();



        }



    }




}