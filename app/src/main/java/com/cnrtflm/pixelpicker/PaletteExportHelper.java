package com.cnrtflm.pixelpicker;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;



public class PaletteExportHelper {


    public static void show(
            Context context,
            LinearLayout exportPanel,
            Bitmap pixelBitmap
    ){

        exportPanel.setVisibility(View.VISIBLE);

        exportPanel.removeAllViews();



        TextView title =
                new TextView(context);


        title.setText(
                "📦 分列压缩色板码\n\n" +
                "编码规则：列优先。\n" +
                "读取顺序：X1(Y1→Y16)…X16(Y1→Y16)"
        );


        title.setTextSize(15);

        title.setGravity(Gravity.CENTER);


        title.setPadding(
                0,
                20,
                0,
                25
        );


        exportPanel.addView(title);






        HorizontalScrollView scroll =
                new HorizontalScrollView(context);



        LinearLayout container =
                new LinearLayout(context);


        container.setOrientation(
                LinearLayout.HORIZONTAL
        );


        scroll.addView(container);


        exportPanel.addView(scroll);






        for(int x = 0; x < 16; x++){

            addColumn(
                    context,
                    container,
                    pixelBitmap,
                    x
            );

        }

    }








    private static void addColumn(
            Context context,
            LinearLayout parent,
            Bitmap bitmap,
            int x
    ){


        LinearLayout column =
                new LinearLayout(context);


        column.setOrientation(
                LinearLayout.VERTICAL
        );


        column.setGravity(
                Gravity.CENTER_HORIZONTAL
        );



        // 修改：缩小列之间空白

        LinearLayout.LayoutParams columnParams =
                new LinearLayout.LayoutParams(
                        165,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        columnParams.setMargins(
                2,
                0,
                2,
                20
        );


        column.setLayoutParams(columnParams);







        // 色块（保持155×155不变）

        for(int y = 0; y < 16; y++){


            View block =
                    new View(context);



            GradientDrawable bg =
                    new GradientDrawable();


            bg.setColor(
                    bitmap.getPixel(
                            x,
                            y
                    )
            );


            bg.setCornerRadius(12);


            block.setBackground(bg);



            LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(
                            155,
                            155
                    );


            bp.setMargins(
                    2,
                    2,
                    2,
                    2
            );


            block.setLayoutParams(bp);



            column.addView(block);

        }








        View space1 =
                new View(context);


        space1.setLayoutParams(
                new LinearLayout.LayoutParams(
                        1,
                        35
                )
        );


        column.addView(space1);








        TextView name =
                new TextView(context);



        name.setText(
                "第"
                +
                (x + 1)
                +
                "列"
        );


        name.setTextSize(14);


        name.setTextColor(
                Color.WHITE
        );


        name.setGravity(
                Gravity.CENTER
        );


        name.setIncludeFontPadding(true);


        name.setPadding(
                0,
                4,
                0,
                4
        );



        name.setLayoutParams(
                new LinearLayout.LayoutParams(
                        175,
                        65
                )
        );


        column.addView(name);









        View space2 =
                new View(context);



        space2.setLayoutParams(
                new LinearLayout.LayoutParams(
                        1,
                        55
                )
        );



        column.addView(space2);









        TextView copy =
                new TextView(context);



        copy.setText("复制");


        copy.setTextSize(14);


        copy.setTextColor(
                Color.WHITE
        );


        copy.setGravity(
                Gravity.CENTER
        );


        copy.setIncludeFontPadding(true);


        copy.setPadding(
                0,
                6,
                0,
                6
        );



        GradientDrawable buttonBg =
                new GradientDrawable();


        buttonBg.setColor(
                Color.rgb(
                        0,
                        150,
                        136
                )
        );


        buttonBg.setCornerRadius(18);


        copy.setBackground(buttonBg);



        copy.setLayoutParams(
                new LinearLayout.LayoutParams(
                        155,
                        100
                )
        );




        copy.setOnClickListener(v -> {



            String code =
                    PaletteExporter.exportColumn(
                            bitmap,
                            x
                    );



            ClipboardManager manager =
                    (ClipboardManager)
                            context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                            );



            manager.setPrimaryClip(
                    ClipData.newPlainText(
                            "palette",
                            code
                    )
            );



            Toast.makeText(
                    context,
                    "已复制第"
                    +
                    (x + 1)
                    +
                    "列色板码",
                    Toast.LENGTH_SHORT
            ).show();



        });



        column.addView(copy);







        View bottomSpace =
                new View(context);



        bottomSpace.setLayoutParams(
                new LinearLayout.LayoutParams(
                        1,
                        20
                )
        );


        column.addView(bottomSpace);




        parent.addView(column);

    }


}