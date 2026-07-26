package com.cnrtflm.pixelpicker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Bitmap originalBitmap;
    private Bitmap pixelBitmap;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> cropLauncher;

    private static final String ORIGINAL_FILE = "original.png";
    private static final String PIXEL_FILE = "pixel.png";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_EXPORT_PANEL_VISIBLE = "export_panel_visible";
    private static final String KEY_COLOR_PANEL_VISIBLE = "color_panel_visible";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedLang = prefs.getString("language", "");
        if (!savedLang.isEmpty()) {
            applyLanguage(savedLang);
        }

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        restoreBitmaps();

        // 恢复 colorPanel 可见性，如果有数据则重新填充
        boolean colorPanelVisible = prefs.getBoolean(KEY_COLOR_PANEL_VISIBLE, false);
        if (colorPanelVisible && pixelBitmap != null) {
            ColorPanelHelper.show(this, binding.colorTable, binding.colorPanel, pixelBitmap);
        }

        // 恢复 exportPanel 可见性，如果有数据则重新填充
        boolean exportPanelVisible = prefs.getBoolean(KEY_EXPORT_PANEL_VISIBLE, false);
        if (exportPanelVisible && pixelBitmap != null) {
            PaletteExportHelper.show(this, binding.exportPanel, pixelBitmap);
        }

        binding.aboutButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        });

        binding.languageButton.setOnClickListener(v -> showLanguageDialog());

        initCrop();
        initPicker();
        initButtons();
    }

    private void initCrop() {
        cropLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String path = result.getData().getStringExtra("cropPath");
                        if (path != null) {
                            Bitmap tempBitmap = BitmapFactory.decodeFile(path);
                            originalBitmap = ImageProcessHelper.centerCropSquare(tempBitmap);
                            saveBitmapToFile(originalBitmap, ORIGINAL_FILE);
                            binding.imagePreview.setImageBitmap(originalBitmap);
                            binding.emptyHint.setVisibility(View.GONE);
                            pixelBitmap = null;
                            binding.colorPanel.setVisibility(View.GONE);
                            binding.exportPanel.setVisibility(View.GONE);
                        }
                    }
                }
        );
    }

    private void initPicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        Intent intent = new Intent(this, CropActivity.class);
                        intent.putExtra("image", uri.toString());
                        cropLauncher.launch(intent);
                    }
                }
        );
    }

    private void initButtons() {
        binding.selectImageButton.setOnClickListener(v -> openImagePicker());

        binding.exportPixelImageButton.setOnClickListener(v -> {
            if (pixelBitmap == null) {
                Toast.makeText(this, getString(R.string.toast_generate_16x16_first), Toast.LENGTH_SHORT).show();
                return;
            }
            PixelImageExporter.export(this, pixelBitmap);
        });

        binding.imagePreview.setOnClickListener(v -> openImagePicker());
        binding.emptyHint.setOnClickListener(v -> openImagePicker());

        binding.pixelButton.setOnClickListener(v -> {
            if (originalBitmap == null) {
                Toast.makeText(this, getString(R.string.toast_import_image_first), Toast.LENGTH_SHORT).show();
                return;
            }
            pixelBitmap = PixelConverter.convertToPixelArt(originalBitmap);
            saveBitmapToFile(pixelBitmap, PIXEL_FILE);
            binding.imagePreview.setImageBitmap(PixelConverter.scalePixelPreview(pixelBitmap));
        });

        binding.colorButton.setOnClickListener(v -> {
            if (pixelBitmap == null) {
                Toast.makeText(this, getString(R.string.toast_generate_pixel_art_first), Toast.LENGTH_SHORT).show();
                return;
            }
            ColorPanelHelper.show(this, binding.colorTable, binding.colorPanel, pixelBitmap);
        });

        binding.exportFullButton.setOnClickListener(v -> {
            if (pixelBitmap == null) {
                Toast.makeText(this, getString(R.string.toast_generate_pixel_art_first), Toast.LENGTH_SHORT).show();
                return;
            }
            String code = PaletteExporter.exportCompressed(pixelBitmap);
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("palette", code));
            Toast.makeText(this, getString(R.string.toast_palette_copied), Toast.LENGTH_SHORT).show();
        });

        binding.exportColumnButton.setOnClickListener(v -> {
            if (pixelBitmap == null) {
                Toast.makeText(this, getString(R.string.toast_generate_pixel_art_first), Toast.LENGTH_SHORT).show();
                return;
            }
            PaletteExportHelper.show(this, binding.exportPanel, pixelBitmap);
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showLanguageDialog() {
        String[] languages = { getString(R.string.lang_chinese), getString(R.string.lang_english), getString(R.string.lang_japanese) };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_language))
                .setItems(languages, (dialog, which) -> {
                    if (which == 0) {
                        changeLanguage("zh");
                    } else if (which == 1) {
                        changeLanguage("en");
                    } else if (which == 2) {
                        changeLanguage("ja");
                    }
                })
                .show();
    }

    private void changeLanguage(String lang) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString("language", lang).apply();

        // 保存 colorPanel 可见性
        boolean colorVisible = binding.colorPanel != null && binding.colorPanel.getVisibility() == View.VISIBLE;
        prefs.edit().putBoolean(KEY_COLOR_PANEL_VISIBLE, colorVisible).apply();

        // 保存 exportPanel 可见性
        boolean exportVisible = binding.exportPanel != null && binding.exportPanel.getVisibility() == View.VISIBLE;
        prefs.edit().putBoolean(KEY_EXPORT_PANEL_VISIBLE, exportVisible).apply();

        applyLanguage(lang);
        recreate();
    }

    private void applyLanguage(String lang) {
        java.util.Locale locale = new java.util.Locale(lang);
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void restoreBitmaps() {
        File origFile = new File(getFilesDir(), ORIGINAL_FILE);
        File pixelFile = new File(getFilesDir(), PIXEL_FILE);

        if (origFile.exists()) {
            originalBitmap = BitmapFactory.decodeFile(origFile.getAbsolutePath());
            if (originalBitmap != null) {
                binding.imagePreview.setImageBitmap(originalBitmap);
                binding.emptyHint.setVisibility(View.GONE);
            }
        }

        if (pixelFile.exists()) {
            pixelBitmap = BitmapFactory.decodeFile(pixelFile.getAbsolutePath());
            if (pixelBitmap != null) {
                binding.imagePreview.setImageBitmap(PixelConverter.scalePixelPreview(pixelBitmap));
            }
        }
    }

    private void saveBitmapToFile(Bitmap bitmap, String fileName) {
        File file = new File(getFilesDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            new File(getFilesDir(), ORIGINAL_FILE).delete();
            new File(getFilesDir(), PIXEL_FILE).delete();
        }
        binding = null;
    }
}