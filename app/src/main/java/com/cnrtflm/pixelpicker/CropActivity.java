package com.cnrtflm.pixelpicker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;


public class CropActivity extends AppCompatActivity {


    private CropImageView cropImageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_crop);



        cropImageView =
                findViewById(R.id.cropImageView);



        String uriString =
                getIntent().getStringExtra("image");


        if(uriString != null){


            Uri uri =
                    Uri.parse(uriString);


            try {


                Bitmap bitmap =
                        BitmapFactory
                                .decodeStream(
                                        getContentResolver()
                                                .openInputStream(uri)
                                );


                cropImageView
                        .setImageBitmap(bitmap);


            }catch(Exception e){

                e.printStackTrace();

            }

        }



        findViewById(R.id.confirmCrop)
                .setOnClickListener(v -> {


                    Bitmap result =
                            cropImageView
                                    .getCropBitmap();



                    // 临时保存到缓存

                    String path =
                            getExternalCacheDir()
                                    .getAbsolutePath()
                            + "/crop.png";



                    try{


                        java.io.FileOutputStream fos =
                                new java.io.FileOutputStream(path);


                        result.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                fos
                        );


                        fos.close();



                        Intent intent =
                                new Intent();


                        intent.putExtra(
                                "cropPath",
                                path
                        );


                        setResult(
                                RESULT_OK,
                                intent
                        );


                        finish();


                    }catch(Exception e){

                        e.printStackTrace();

                    }


                });


    }


}