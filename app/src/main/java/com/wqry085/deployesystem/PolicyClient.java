package com.wqry085.deployesystem;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PolicyClient {
    private static final String TAG = "PolicyClient";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8083;
    private static final int CONNECT_TIMEOUT = 1000;
    private static final int READ_TIMEOUT = 2000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 发送命令到策略守护进程
     * @param command 命令字符串
     * @return 响应字符串，失败返回 null
     */
    public static String sendCommand(String command) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT);
            socket.setSoTimeout(READ_TIMEOUT);

            OutputStream out = socket.getOutputStream();
            out.write(command.getBytes());
            out.flush();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            String response = reader.readLine();
            
            Log.d(TAG, "Command: " + command + " -> Response: " + response);
            return response;

        } catch (IOException e) {
            Log.e(TAG, "Failed to send command: " + command, e);
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 异步发送命令
     */
    public static void sendCommandAsync(String command, CommandCallback callback) {
        executor.execute(() -> {
            String response = sendCommand(command);
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(response));
            }
        });
    }

    /**
     * 检查守护进程是否运行
     */
    public static boolean isAlive() {
        String response = sendCommand("PING");
        return "PONG".equals(response);
    }

    public static void isAliveAsync(BooleanCallback callback) {
        executor.execute(() -> {
            boolean alive = isAlive();
            mainHandler.post(() -> callback.onResult(alive));
        });
    }

    /**
     * 获取白名单状态
     * @return true=启用, false=禁用, null=错误
     */
    public static Boolean isEnabled() {
        String response = sendCommand("STATUS");
        if (response == null) return null;
        return response.startsWith("ENABLED");
    }

    public static void isEnabledAsync(BooleanCallback callback) {
        executor.execute(() -> {
            Boolean enabled = isEnabled();
            mainHandler.post(() -> callback.onResult(enabled != null && enabled));
        });
    }

    /**
     * 启用白名单
     */
    public static boolean enable() {
        String response = sendCommand("ENABLE");
        return "OK".equals(response);
    }

    public static void enableAsync(BooleanCallback callback) {
        executor.execute(() -> {
            boolean result = enable();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * 禁用白名单
     */
    public static boolean disable() {
        String response = sendCommand("DISABLE");
        return "OK".equals(response);
    }

    public static void disableAsync(BooleanCallback callback) {
        executor.execute(() -> {
            boolean result = disable();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * 添加 UID 到白名单
     */
    public static boolean addUid(int uid) {
        String response = sendCommand("ADD " + uid);
        return "OK".equals(response);
    }

    public static void addUidAsync(int uid, BooleanCallback callback) {
        executor.execute(() -> {
            boolean result = addUid(uid);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * 从白名单移除 UID
     */
    public static boolean removeUid(int uid) {
        String response = sendCommand("REMOVE " + uid);
        return "OK".equals(response);
    }

    public static void removeUidAsync(int uid, BooleanCallback callback) {
        executor.execute(() -> {
            boolean result = removeUid(uid);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * 检查 UID 是否在白名单中
     */
    public static boolean checkUid(int uid) {
        String response = sendCommand("CHECK_UID " + uid);
        return "ALLOW".equals(response);
    }

    public static void checkUidAsync(int uid, BooleanCallback callback) {
        executor.execute(() -> {
            boolean result = checkUid(uid);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * 获取白名单中所有 UID
     */
    public static Set<Integer> getWhitelistedUids() {
        Set<Integer> uids = new HashSet<>();
        String response = sendCommand("LIST");
        
        if (response == null || response.isEmpty() || response.equals("(empty)")) {
            return uids;
        }

        try {
            String[] parts = response.split(",");
            for (String part : parts) {
                part = part.trim();
                if (!part.isEmpty()) {
                    uids.add(Integer.parseInt(part));
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse UID list: " + response, e);
        }

        return uids;
    }

    public static void getWhitelistedUidsAsync(UidSetCallback callback) {
        executor.execute(() -> {
            Set<Integer> uids = getWhitelistedUids();
            mainHandler.post(() -> callback.onResult(uids));
        });
    }

    /**
     * 保存白名单到文件
     */
    public static boolean save() {
        String response = sendCommand("SAVE");
        return "OK".equals(response);
    }

    public static void saveAsync(BooleanCallback callback) {
        executor.execute(() -> {
            boolean result = save();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * 从文件加载白名单
     */
    public static boolean load() {
        String response = sendCommand("LOAD");
        return "OK".equals(response);
    }

    // 回调接口
    public interface CommandCallback {
        void onResult(String response);
    }

    public interface BooleanCallback {
        void onResult(boolean result);
    }

    public interface UidSetCallback {
        void onResult(Set<Integer> uids);
    }
}