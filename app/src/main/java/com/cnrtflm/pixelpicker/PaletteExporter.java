package com.cnrtflm.pixelpicker;


import android.graphics.Bitmap;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;



public class PaletteExporter {



    /**
     * 导出单列18色板
     *
     * 16个像素颜色
     * + 2个白色补位
     */
    public static String exportColumn(
            Bitmap bitmap,
            int column
    ){


        int[] colors =
                new int[18];



        // 当前列16个颜色

        for(int y = 0; y < 16; y++){

            colors[y] =
                    bitmap.getPixel(
                            column,
                            y
                    );

        }



        // 两个白色

        colors[16] =
                0xFFFFFFFF;


        colors[17] =
                0xFFFFFFFF;



        return encodePalette(
                colors
        );

    }









    /**
     * 完整压缩色板
     *
     * 保留兼容 MainActivity
     */
    public static String exportCompressed(
            Bitmap bitmap
    ){


        int[] colors =
                new int[18];


        int index = 0;



        for(int x = 0; x < 16; x++){


            for(int y = 0; y < 16; y++){


                if(index < 16){

                    colors[index++] =
                            bitmap.getPixel(
                                    x,
                                    y
                            );

                }


            }


        }



        colors[16] =
                0xFFFFFFFF;


        colors[17] =
                0xFFFFFFFF;




        return encodePalette(
                colors
        );

    }









    /**
     * 构造 protobuf 色板
     */
    private static String encodePalette(
            int[] colors
    ){


        ByteArrayOutputStream palette =
                new ByteArrayOutputStream();



        // field1 = "18"

        palette.write(
                0x0A
        );


        palette.write(
                0x02
        );


        palette.write(
                '1'
        );


        palette.write(
                '8'
        );






        // 18个颜色

        for(int color : colors){



            byte[] data =
                    encodeColor(
                            color
                    );



            // field2 repeated Color

            palette.write(
                    0x12
            );



            writeVarint(
                    palette,
                    data.length
            );



            palette.write(
                    data,
                    0,
                    data.length
            );



        }




        return gzipBase64(
                palette.toByteArray()
        );


    }









    /**
     * 单个颜色 protobuf
     */
    private static byte[] encodeColor(
            int color
    ){



        ByteArrayOutputStream out =
                new ByteArrayOutputStream();




        int r =
                (color >> 16)
                        & 0xFF;



        int g =
                (color >> 8)
                        & 0xFF;



        int b =
                color
                        & 0xFF;




        // Alpha 固定255

        int a =
                255;






        // R

        if(r != 0){


            out.write(
                    0x08
            );


            writeVarint(
                    out,
                    r
            );


        }







        // G

        if(g != 0){


            out.write(
                    0x10
            );


            writeVarint(
                    out,
                    g
            );


        }







        // B

        if(b != 0){


            out.write(
                    0x18
            );


            writeVarint(
                    out,
                    b
            );


        }







        // Alpha

        out.write(
                0x20
        );


        writeVarint(
                out,
                a
        );






        return out.toByteArray();


    }









    /**
     * protobuf varint
     */
    private static void writeVarint(
            ByteArrayOutputStream out,
            int value
    ){


        while(true){


            if(
                    (value & ~0x7F)
                            == 0
            ){


                out.write(
                        value
                );


                return;


            }



            out.write(
                    (value & 0x7F)
                            |
                    0x80
            );



            value >>>= 7;


        }


    }









    /**
     * GZIP + Base64
     */
    private static String gzipBase64(
            byte[] data
    ){



        try{


            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();



            GZIPOutputStream gzip =
                    new GZIPOutputStream(
                            output
                    );



            gzip.write(
                    data
            );



            gzip.close();




            return Base64.encodeToString(
                    output.toByteArray(),
                    Base64.NO_WRAP
            );



        }catch(IOException e){


            e.printStackTrace();


        }




        return "";

    }



}