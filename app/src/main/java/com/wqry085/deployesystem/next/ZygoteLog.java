package com.wqry085.deployesystem.next;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZygoteLog {

    private static final String TAG = "ZygoteLog";
    private static final int SOCKET_PORT = 13568;
    private static final int UID_ROOT = 0;
    private static final int UID_SHELL = 2000;
    private static final int MAX_LOG_HISTORY = 1000; // 最大历史日志条数
    private static final int CLIENT_TIMEOUT = 30000; // 客户端超时 30秒

    private static ServerSocket serverSocket;
    private static ExecutorService executor;
    private static final AtomicBoolean isServiceRunning = new AtomicBoolean(false);
    private static final List<String> logHistory = new CopyOnWriteArrayList<>();
    private static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    private enum LogMode {
        PID,
        GREP
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ZygoteLog::performCleanup));

        int currentUid = getCurrentUid();

        if (currentUid != UID_ROOT && currentUid != UID_SHELL) {
            System.err.println(TAG + ": Insufficient UID. Required: " + UID_ROOT + " or " + UID_SHELL + ". Actual: " + currentUid + ". Exiting.");
            System.exit(1);
            return;
        }

        System.out.println(TAG + ": Starting ZygoteLog service on port " + SOCKET_PORT + " (UID: " + currentUid + ")");

        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        
        isServiceRunning.set(true);
        
        try {
            serverSocket = new ServerSocket(SOCKET_PORT);
            serverSocket.setReuseAddress(true);
            
            System.out.println(TAG + ": Server started, waiting for connections...");
            
            while (isServiceRunning.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setKeepAlive(true);
                    clientSocket.setSoTimeout(CLIENT_TIMEOUT);
                    
                    ClientHandler handler = new ClientHandler(clientSocket);
                    activeClients.add(handler);
                    executor.execute(handler);
                    
                    System.out.println(TAG + ": Client connected. Active clients: " + activeClients.size());
                } catch (SocketException e) {
                    if (isServiceRunning.get()) {
                        System.err.println(TAG + ": Socket accept error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (isServiceRunning.get()) {
                System.err.println(TAG + ": ServerSocket error: " + e.getMessage());
            }
        } finally {
            System.out.println(TAG + ": Main loop finished.");
        }
    }

    /**
     * 获取当前 UID
     */
    private static int getCurrentUid() {
        int currentUid = -1;
        try {
            Class<?> processClass = Class.forName("android.os.Process");
            Method myUidMethod = processClass.getMethod("myUid");
            currentUid = (Integer) myUidMethod.invoke(null);
        } catch (Exception e) {
            System.err.println(TAG + ": Warning: Could not use android.os.Process.myUid(). Falling back to /proc/self/status.");
            currentUid = getUidFromFile();
        }
        return currentUid;
    }

    /**
     * 广播日志到所有连接的客户端
     */
    private static void broadcastLog(String logLine) {
        // 添加到历史记录
        addToHistory(logLine);
        
        // 广播给所有活跃客户端
        Iterator<ClientHandler> iterator = activeClients.iterator();
        while (iterator.hasNext()) {
            ClientHandler client = iterator.next();
            if (!client.sendLog(logLine)) {
                // 发送失败，客户端可能已断开
                iterator.remove();
            }
        }
    }

    /**
     * 添加到历史记录（限制大小）
     */
    private static void addToHistory(String logLine) {
        logHistory.add(logLine);
        // 限制历史记录大小
        while (logHistory.size() > MAX_LOG_HISTORY) {
            logHistory.remove(0);
        }
    }

    /**
     * 移除客户端
     */
    private static void removeClient(ClientHandler client) {
        activeClients.remove(client);
        System.out.println(TAG + ": Client disconnected. Active clients: " + activeClients.size());
    }

    private static void performCleanup() {
        if (!isServiceRunning.getAndSet(false)) {
            return;
        }
        System.out.println(TAG + ": Shutdown hook activated. Initiating graceful cleanup...");

        // 关闭所有客户端连接
        for (ClientHandler client : activeClients) {
            client.close();
        }
        activeClients.clear();

        // 关闭服务器 Socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println(TAG + ": Error closing server socket: " + e.getMessage());
            }
        }

        // 关闭线程池
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println(TAG + ": Cleanup complete.");
    }

    private static int getUidFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Uid:")) {
                    return Integer.parseInt(line.split("\\s+")[1]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println(TAG + ": Error reading /proc/self/status for UID: " + e.getMessage());
        }
        return -1;
    }

    /**
     * 客户端处理器
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private Future<?> logStreamerFuture;
        private final AtomicBoolean isRunning = new AtomicBoolean(true);
        private final Object writeLock = new Object();

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        /**
         * 发送日志到客户端
         * @return true 如果发送成功，false 如果客户端已断开
         */
        public boolean sendLog(String logLine) {
            if (!isRunning.get() || out == null) {
                return false;
            }
            
            synchronized (writeLock) {
                if (out != null && !out.checkError()) {
                    out.println(logLine);
                    return !out.checkError();
                }
            }
            return false;
        }

        /**
         * 关闭客户端连接
         */
        public void close() {
            isRunning.set(false);
            stopCurrentLogStreamer();
            closeClientSocket();
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // 发送欢迎消息
                sendLog("INFO: Connected to ZygoteLog server");
                sendLog("INFO: Commands: DUMP_LOGS, TRY_PID, TRY_GREP, STOP");

                // 启动日志流
                startLogStreamer(LogMode.PID);

                String command;
                while (isServiceRunning.get() && isRunning.get() && !clientSocket.isClosed()) {
                    try {
                        command = in.readLine();
                        if (command == null) {
                            // 客户端断开连接
                            break;
                        }
                        handleCommand(command.trim());
                    } catch (java.net.SocketTimeoutException e) {
                        // 读取超时，发送心跳检测
                        if (out.checkError()) {
                            // 客户端已断开
                            break;
                        }
                        // 继续等待命令
                    }
                }
            } catch (IOException e) {
                if (isRunning.get() && !clientSocket.isClosed()) {
                    System.err.println(TAG + ": ClientHandler I/O error: " + e.getMessage());
                }
            } finally {
                System.out.println(TAG + ": ClientHandler finishing.");
                close();
                removeClient(this);
            }
        }

        /**
         * 处理客户端命令
         */
        private void handleCommand(String command) {
            if (command.isEmpty()) {
                return;
            }
            
            System.out.println(TAG + ": Received command: " + command);

            switch (command.toUpperCase()) {
                case "STOP":
                    System.out.println(TAG + ": Client requested STOP. Shutting down...");
                    sendLog("ACK: Application stopping.");
                    System.exit(0);
                    break;

                case "TRY_GREP":
                    System.out.println(TAG + ": Switching to grep mode.");
                    sendLog("ACK: Switching to grep mode.");
                    stopCurrentLogStreamer();
                    startLogStreamer(LogMode.GREP);
                    break;

                case "TRY_PID":
                    System.out.println(TAG + ": Switching to PID mode.");
                    sendLog("ACK: Switching to PID mode.");
                    stopCurrentLogStreamer();
                    startLogStreamer(LogMode.PID);
                    break;

                case "DUMP_LOGS":
                    System.out.println(TAG + ": Dumping log history (" + logHistory.size() + " entries).");
                    sendLog("ACK: Dumping " + logHistory.size() + " log entries.");
                    for (String logLine : logHistory) {
                        sendLog(logLine);
                    }
                    sendLog("INFO: Log history dump complete.");
                    break;

                case "PING":
                    sendLog("PONG");
                    break;

                case "STATUS":
                    sendLog("INFO: Active clients: " + activeClients.size());
                    sendLog("INFO: Log history size: " + logHistory.size());
                    sendLog("INFO: Service running: " + isServiceRunning.get());
                    break;

                case "CLEAR_HISTORY":
                    logHistory.clear();
                    sendLog("ACK: Log history cleared.");
                    break;

                default:
                    sendLog("ACK: Unknown command '" + command + "'");
                    break;
            }
        }

        private void startLogStreamer(LogMode mode) {
            if (logStreamerFuture != null && !logStreamerFuture.isDone()) {
                return;
            }
            LogStreamerTask task = new LogStreamerTask(this, mode);
            logStreamerFuture = executor.submit(task);
        }

        private void stopCurrentLogStreamer() {
            if (logStreamerFuture != null && !logStreamerFuture.isDone()) {
                logStreamerFuture.cancel(true);
                logStreamerFuture = null;
            }
        }

        private void closeClientSocket() {
            // 关闭输出流
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {}
                out = null;
            }
            
            // 关闭输入流
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
                in = null;
            }
            
            // 关闭 Socket
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println(TAG + ": Error closing client socket: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 日志流任务
     */
    private static class LogStreamerTask implements Runnable {
        private final ClientHandler client;
        private final LogMode mode;
        private Process logcatProcess;

        public LogStreamerTask(ClientHandler client, LogMode mode) {
            this.client = client;
            this.mode = mode;
        }

        @Override
        public void run() {
            try {
                if (mode == LogMode.PID) {
                    streamLogsByPid();
                } else {
                    streamLogsByGrep();
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println(TAG + ": Log streaming error: " + e.getMessage());
                    client.sendLog("ERROR: Log streaming failed: " + e.getMessage());
                }
            } finally {
                destroyProcess();
                System.out.println(TAG + ": LogStreamerTask (" + mode + ") finished.");
            }
        }

        private void streamLogsByPid() throws IOException {
            String zygotePid = getZygotePid();
            if (zygotePid == null) {
                String errorMsg = "ERROR: Could not find zygote PID. Try 'TRY_GREP'.";
                client.sendLog(errorMsg);
                broadcastLog(errorMsg);
                return;
            }
            
            String infoMsg = "INFO: Zygote PID found: " + zygotePid + ". Streaming logs...";
            client.sendLog(infoMsg);
            addToHistory(infoMsg);

            String[] command = {"logcat", "-v", "threadtime", "--pid=" + zygotePid};
            logcatProcess = Runtime.getRuntime().exec(command);
            streamProcessOutput();
        }

        private void streamLogsByGrep() throws IOException {
            String infoMsg = "INFO: Streaming logs using grep 'zygote'...";
            client.sendLog(infoMsg);
            addToHistory(infoMsg);

            String[] command = {"/system/bin/sh", "-c", "logcat -v threadtime | grep -i zygote"};
            logcatProcess = Runtime.getRuntime().exec(command);
            streamProcessOutput();
        }

        private void streamProcessOutput() throws IOException {
            if (logcatProcess == null) {
                return;
            }
            
            try (BufferedReader logcatReader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream()))) {
                
                String line;
                while (!Thread.currentThread().isInterrupted() && (line = logcatReader.readLine()) != null) {
                    // 添加到历史并发送给客户端
                    addToHistory(line);
                    
                    if (!client.sendLog(line)) {
                        // 客户端断开，停止流
                        break;
                    }
                }
            }
        }

        private void destroyProcess() {
            if (logcatProcess != null) {
                try {
                    logcatProcess.destroy();
                    // 等待进程结束
                    logcatProcess.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    logcatProcess = null;
                }
            }
        }

        private String getZygotePid() {
            String[] zygoteNames = {"zygote64", "zygote"};
            for (String name : zygoteNames) {
                try {
                    Process pidofProcess = Runtime.getRuntime().exec(new String[]{"pidof", name});
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(pidofProcess.getInputStream()))) {
                        String pid = reader.readLine();
                        if (pidofProcess.waitFor() == 0 && pid != null && !pid.isEmpty()) {
                            return pid.trim().split("\\s+")[0]; // 取第一个 PID
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    System.err.println(TAG + ": Error executing pidof for " + name + ": " + e.getMessage());
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            return null;
        }
    }
}