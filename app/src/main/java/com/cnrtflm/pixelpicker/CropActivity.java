package com.cnrtflm.pixelpicker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;

public class CropActivity extends AppCompatActivity {
    private CropImageView cropImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropImageView = findViewById(R.id.cropImageView);

        String uriString = getIntent().getStringExtra("image");
        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);

                int width = options.outWidth;
                int height = options.outHeight;

                int sample = 1;
                while (width / sample > 2000 || height / sample > 2000) {
                    sample *= 2;
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;

                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
                cropImageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        findViewById(R.id.confirmCrop).setOnClickListener(v -> {
            Bitmap result = cropImageView.getCropBitmap();
            if (result == null) return;

            File file = new File(getExternalCacheDir(), "crop.webp");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                // 改用 WEBP 有损压缩，quality=95，速度比 LOSSLESS 快很多
                result.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.flush();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            Intent intent = new Intent();
            intent.putExtra("cropPath", file.getAbsolutePath());
            setResult(RESULT_OK, intent);
            finish();
        });
    }
}