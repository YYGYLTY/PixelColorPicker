package com.cnrtflm.pixelpicker;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;



public class ColorPanelHelper {


    public static void show(
            Context context,
            TableLayout colorTable,
            View colorPanel,
            Bitmap pixelBitmap
    ){


        colorPanel.setVisibility(
                View.VISIBLE
        );


        colorTable.removeAllViews();


        // 禁止自动拉伸

        colorTable.setShrinkAllColumns(
                false
        );

        colorTable.setStretchAllColumns(
                false
        );



        HashMap<Integer,Integer> repeat =
                new HashMap<>();



        for(int y = 0; y < 16; y++){

            for(int x = 0; x < 16; x++){


                int color =
                        pixelBitmap.getPixel(
                                x,
                                y
                        );


                repeat.put(
                        color,
                        repeat.getOrDefault(
                                color,
                                0
                        ) + 1
                );


            }

        }





        // 顶部列标题

        TableRow titleRow =
                new TableRow(context);


        titleRow.setGravity(
                Gravity.CENTER
        );


        addTitle(
                context,
                titleRow,
                ""
        );



        for(int x = 0; x < 16; x++){


            addTitle(
                    context,
                    titleRow,
                    "X"
                    +
                    (x + 1)
            );


        }



        colorTable.addView(
                titleRow
        );






        // 内容16行

        for(int y = 0; y < 16; y++){



            TableRow row =
                    new TableRow(context);



            row.setGravity(
                    Gravity.CENTER
            );



            addTitle(
                    context,
                    row,
                    "Y"
                    +
                    (y + 1)
            );



            for(int x = 0; x < 16; x++){


                int color =
                        pixelBitmap.getPixel(
                                x,
                                y
                        );



                addColorBox(
                        context,
                        row,
                        color,
                        x,
                        y,
                        repeat.get(color)
                );


            }



            colorTable.addView(
                    row
            );


        }


    }









    private static void addTitle(
            Context context,
            TableRow row,
            String text
    ){


        TextView title =
                new TextView(context);



        title.setText(
                text
        );


        title.setTextSize(
                9
        );


        title.setGravity(
                Gravity.CENTER
        );


        title.setIncludeFontPadding(
                false
        );



        TableRow.LayoutParams params =
                new TableRow.LayoutParams();


        params.width =
                65;


        params.height =
                45;



        params.setMargins(
                2,
                2,
                2,
                2
        );



        title.setLayoutParams(
                params
        );


        row.addView(
                title
        );


    }









    private static void addColorBox(
            Context context,
            TableRow row,
            int color,
            int x,
            int y,
            int count
    ){



        String hex =
                String.format(
                        "#%06X",
                        (0xFFFFFF & color)
                );



        TextView box =
                new TextView(context);



        String text =
                hex
                +
                "\n("
                +
                (x + 1)
                +
                ","
                +
                (y + 1)
                +
                ")";



        if(count > 1){


            text +=
                    "\n×"
                    +
                    count;


        }



        box.setText(
                text
        );



        box.setTextSize(
                7
        );



        box.setGravity(
                Gravity.CENTER
        );



        box.setIncludeFontPadding(
                false
        );


        box.setPadding(
                0,
                0,
                0,
                0
        );




        if(
                Color.red(color)
                +
                Color.green(color)
                +
                Color.blue(color)
                <
                380
        ){


            box.setTextColor(
                    Color.WHITE
            );


        }else{


            box.setTextColor(
                    Color.BLACK
            );


        }






        GradientDrawable drawable =
                new GradientDrawable();



        drawable.setColor(
                color
        );



        drawable.setCornerRadius(
                12
        );



        box.setBackground(
                drawable
        );







        TableRow.LayoutParams params =
                new TableRow.LayoutParams();



        params.width =
                155;


        params.height =
                155;



        params.setMargins(
                3,
                3,
                3,
                3
        );



        box.setLayoutParams(
                params
        );






        box.setOnClickListener(v -> {



            ClipboardManager manager =
                    (ClipboardManager)
                            context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                            );



            manager.setPrimaryClip(
                    ClipData.newPlainText(
                            "color",
                            hex
                    )
            );



            Toast.makeText(
                    context,
                    "复制 "
                    +
                    hex
                    +
                    "\n坐标("
                    +
                    (x + 1)
                    +
                    ","
                    +
                    (y + 1)
                    +
                    ")",
                    Toast.LENGTH_SHORT
            ).show();



        });





        row.addView(
                box
        );


    }


}