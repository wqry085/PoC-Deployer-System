package com.wqry085.deployesystem;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.Toolbar;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TerminalActivity extends AppCompatActivity {
    private static final String TAG = "TerminalActivity";
    private TerminalView terminalView;
    private TerminalSession terminalSession;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private boolean mIsShuttingDown = false;
    private static final String ENV_FLAG_FILE = "environment_deployed.flag";
    private static final long DEPLOYMENT_TIMEOUT_MS = 30000; // 30秒超时
    
    // 一次性命令执行模式相关
    private boolean mIsOneTimeCommandMode = false;
    private String mOneTimeCommand = null;
    private static final String EXTRA_ONE_TIME_COMMAND = "one_time_command";

    // TerminalSessionClient 和 TerminalViewClient 实现保持不变...
    private final TerminalSessionClient terminalSessionClient = new TerminalSessionClient() {
        @Override public void onTextChanged(@NonNull TerminalSession changedSession) { runOnUiThread(() -> { if (terminalView != null) terminalView.onScreenUpdated(); }); }
        @Override public void onTitleChanged(@NonNull TerminalSession changedSession) { }
        @Override public void onSessionFinished(@NonNull TerminalSession finishedSession) {
            Log.d(TAG, "Session finished. Exit status: " + finishedSession.getExitStatus());
            if (!mIsShuttingDown) {
                Log.d(TAG, "Session ended unexpectedly.");
            }
            // 如果是一次性命令模式，会话结束就关闭Activity
            if (mIsOneTimeCommandMode) {
               // finish();
            }
        }
        @Override public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("终端输出", text);
                clipboard.setPrimaryClip(clip);
            });
        }
        @Override public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip()) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence paste = clip.getItemAt(0).getText();
                        if (paste != null && terminalSession != null) {
                            terminalSession.write(paste.toString());
                        }
                    }
                }
            });
        }
        @Override public void onBell(@NonNull TerminalSession session) {}
        @Override public void onColorsChanged(@NonNull TerminalSession session) {}
        @Override public void onTerminalCursorStateChange(boolean state) {}
        @Override public void setTerminalShellPid(@NonNull TerminalSession session, int pid) { Log.d(TAG, "Shell PID: " + pid); }
        @Override public Integer getTerminalCursorStyle() { return null; }
        @Override public void logError(String tag, String message) { Log.e(tag, message); }
        @Override public void logWarn(String tag, String message) { Log.w(tag, message); }
        @Override public void logInfo(String tag, String message) { Log.i(tag, message); }
        @Override public void logDebug(String tag, String message) { Log.d(tag, message); }
        @Override public void logVerbose(String tag, String message) { Log.v(tag, message); }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Log.e(tag, message, e); }
        @Override public void logStackTrace(String tag, Exception e) { Log.e(tag, "Stack trace", e); }
    };
    
    private final TerminalViewClient terminalViewClient = new TerminalViewClient() {
        @Override public float onScale(float scale) { return scale; }
        @Override public void onSingleTapUp(MotionEvent e) { showKeyboard(); }
        @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
        @Override public boolean shouldEnforceCharBasedInput() { return false; }
        @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
        @Override public boolean isTerminalViewSelected() { return true; }
        @Override public void copyModeChanged(boolean copyMode) { }
        @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
        @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
        @Override public boolean onLongPress(MotionEvent event) { return false; }
        @Override public boolean readControlKey() { return false; }
        @Override public boolean readAltKey() { return false; }
        @Override public boolean readShiftKey() { return false; }
        @Override public boolean readFnKey() { return false; }
        @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
        @Override public void onEmulatorSet() { }
        @Override public void logError(String tag, String message) { Log.e(tag, message); }
        @Override public void logWarn(String tag, String message) { Log.w(tag, message); }
        @Override public void logInfo(String tag, String message) { Log.i(tag, message); }
        @Override public void logDebug(String tag, String message) { Log.d(tag, message); }
        @Override public void logVerbose(String tag, String message) { Log.v(tag, message); }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Log.e(tag, message, e); }
        @Override public void logStackTrace(String tag, Exception e) { Log.e(tag, "Stack trace", e); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        setupWindow();
        Log.d(TAG, "TerminalActivity created");
        
        // 检查是否是一次性命令模式
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_ONE_TIME_COMMAND)) {
            mIsOneTimeCommandMode = true;
            mOneTimeCommand = intent.getStringExtra(EXTRA_ONE_TIME_COMMAND);
            Log.d(TAG, "One-time command mode enabled, command: " + mOneTimeCommand);
        }
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        terminalView = findViewById(R.id.terminal_view);
        terminalView.setTerminalViewClient(terminalViewClient);
        terminalView.setTextSize(28);
        setSupportActionBar(toolbar);
        
        // 检查是否可以直接启动增强环境
        if (isEnhancedEnvironmentReady()) {
            Log.d(TAG, "Enhanced environment is ready, starting directly");
            mHandler.postDelayed(this::startEnhancedSession, 500);
        } else {
            // 否则启动引导会话
            Log.d(TAG, "Enhanced environment not ready, starting bootstrap");
            mHandler.postDelayed(this::startBootstrapSession, 1000);
        }
    }

    /**
     * 检查增强环境是否就绪
     */
    private boolean isEnhancedEnvironmentReady() {
        File envDir = getExecutableDir();
        File shellFile = new File(envDir, "bin/sh");
        File busyboxFile = new File(envDir, "bin/busybox");
        
        // 检查关键文件是否存在且可执行
        boolean filesExist = shellFile.exists() && busyboxFile.exists();
        boolean filesExecutable = shellFile.canExecute() && busyboxFile.canExecute();
        
        Log.d(TAG, "Enhanced environment check - Files exist: " + filesExist + ", Executable: " + filesExecutable);
        return filesExist && filesExecutable;
    }

    /**
     * 获取完整的系统环境变量
     */
    private Map<String, String> getSystemEnvironment() {
        Map<String, String> env = new HashMap<>();
        
        try {
            // 执行 env 命令获取系统环境变量
            Process process = Runtime.getRuntime().exec("env");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex);
                    String value = line.substring(equalsIndex + 1);
                    env.put(key, value);
                }
            }
            
            process.waitFor();
            reader.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get system environment", e);
        }
        
        return env;
    }

    /**
     * 构建完整的环境变量数组
     */
    private String[] buildEnhancedEnvironment(String homeDir, String binPath, String shellPath) {
        Map<String, String> systemEnv = getSystemEnvironment();
        Map<String, String> enhancedEnv = new HashMap<>();
        
        // 首先添加系统环境变量（过滤掉一些可能冲突的）
        for (Map.Entry<String, String> entry : systemEnv.entrySet()) {
            String key = entry.getKey();
            // 保留大部分系统变量，但排除一些可能冲突的
            if (!key.equals("PS1") && !key.equals("HOME") && !key.equals("PATH") && 
                !key.equals("SHELL") && !key.equals("PWD") && !key.equals("TMPDIR")) {
                enhancedEnv.put(key, entry.getValue());
            }
        }
        
        // 添加我们的核心环境变量（会覆盖系统中的冲突项）
        enhancedEnv.put("TERM", "xterm-256color");
        enhancedEnv.put("HOME", homeDir);
        enhancedEnv.put("PATH", binPath + ":/system/bin:/system/xbin:/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/odm/bin:/vendor/bin:/vendor/xbin");
        enhancedEnv.put("PWD", homeDir);
        enhancedEnv.put("TMPDIR", getCacheDir().getAbsolutePath());
        enhancedEnv.put("SHELL", shellPath);
        enhancedEnv.put("LD_LIBRARY_PATH",getFilesDir().getAbsolutePath()+"/terminal_env/lib");
        enhancedEnv.put("APP_NAME","com.wqry085.deployesystem");
        enhancedEnv.put("USER", "shell");
        enhancedEnv.put("LOGNAME", "shell");
        enhancedEnv.put("HOSTNAME", Build.MODEL != null ? Build.MODEL.replace(" ", "_") : "android");
        
        // 设置美观的PS1提示符
        enhancedEnv.put("PS1", "\\[$( [ $? -eq 0 ] && echo \"\\e[1;32m\" || echo \"\\e[1;31m\" )\\]➜ \\[\\e[1;36m\\]\\W\\[\\e[0m\\] ");
        
        // 添加Android特有的重要环境变量（如果系统中没有）
        if (!enhancedEnv.containsKey("ANDROID_DATA")) enhancedEnv.put("ANDROID_DATA", "/data");
        if (!enhancedEnv.containsKey("ANDROID_ROOT")) enhancedEnv.put("ANDROID_ROOT", "/system");
        if (!enhancedEnv.containsKey("ANDROID_STORAGE")) enhancedEnv.put("ANDROID_STORAGE", "/storage");
        if (!enhancedEnv.containsKey("EXTERNAL_STORAGE")) enhancedEnv.put("EXTERNAL_STORAGE", "/sdcard");
        if (!enhancedEnv.containsKey("ANDROID_ART_ROOT")) enhancedEnv.put("ANDROID_ART_ROOT", "/apex/com.android.art");
        if (!enhancedEnv.containsKey("ANDROID_I18N_ROOT")) enhancedEnv.put("ANDROID_I18N_ROOT", "/apex/com.android.i18n");
        if (!enhancedEnv.containsKey("ANDROID_TZDATA_ROOT")) enhancedEnv.put("ANDROID_TZDATA_ROOT", "/apex/com.android.tzdata");
        
        // 转换回数组格式
        String[] envArray = new String[enhancedEnv.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : enhancedEnv.entrySet()) {
            envArray[i++] = entry.getKey() + "=" + entry.getValue();
        }
        
        Log.d(TAG, "Built enhanced environment with " + envArray.length + " variables");
        return envArray;
    }

    /**
     * 构建引导环境变量
     */
    private String[] buildBootstrapEnvironment(String homeDir) {
        Map<String, String> systemEnv = getSystemEnvironment();
        Map<String, String> bootstrapEnv = new HashMap<>();
        
        // 添加系统环境变量
        for (Map.Entry<String, String> entry : systemEnv.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("PS1") && !key.equals("HOME") && !key.equals("PWD")) {
                bootstrapEnv.put(key, entry.getValue());
            }
        }
        
        // 设置基础环境变量
        bootstrapEnv.put("TERM", "xterm-256color");
        bootstrapEnv.put("HOME", homeDir);
        bootstrapEnv.put("PWD", homeDir);
        bootstrapEnv.put("TMPDIR", getCacheDir().getAbsolutePath());
        bootstrapEnv.put("PS1", "[\\u@\\h \\W]\\$ ");
        
        // 转换回数组格式
        String[] envArray = new String[bootstrapEnv.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : bootstrapEnv.entrySet()) {
            envArray[i++] = entry.getKey() + "=" + entry.getValue();
        }
        
        return envArray;
    }

    /**
     * 直接启动增强会话
     */
    private void startEnhancedSession() {
        Log.d(TAG, "Starting enhanced session directly");
        
        File envDir = getExecutableDir();
        String homeDir = getFilesDir().getAbsolutePath();
        String binPath = new File(envDir, "bin").getAbsolutePath();
        String shellPath = binPath + "/sh";

        // 使用完整的增强环境变量
        String[] environment = buildEnhancedEnvironment(homeDir, binPath, shellPath);
        
        terminalSession = new TerminalSession(
            shellPath, 
            homeDir, 
            new String[]{"sh", "-i"},
            environment, 
            null, 
            terminalSessionClient
        );
        terminalView.attachSession(terminalSession);

        // 如果是一次性命令模式，延迟执行命令
        if (mIsOneTimeCommandMode) {
            mHandler.postDelayed(this::executeOneTimeCommand, 200);
        }
    }

    /**
     * 启动引导会话
     */
    private void startBootstrapSession() {
        Log.d(TAG, "Starting bootstrap session with /system/bin/sh...");

        String homeDir = getFilesDir().getAbsolutePath();
        
        // 使用增强的引导环境变量
        String[] environment = buildBootstrapEnvironment(homeDir);

        terminalSession = new TerminalSession("/system/bin/sh", homeDir, new String[]{"-i"}, environment, null, terminalSessionClient);
        terminalView.attachSession(terminalSession);

        // 给会话更多时间初始化
        mHandler.postDelayed(this::runDeploymentLogic, 1500);
    }

    /**
     * 运行部署逻辑
     */
    private void runDeploymentLogic() {
        mExecutor.execute(() -> {
            File envDir = getExecutableDir();
            File flagFile = new File(envDir, ENV_FLAG_FILE);

            // 检查部署标志和完整性
            if (flagFile.exists() && verifyDeployment(envDir)) {
                Log.d(TAG, "Environment already deployed and verified.");
                runOnUiThread(this::switchToEnhancedSession);
            } else {
                Log.d(TAG, "Environment not deployed or verification failed. Starting deployment.");
                runOnUiThread(() -> {
                    executeCommand("echo '=== 正在部署终端环境 ==='");
                    executeCommand("echo '请稍候，这可能需要一些时间...'");
                });

                boolean success = deployEnvironment(envDir);

                if (success && verifyDeployment(envDir)) {
                    Log.d(TAG, "Deployment successful and verified.");
                    runOnUiThread(() -> {
                        executeCommand("echo '=== 部署完成 ==='");
                        mHandler.postDelayed(this::switchToEnhancedSession, 1000);
                    });
                } else {
                    Log.e(TAG, "Deployment or verification failed.");
                    runOnUiThread(() -> {
                        executeCommand("echo '=== 环境部署失败 ==='");
                        executeCommand("echo '您当前处于基础系统模式。'");
                        // 如果是一次性命令模式，即使在基础模式下也尝试执行命令
                        if (mIsOneTimeCommandMode) {
                            mHandler.postDelayed(this::executeOneTimeCommand, 1000);
                        }
                    });
                }
            }
        });
    }

    /**
     * 执行一次性命令
     */
    private void executeOneTimeCommand() {
        if (mOneTimeCommand != null && terminalSession != null && terminalSession.isRunning()) {
            Log.d(TAG, "Executing one-time command: " + mOneTimeCommand);
            // 执行命令后立即退出
            String fullCommand = mOneTimeCommand + "; exit\n";
            terminalSession.write(fullCommand);
        }
    }

    /**
     * 部署环境
     */
    private boolean deployEnvironment(File envDir) {
        Log.d(TAG, "Starting environment deployment to: " + envDir.getAbsolutePath());
        
        File flagFile = new File(envDir, ENV_FLAG_FILE);
        
        try {
            // 确保目标目录存在
            if (!envDir.exists() && !envDir.mkdirs()) {
                Log.e(TAG, "Failed to create environment directory");
                return false;
            }

            // 清理旧部署（如果有）
            cleanupOldDeployment(envDir);

            // 从 assets 提取 ZIP
            File zipFile = new File(getCacheDir(), "termarm64.zip");
            if (!extractAssetToFile("termarm64.zip", zipFile)) {
                Log.e(TAG, "Failed to extract asset");
                return false;
            }

            // 验证 ZIP 文件
            if (!verifyZipFile(zipFile)) {
                Log.e(TAG, "ZIP file verification failed");
                zipFile.delete();
                return false;
            }

            // 使用系统 unzip 解压
            if (!executeCommandWithTimeout(new String[]{"unzip", "-o", "-q", zipFile.getAbsolutePath(), "-d", envDir.getAbsolutePath()}, 30000)) {
                Log.e(TAG, "Unzip command failed");
                zipFile.delete();
                return false;
            }
            zipFile.delete();

            // 设置权限
            File binDir = new File(envDir, "bin");
            if (!executeCommandWithTimeout(new String[]{"chmod", "-R", "755", binDir.getAbsolutePath()}, 15000)) {
                Log.e(TAG, "Chmod command failed");
                return false;
            }

            // 运行安装脚本
            File installScript = new File(envDir, "install");
            if (installScript.exists()) {
                if (!executeCommandWithTimeout(new String[]{"sh", installScript.getAbsolutePath()}, 30000)) {
                    Log.e(TAG, "Install script execution failed");
                    return false;
                }
            }

            // 创建成功标志
            return flagFile.createNewFile();

        } catch (Exception e) {
            Log.e(TAG, "Exception during deployment", e);
            return false;
        }
    }

    /**
     * 验证部署完整性
     */
    private boolean verifyDeployment(File envDir) {
        File[] criticalFiles = {
            new File(envDir, "bin/busybox"),
            new File(envDir, "bin/sh"),
            new File(envDir, "install")
        };

        for (File file : criticalFiles) {
            if (!file.exists()) {
                Log.e(TAG, "Critical file missing: " + file.getAbsolutePath());
                return false;
            }
            if (file.getName().equals("busybox") && !file.canExecute()) {
                Log.e(TAG, "Busybox is not executable");
                return false;
            }
        }
        
        // 检查 busybox 是否能正常运行
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                new File(envDir, "bin/busybox").getAbsolutePath(), "echo", "test"
            });
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.e(TAG, "Busybox test execution failed with code: " + exitCode);
                return false;
            }
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, "Busybox verification failed", e);
            return false;
        }

        return true;
    }

    /**
     * 切换到增强会话
     */
    private void switchToEnhancedSession() {
        Log.d(TAG, "Switching to enhanced session...");

        File envDir = getExecutableDir();
        String homeDir = getFilesDir().getAbsolutePath();
        String binPath = new File(envDir, "bin").getAbsolutePath();
        String shellPath = binPath + "/sh";

        // 最终安全检查
        if (!verifyDeployment(envDir)) {
            Log.e(TAG, "Enhanced environment verification failed! Staying in bootstrap.");
            executeCommand("echo '错误：增强环境验证失败，保持基础模式'");
            // 如果是一次性命令模式，即使在基础模式下也尝试执行命令
            if (mIsOneTimeCommandMode) {
                mHandler.postDelayed(this::executeOneTimeCommand, 1000);
            }
            return;
        }

        // 结束旧的引导会话
        if (terminalSession != null && terminalSession.isRunning()) {
            terminalSession.finishIfRunning();
        }

        // 使用完整的增强环境变量启动新的增强会话
        String[] environment = buildEnhancedEnvironment(homeDir, binPath, shellPath);
        
        terminalSession = new TerminalSession(
            shellPath, 
            homeDir, 
            new String[]{"sh", "-i"},
            environment, 
            null, 
            terminalSessionClient
        );
        terminalView.attachSession(terminalSession);

        // 如果是一次性命令模式，延迟执行命令
        if (mIsOneTimeCommandMode) {
            mHandler.postDelayed(this::executeOneTimeCommand, 1000);
        }
    }

    // --- 辅助方法 ---

    /**
     * 使用更安全的目录
     */
    private File getExecutableDir() {
        return new File(getFilesDir(), "terminal_env");
    }

    /**
     * 清理旧部署
     */
    private void cleanupOldDeployment(File envDir) {
        try {
            if (envDir.exists()) {
                // 只删除文件，保留目录结构
                File[] files = envDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            file.delete();
                        } else if (file.isDirectory()) {
                            deleteDirectory(file);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Cleanup of old deployment failed", e);
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * 提取资源文件
     */
    private boolean extractAssetToFile(String assetName, File outputFile) {
        try (InputStream in = getAssets().open(assetName);
             OutputStream out = new FileOutputStream(outputFile)) {
            
            byte[] buffer = new byte[16384]; // 更大的缓冲区
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                // 简单的进度日志
                if (totalBytes % (1024 * 1024) == 0) { // 每MB日志一次
                    Log.d(TAG, "Extracted " + (totalBytes / (1024 * 1024)) + "MB");
                }
            }
            
            Log.d(TAG, "Asset extraction completed: " + totalBytes + " bytes");
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract asset: " + assetName, e);
            return false;
        }
    }

    /**
     * 验证ZIP文件
     */
    private boolean verifyZipFile(File zipFile) {
        return zipFile.exists() && zipFile.length() > 1000; // 简单的大小检查
    }

    /**
     * 执行命令并处理超时
     */
    private boolean executeCommandWithTimeout(String[] command, long timeoutMs) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            // 读取输出用于调试
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                Log.e(TAG, "Command timed out: " + String.join(" ", command));
                process.destroy();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Log.e(TAG, "Command failed with exit code " + exitCode + ": " + String.join(" ", command));
                Log.e(TAG, "Command output: " + output.toString());
                return false;
            }

            Log.d(TAG, "Command executed successfully: " + String.join(" ", command));
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Exception executing command: " + String.join(" ", command), e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void executeCommand(String command) {
        if (terminalSession != null && terminalSession.isRunning()) {
            terminalSession.write(command + "\n");
        }
    }

    private void setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void showKeyboard() {
        if (terminalView != null) {
            terminalView.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.postDelayed(this::showKeyboard, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIsShuttingDown = true;
        if (terminalSession != null) {
            terminalSession.finishIfRunning();
        }
        mExecutor.shutdownNow();
    }
}