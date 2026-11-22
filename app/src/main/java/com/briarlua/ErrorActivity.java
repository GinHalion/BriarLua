package com.briarlua;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.androlua.LuaApplication;
import com.androlua.util.FileUtil;
import com.briarlua.databinding.ActivityErrorBinding;
import com.briarlua.utils.UiUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ErrorActivity extends AppCompatActivity {

    private SpannableStringBuilder errorInformation;
    private ActivityErrorBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(com.briarlua.R.style.Theme_Material3_Blue_NoActionBar);
        super.onCreate(savedInstanceState);

        binding = ActivityErrorBinding.inflate(getLayoutInflater());

        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String crashDir = LuaApplication.getInstance().getLuaExtDir("crash");

        String fileName = "crash_" + dateFormat.format(currentDate)
                .replace(" ", "_")
                .replace(":", "_")
                .replace("-", "_") + ".txt";
        File file = new File(crashDir, fileName);

        FileUtil.createDirectory(crashDir);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setTitle("应用崩溃了");

        CrashManager.adoptNativeCrashLog(getIntent());

        binding.toolbar.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams lp = binding.toolbar.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).topMargin =
                            UiUtil.getStatusBarHeight(ErrorActivity.this);
                    binding.toolbar.setLayoutParams(lp);
                }
            }
        });

        SpannableStringBuilder crashDetails =
                CrashManager.getAllErrorDetailsFromIntent(this, getIntent());
        errorInformation = crashDetails;
        try {
            FileUtil.write(file.toString(), crashDetails.toString());
        } catch (java.io.IOException e) {
            Log.w("BriarLua", "crash report save failed", e);
        }

        binding.emessage.setText(crashDetails, TextView.BufferType.SPANNABLE);

        binding.errfab.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                copy(ErrorActivity.this, crashDetails.toString());
            }
        });
    }

    private void copy(Context context, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("crash_log", text);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}