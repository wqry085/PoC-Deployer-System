package com.wqry085.deployesystem;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import com.wqry085.deployesystem.next.LogView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HelpFragment extends Fragment {
    private static final String TAG = "HelpFragment";
    private static final String LOG_SERVER_HOST = "localhost";
    private static final int LOG_SERVER_PORT = 13568;
    private static final int SOCKET_TIMEOUT = 5000;
    private static final int INITIAL_RECONNECT_DELAY = 2000;  // 初始重连延迟 2秒
    private static final int MAX_RECONNECT_DELAY = 30000;     // 最大重连延迟 30秒

    private LogView logView;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    private ExecutorService logExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private int currentReconnectDelay = INITIAL_RECONNECT_DELAY;
    private int reconnectCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                             @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_help, container, false);
        initLogView(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startLogging();
    }

    /**
     * 初始化 LogView
     */
    private void initLogView(View view) {
        logView = view.findViewById(R.id.zygotelog);
        
        if (logView == null) {
            Log.e(TAG, "LogView not found in layout!");
            return;
        }
        
        logView.setShowStatusBar(true);
        logView.setShowLineNumbers(true);
        logView.setMonokaiTheme();
        logView.setTextSize(6f);
        logView.setScaleRange(8f, 28f);
    }

    /**
     * 启动日志捕获
     */
    private void startLogging() {
        if (isRunning.get()) {
            Log.w(TAG, "Logging already running");
            return;
        }
        
        isRunning.set(true);
        resetReconnectState();
        
        logExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LogReaderThread");
            t.setDaemon(true);
            return t;
        });
        
        appendLogSafe("System", "开始实时捕获 Zygote 日志...");
        logExecutor.execute(this::connectLoop);
    }

    /**
     * 无限重连循环
     */
    private void connectLoop() {
        while (isRunning.get()) {
            try {
                if (connect()) {
                    // 连接成功，重置重连状态
                    resetReconnectState();
                    readLogs();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in log connection", e);
            } finally {
                closeConnection();
            }
            
            // 如果还在运行，等待后重连
            if (isRunning.get()) {
                waitAndPrepareReconnect();
            }
        }
    }

    /**
     * 等待并准备重连（指数退避策略）
     */
    private void waitAndPrepareReconnect() {
        reconnectCount++;
        
        // 只在特定次数显示日志，避免刷屏
        if (shouldShowReconnectLog()) {
            appendLogSafe("System", "等待 " + (currentReconnectDelay / 1000) + " 秒后重连... (第 " + reconnectCount + " 次)");
        }
        
        try {
            Thread.sleep(currentReconnectDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // 指数退避：逐渐增加重连间隔，但不超过最大值
        currentReconnectDelay = Math.min(currentReconnectDelay * 2, MAX_RECONNECT_DELAY);
    }

    /**
     * 判断是否应该显示重连日志（避免刷屏）
     */
    private boolean shouldShowReconnectLog() {
        // 前5次每次都显示，之后每10次显示一次
        return reconnectCount <= 5 || reconnectCount % 10 == 0;
    }

    /**
     * 重置重连状态
     */
    private void resetReconnectState() {
        currentReconnectDelay = INITIAL_RECONNECT_DELAY;
        reconnectCount = 0;
    }

    /**
     * 建立 Socket 连接
     */
    private boolean connect() {
        try {
            appendLogSafe("System", "正在连接日志服务器...");
            
            socket = new Socket();
            socket.connect(new InetSocketAddress(LOG_SERVER_HOST, LOG_SERVER_PORT), SOCKET_TIMEOUT);
            socket.setSoTimeout(0); // 读取时不超时，持续等待数据
            socket.setKeepAlive(true);
            
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            isConnected.set(true);
            appendLogSafe("System", "✓ 已连接到日志服务器");
            
            // 发送 DUMP_LOGS 命令获取历史日志
            out.println("DUMP_LOGS");
            
            return true;
            
        } catch (SocketTimeoutException e) {
            // 连接超时时静默处理，避免刷屏
            Log.d(TAG, "Connection timeout");
        } catch (IOException e) {
            // 连接失败时静默处理
            Log.d(TAG, "Connection failed: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * 持续读取日志
     */
    private void readLogs() {
        try {
            String line;
            while (isRunning.get() && isConnected.get()) {
                line = in.readLine();
                if (line == null) {
                    // 服务器关闭连接
                    appendLogSafe("System", "服务器断开连接");
                    break;
                }
                appendLogSafe(line);
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                appendLogSafe("System", "连接中断: " + e.getMessage());
            }
        }
    }

    /**
     * 安全地追加日志（带标签）
     */
    private void appendLogSafe(String tag, String message) {
        appendLogSafe("[" + tag + "] " + message);
    }

    /**
     * 安全地追加日志到 LogView
     */
    private void appendLogSafe(final String logLine) {
        if (!isAdded() || getLifecycle().getCurrentState().ordinal() < Lifecycle.State.STARTED.ordinal()) {
            return;
        }
        
        mainHandler.post(() -> {
            if (logView != null && isAdded()) {
                logView.appendLog(logLine);
            }
        });
    }

    /**
     * 关闭连接
     */
    private void closeConnection() {
        isConnected.set(false);
        
        if (out != null) {
            try {
                out.close();
            } catch (Exception ignored) {}
            out = null;
        }
        
        if (in != null) {
            try {
                in.close();
            } catch (Exception ignored) {}
            in = null;
        }
        
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
            socket = null;
        }
    }

    /**
     * 停止日志捕获
     */
    private void stopLogging() {
        Log.d(TAG, "Stopping logging...");
        
        isRunning.set(false);
        closeConnection();
        
        if (logExecutor != null && !logExecutor.isShutdown()) {
            logExecutor.shutdownNow();
            try {
                logExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logExecutor = null;
        }
        
        Log.d(TAG, "Logging stopped");
    }

    /**
     * 公开方法：手动重新连接
     */
    public void reconnect() {
        stopLogging();
        startLogging();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        stopLogging();
        mainHandler.removeCallbacksAndMessages(null);
        
        if (logView != null) {
            logView.destroy();
            logView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLogging();
    }
}