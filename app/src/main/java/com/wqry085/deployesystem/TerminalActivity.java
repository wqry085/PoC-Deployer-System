package com.wqry085.deployesystem;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TerminalActivity extends AppCompatActivity {
    private static final String TAG = "TerminalActivity";
    private TerminalView terminalView;
    private TerminalSession terminalSession;
    private ScrollView scrollView;
    private EditText etInput;
    private Button btnSend;
    private Button btnClear;
    private Button btnTest;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        
        Log.d(TAG, "TerminalActivity created");

        // 初始化视图
        terminalView = findViewById(R.id.terminal_view);
        scrollView = findViewById(R.id.scroll_view);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);
        btnClear = findViewById(R.id.btn_clear);
        btnTest = findViewById(R.id.btn_test);
        tvStatus = findViewById(R.id.tv_status);

        // 初始化终端会话
        terminalSession = new TerminalSession();

        // 设置输出回调
        terminalSession.setOutputCallback(new TerminalSession.OutputCallback() {
            @Override
            public void onOutput(byte[] data, int size) {
                if (data != null && size > 0) {
                    try {
                        String output = new String(data, 0, size, StandardCharsets.UTF_8);
                        Log.d(TAG, "Received output: " + output.replace("\n", "\\n"));
                        
                        runOnUiThread(() -> {
                            terminalView.appendOutput(output);
                            // 自动滚动到底部
                            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                            updateStatus("Command executed");
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing output: " + e.getMessage());
                    }
                }
            }
        });

        // 发送按钮点击事件
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand();
            }
        });

        // 输入框回车键监听
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendCommand();
                return true;
            }
            return false;
        });

        // 清除按钮点击事件
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terminalView.clearTerminal();
                updateStatus("Terminal cleared");
            }
        });

        // 测试按钮点击事件
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (terminalSession.isRunning()) {
                    sendTestCommands();
                } else {
                    terminalView.appendOutput("\nPlease start terminal first!\n");
                    updateStatus("Terminal not running");
                }
            }
        });

        // 延迟启动终端会话
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startTerminalSession();
            }
        }, 1000);
    }

    private void sendCommand() {
        String command = etInput.getText().toString().trim();
        if (command.isEmpty()) {
            updateStatus("Please enter a command");
            return;
        }

        if (terminalSession.isRunning()) {
            // 在终端中显示输入的命令
            terminalView.appendOutput(command + "\n");
            // 发送到终端进程
            terminalSession.writeInput(command + "\n");
            // 清空输入框
            etInput.setText("");
            updateStatus("Command sent: " + command);
        } else {
            terminalView.appendOutput("\nTerminal not running!\n");
            updateStatus("Terminal not running");
        }
    }

    private void startTerminalSession() {
        Log.d(TAG, "Starting terminal session...");
        updateStatus("Starting terminal...");
        
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm-256color");
        env.put("PS1", "\\$ ");
        env.put("HOME", "/data/local/tmp");
        env.put("USER", "shell");

        boolean success = terminalSession.startSession("/system/bin/sh", new String[]{"-i"}, env);
        
        if (success) {
            Log.d(TAG, "Terminal session started successfully");
            updateStatus("Terminal ready");
            updateStatus("输入connect尝试连接");
        } else {
            Log.e(TAG, "Failed to start terminal session");
            updateStatus("Failed to start terminal");
            terminalView.appendOutput("Failed to start terminal session\n");
        }
    }

    private void sendTestCommands() {
        if (!terminalSession.isRunning()) {
            return;
        }

        updateStatus("Running test commands...");
        
        String[] testCommands = {
            "echo '=== Running Test Commands ==='",
            "pwd",
            "whoami", 
            "id",
            "echo '=== Test Completed ==='"
        };

        for (int i = 0; i < testCommands.length; i++) {
            final String cmd = testCommands[i];
            new Handler().postDelayed(() -> {
                terminalView.appendOutput(cmd + "\n");
                terminalSession.writeInput(cmd + "\n");
            }, i * 500);
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText("Status: " + message);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Activity resumed");
        if (etInput != null) {
            etInput.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");
        
        // 停止终端会话
        if (terminalSession != null) {
            terminalSession.stopSession();
        }
    }
}