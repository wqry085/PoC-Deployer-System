package com.wqry085.deployesystem.next;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wqry085.deployesystem.ZygoteActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class BootLoader extends AppCompatActivity {
    private static final int SHIZUKU_REQUEST_CODE = 0;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1;
    private static final String PREFS_NAME = "boot_pref"; // 统一使用一个名称
    private static final String KEY_SKIP_BOOTLOADER = "skipBootLoader";

    private TextView loadingText;
    private LinearProgressIndicator progressIndicator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 保存检测结果，传递给 ZygoteActivity
    private boolean lastCheckResult = false;
    private boolean isProcessing = false; // 防止重复处理
    private boolean storagePermissionGranted = false; // 存储权限状态

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 先检查用户是否已经做过选择
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(KEY_SKIP_BOOTLOADER)) {
            boolean skip = prefs.getBoolean(KEY_SKIP_BOOTLOADER, false);
            if (skip) {
                // 用户之前选择过"跳过 BootLoader"，直接进入主界面
                navigateToZygoteActivity(false); // 这里需要实际检测结果，暂时传false
                finish();
                return;
            }
            // 用户选择过"保留 BootLoader"，就继续运行本页
        }

        // 沉浸式状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(
                    ViewGroup.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | ViewGroup.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        // 初始化布局
        setupUI();

        // 开始流程
        startBootProcess();
    }

    private void setupUI() {
        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 标题栏
        TextView titleBar = new TextView(this);
        titleBar.setText("引导加载");
        titleBar.setTextSize(20f);
        titleBar.setPadding(dp(16), dp(48), dp(16), dp(12));
        root.addView(titleBar);

        // 中心内容
        LinearLayout center = new LinearLayout(this);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        centerLp.weight = 1f;
        center.setLayoutParams(centerLp);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);

        progressIndicator = new LinearProgressIndicator(this);
        progressIndicator.setIndeterminate(true);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progLp.setMargins(dp(24), 0, dp(24), 0);
        progressIndicator.setLayoutParams(progLp);
        center.addView(progressIndicator);

        loadingText = new TextView(this);
        loadingText.setText("初始化中...");
        loadingText.setTextSize(16f);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, dp(20), 0, 0);
        center.addView(loadingText);

        root.addView(center);

        // 底部小提示
        TextView bottom = new TextView(this);
        bottom.setText("PoC Deployer System");
        bottom.setTextSize(12f);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(16));
        root.addView(bottom);

        container.addView(root);
        setContentView(container);
    }

    private void startBootProcess() {
        if (isProcessing) return;
        isProcessing = true;
        
        // 首先静默申请存储权限
        requestStoragePermissionSilently();
    }

    /**
     * 静默申请存储权限，不在界面上显示
     */
    private void requestStoragePermissionSilently() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查是否已经有权限
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED) {
                
                storagePermissionGranted = true;
                updateLoadingText("存储权限已获取");
                proceedWithShizukuCheck();
                return;
            }
            
            // 静默申请权限，不显示对话框
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST_CODE);
            
            // 继续流程，不等待权限结果
            updateLoadingText("正在处理 Shizuku 权限...");
            proceedWithShizukuCheck();
            
        } else {
            // Android 6.0 以下自动拥有权限
            storagePermissionGranted = true;
            updateLoadingText("存储权限已获取");
            proceedWithShizukuCheck();
        }
    }

    /**
     * 继续执行 Shizuku 权限检查和漏洞检测
     */
    private void proceedWithShizukuCheck() {
        executor.execute(() -> {
            boolean shizukuOk = requestShizukuPermissionSafe(BootLoader.this);
            if (!shizukuOk) {
                mainHandler.post(() -> {
                    updateLoadingText("\n请在 Shizuku Manager 中授权后重启此界面。");
                    isProcessing = false;
                });
                return;
            }

            mainHandler.post(() -> updateLoadingText("\nShizuku 权限检查通过，开始检测设备可用性..."));

            boolean vulnerable = cve_2024_31317(BootLoader.this);
            lastCheckResult = vulnerable;

            mainHandler.post(() -> {
                if (vulnerable) {
                    updateLoadingText("\n检测结果: 设备可能存在漏洞（可利用）");
                } else {
                    updateLoadingText("\n检测结果: 漏洞已修复");
                }

                // 提示用户选择下次启动方式（只出现一次）
                mainHandler.postDelayed(this::askUserForNextTimeChoice, 800);
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // 静默处理权限结果，不在界面上显示
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            storagePermissionGranted = allGranted;
            
            // 不在界面上显示权限结果，继续静默执行
            if (allGranted) {
                // 权限已授予，静默继续
            } else {
                // 权限被拒绝，也继续执行，只是某些功能可能受限
            }
        }
    }

    private void askUserForNextTimeChoice() {
        // 检查是否已经询问过
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains("hasAsked")) {
            // 已经询问过，直接跳转
            navigateToZygoteActivity(lastCheckResult);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择启动方式")
                .setMessage("您希望下次启动时直接进入 荷载控制台 吗？")
                .setCancelable(false) // 用户必须选择
                .setPositiveButton("是下次直接进入", (dialog, which) -> {
                    // 保存用户选择
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_SKIP_BOOTLOADER, true)
                            .putBoolean("hasAsked", true)
                            .apply();

                    updateLoadingText("\n已保存选择，即将跳转...");
                    mainHandler.postDelayed(() -> navigateToZygoteActivity(lastCheckResult), 2000);
                })
                .setNegativeButton("否下次保留引导加载", (dialog, which) -> {
                    // 只标记已询问，不跳过
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean("hasAsked", true)
                            .apply();

                    updateLoadingText("\n即将跳转...");
                    mainHandler.postDelayed(() -> navigateToZygoteActivity(lastCheckResult), 2000);
                })
                .setOnDismissListener(dialog -> {
                    // 防止对话框意外关闭
                    if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).contains("hasAsked")) {
                        // 用户没有做出选择，重新显示对话框
                        mainHandler.postDelayed(this::askUserForNextTimeChoice, 500);
                    }
                })
                .show();
    }

    private boolean requestShizukuPermissionSafe(Activity activity) {
        boolean shizukuRunning = true;
        boolean shizukuGranted = false;

        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                // 在子线程中请求权限可能有问题，移到主线程
                mainHandler.post(() -> {
                    try {
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
                    } catch (Throwable ignored) {}
                });
                
                // 等待一段时间检查权限状态
                Thread.sleep(1000);
            }
            
            // 检查最终权限状态
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuGranted = true;
            }
        } catch (Throwable e) {
            shizukuRunning = false;
        }

        return shizukuRunning && shizukuGranted;
    }

    public boolean cve_2024_31317(Context context) {
        BufferedReader reader = null;
        try {
            Process process = new ProcessBuilder()
                    .command("sh", "-c", "strings /system/framework/framework.jar | grep -m1 'Embedded nulls not allowed'")
                    .redirectErrorStream(true)
                    .start();
            
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            boolean found = reader.readLine() != null;
            
            // 等待进程结束
            process.waitFor();
            return !found;
        } catch (IOException | InterruptedException e) {
            mainHandler.post(() -> updateLoadingText("\n检查失败: " + e.getMessage()));
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void navigateToZygoteActivity(boolean vulnerable) {
        Intent intent = new Intent(this, ZygoteActivity.class);
        intent.putExtra("isVulnerable", vulnerable);
        intent.putExtra("storagePermissionGranted", storagePermissionGranted);
        startActivity(intent);
        finish();
    }

    private void updateLoadingText(String msg) {
        if (loadingText != null) {
            mainHandler.post(() -> loadingText.append("\n" + msg));
        }
    }

    private int dp(int dp) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            // 处理 Shizuku 权限请求结果
            if (resultCode == RESULT_OK) {
                updateLoadingText("Shizuku 权限已授予");
            } else {
                updateLoadingText("Shizuku 权限被拒绝");
            }
        }
    }
}