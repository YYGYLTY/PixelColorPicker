package com.cnrtflm.pixelpicker;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.cnrtflm.pixelpicker.databinding.ActivityMainBinding;



public class MainActivity extends AppCompatActivity {



    private ActivityMainBinding binding;


    private Bitmap originalBitmap;


    private Bitmap pixelBitmap;



    private ActivityResultLauncher<Intent> imagePickerLauncher;


    private ActivityResultLauncher<Intent> cropLauncher;







    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);



        binding =
                ActivityMainBinding.inflate(
                        getLayoutInflater()
                );



        setContentView(
                binding.getRoot()
        );



        binding.aboutButton.setOnClickListener(v -> {


            Intent intent =
                    new Intent(
                            MainActivity.this,
                            AboutActivity.class
                    );


            startActivity(
                    intent
            );


        });



        initCrop();


        initPicker();


        initButtons();



    }









    private void initCrop(){


        cropLauncher =
                registerForActivityResult(

                        new ActivityResultContracts.StartActivityForResult(),


                        result -> {



                            if(result.getResultCode() == RESULT_OK
                                    && result.getData()!=null){



                                String path =
                                        result.getData()
                                                .getStringExtra(
                                                        "cropPath"
                                                );



                                if(path != null){



                                    /*
                                     *
                                     * 这里恢复高清加载
                                     *
                                     * 删除 inSampleSize
                                     *
                                     */


                                    Bitmap tempBitmap =
                                            BitmapFactory.decodeFile(
                                                    path
                                            );



                                    originalBitmap =
                                            ImageProcessHelper.centerCropSquare(
                                                    tempBitmap
                                            );



                                    binding.imagePreview
                                            .setImageBitmap(
                                                    originalBitmap
                                            );



                                    binding.emptyHint
                                            .setVisibility(
                                                    View.GONE
                                            );



                                    pixelBitmap = null;



                                    binding.colorPanel
                                            .setVisibility(
                                                    View.GONE
                                            );



                                }



                            }


                        });



    }









    private void initPicker(){



        imagePickerLauncher =

                registerForActivityResult(

                        new ActivityResultContracts.StartActivityForResult(),


                        result -> {



                            if(result.getResultCode()==RESULT_OK

                                    && result.getData()!=null){



                                Uri uri =

                                        result.getData()
                                                .getData();



                                Intent intent =

                                        new Intent(
                                                this,
                                                CropActivity.class
                                        );



                                intent.putExtra(
                                        "image",
                                        uri.toString()
                                );



                                cropLauncher.launch(
                                        intent
                                );



                            }


                        });



    }
    private void initButtons(){


        binding.selectImageButton.setOnClickListener(v ->

                openImagePicker()

        );





        binding.exportPixelImageButton.setOnClickListener(v -> {



            if(pixelBitmap == null){



                Toast.makeText(
                        this,
                        "请先生成16×16像素画",
                        Toast.LENGTH_SHORT
                ).show();



                return;


            }





            PixelImageExporter.export(
                    this,
                    pixelBitmap
            );



        });






        binding.imagePreview.setOnClickListener(v ->

                openImagePicker()

        );



        binding.emptyHint.setOnClickListener(v ->

                openImagePicker()

        );








        binding.pixelButton.setOnClickListener(v -> {



            if(originalBitmap == null){



                Toast.makeText(
                        this,
                        "请先导入图片",
                        Toast.LENGTH_SHORT
                ).show();



                return;


            }





            pixelBitmap =

                    PixelConverter.convertToPixelArt(
                            originalBitmap
                    );





            binding.imagePreview

                    .setImageBitmap(

                            PixelConverter.scalePixelPreview(
                                    pixelBitmap
                            )

                    );



        });









        binding.colorButton.setOnClickListener(v -> {



            if(pixelBitmap == null){



                Toast.makeText(
                        this,
                        "请先生成像素画",
                        Toast.LENGTH_SHORT
                ).show();



                return;


            }






            ColorPanelHelper.show(

                    this,

                    binding.colorTable,

                    binding.colorPanel,

                    pixelBitmap

            );



        });









        binding.exportFullButton.setOnClickListener(v -> {



            if(pixelBitmap == null){



                Toast.makeText(
                        this,
                        "请先生成像素画",
                        Toast.LENGTH_SHORT
                ).show();



                return;


            }






            String code =

                    PaletteExporter.exportCompressed(
                            pixelBitmap
                    );







            ClipboardManager manager =


                    (ClipboardManager)

                            getSystemService(
                                    CLIPBOARD_SERVICE
                            );







            manager.setPrimaryClip(

                    ClipData.newPlainText(

                            "palette",

                            code

                    )

            );







            Toast.makeText(

                    this,

                    "已复制游戏色板码",

                    Toast.LENGTH_SHORT

            ).show();




        });









        binding.exportColumnButton.setOnClickListener(v -> {



            if(pixelBitmap == null){



                Toast.makeText(

                        this,

                        "请先生成像素画",

                        Toast.LENGTH_SHORT

                ).show();



                return;


            }






            PaletteExportHelper.show(

                    this,

                    binding.exportPanel,

                    pixelBitmap

            );




        });






    }









    private void openImagePicker(){



        Intent intent =

                new Intent(
                        Intent.ACTION_PICK
                );



        intent.setType(
                "image/*"
        );



        imagePickerLauncher.launch(
                intent
        );



    }









    @Override

    protected void onDestroy(){



        super.onDestroy();



        binding = null;



    }



}