package com.cnrtflm.pixelpicker;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView supportToggle = findViewById(R.id.supportToggle);
        LinearLayout rewardLayout = findViewById(R.id.rewardLayout);

        supportToggle.setOnClickListener(v -> {
            if (rewardLayout.getVisibility() == View.GONE) {
                rewardLayout.setVisibility(View.VISIBLE);
            } else {
                rewardLayout.setVisibility(View.GONE);
            }
        });
    }
}