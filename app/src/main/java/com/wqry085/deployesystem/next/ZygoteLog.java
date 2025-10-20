package com.wqry085.deployesystem.next;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future; // 引入 Future
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
 
public class ZygoteLog {

    private static final String TAG = "ZygoteLog";
    private static final int SOCKET_PORT = 13568;
    private static final int UID_ROOT = 0;
    private static final int UID_SHELL = 2000;

    private static ServerSocket serverSocket;
    private static ExecutorService executor;
    private static AtomicBoolean isServiceRunning = new AtomicBoolean(false);
    private static AtomicBoolean shouldStopAllClientStreams = new AtomicBoolean(false);

    // 新增：定义日志获取模式的枚举
    private enum LogMode {
        PID,  // 通过 PID 获取
        GREP  // 通过 grep 关键字获取
    }

    public static void main(String[] args) {
        System.out.println(TAG + ": Starting main method.");
        
        Runtime.getRuntime().addShutdownHook(new Thread(ZygoteLog::performCleanup));
        
        int currentUid = -1;
        try {
            Class<?> processClass = Class.forName("android.os.Process");
            Method myUidMethod = processClass.getMethod("myUid");
            currentUid = (Integer) myUidMethod.invoke(null);
        } catch (Exception e) {
            System.err.println(TAG + ": Warning: Could not use android.os.Process.myUid(). Falling back to /proc/self/status. Error: " + e.getMessage());
            currentUid = getUidFromFile();
        }

        System.out.println(TAG + ": Current process UID: " + currentUid);

        if (currentUid != UID_ROOT && currentUid != UID_SHELL) {
            System.err.println(TAG + ": Insufficient UID. Required: " + UID_ROOT + " or " + UID_SHELL + ". Actual: " + currentUid + ". Exiting.");
            System.exit(1);
            return;
        }

        System.out.println(TAG + ": UID check passed. Starting log streamer...");

        executor = Executors.newCachedThreadPool();
        isServiceRunning.set(true);
        try {
            serverSocket = new ServerSocket(SOCKET_PORT);
            System.out.println(TAG + ": ServerSocket started on port " + SOCKET_PORT + ", waiting for client connection...");

            while (isServiceRunning.get()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(TAG + ": Client connected: " + clientSocket.getInetAddress());

                executor.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (isServiceRunning.get()) {
                System.err.println(TAG + ": ServerSocket error: " + e.getMessage());
            } else {
                System.out.println(TAG + ": ServerSocket closed gracefully.");
            }
        } finally {
            System.out.println(TAG + ": Main loop finished.");
        }
    }

    private static void performCleanup() {
        if (!isServiceRunning.getAndSet(false)) {
            System.out.println(TAG + ": Cleanup already initiated or not running.");
            return;
        }
        System.out.println(TAG + ": Shutdown hook activated. Initiating graceful cleanup...");
        shouldStopAllClientStreams.set(true);

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println(TAG + ": ServerSocket closed.");
            } catch (IOException e) {
                System.err.println(TAG + ": Error closing server socket: " + e.getMessage());
            }
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    System.err.println(TAG + ": Forcibly shutting down executor.");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println(TAG + ": Cleanup complete. Exiting application.");
    }

    private static int getUidFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Uid:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        return Integer.parseInt(parts[1]);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println(TAG + ": Error reading /proc/self/status for UID: " + e.getMessage());
        }
        return -1;
    }

    /**
     * 客户端处理器：处理单个客户端的请求，发送日志和接收命令
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        // 新增：用于持有当前日志流任务的 Future 对象，以便可以取消它
        private Future<?> logStreamerFuture;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // 启动默认的日志流（按 PID）
                startLogStreamer(LogMode.PID);

                // 监听客户端发送的命令
                String command;
                while (isServiceRunning.get() && clientSocket.isConnected() && !clientSocket.isClosed() && (command = in.readLine()) != null) {
                    System.out.println(TAG + ": Received command from client: " + command);
                    
                    // 处理 STOP 命令
                    if ("STOP".equalsIgnoreCase(command.trim())) {
                        System.out.println(TAG + ": Client requested STOP. Shutting down application (and process)...");
                        out.println("ACK: Application stopping.");
                        System.exit(0);
                    
                    // 新增：处理 TRY_GREP 命令
                    } else if ("TRY_GREP".equalsIgnoreCase(command.trim())) {
                        System.out.println(TAG + ": Client requested to try alternative log method (grep).");
                        out.println("ACK: Switching to grep mode for zygote logs.");
                        // 停止当前的日志流任务，然后用 grep 模式启动一个新的
                        stopCurrentLogStreamer();
                        startLogStreamer(LogMode.GREP);

                    } else if ("TRY_PID".equalsIgnoreCase(command.trim())) {
    System.out.println(TAG + ": Client requested to switch back to PID log method.");
    out.println("ACK: Switching to PID mode for zygote logs.");
    // 同样是先停止，再启动
    stopCurrentLogStreamer();
    startLogStreamer(LogMode.PID);
} else {
                        out.println("ACK: Unknown command '" + command + "'");
                    }
                }
            } catch (IOException e) {
                // 当客户端断开连接时，readLine() 可能会抛出异常
                if (!clientSocket.isClosed()) {
                    System.err.println(TAG + ": ClientHandler I/O error: " + e.getMessage());
                } else {
                    System.out.println(TAG + ": Client disconnected.");
                }
            } finally {
                System.out.println(TAG + ": ClientHandler finishing. Cleaning up resources.");
                stopCurrentLogStreamer(); // 确保在客户端断开时停止日志流
                closeClientSocket();
            }
        }

        /**
         * 新增：启动一个新的日志流任务
         * @param mode 日志获取模式 (PID 或 GREP)
         */
        private void startLogStreamer(LogMode mode) {
            if (logStreamerFuture != null && !logStreamerFuture.isDone()) {
                System.err.println(TAG + ": A log streamer is already running. Please stop it first.");
                return;
            }
            System.out.println(TAG + ": Starting log streamer in " + mode + " mode.");
            LogStreamerTask task = new LogStreamerTask(out, clientSocket, mode);
            logStreamerFuture = executor.submit(task);
        }

        /**
         * 新增：停止当前正在运行的日志流任务
         */
        private void stopCurrentLogStreamer() {
            if (logStreamerFuture != null && !logStreamerFuture.isDone()) {
                System.out.println(TAG + ": Attempting to stop current log streamer task...");
                // true 表示如果任务正在运行，就中断它
                logStreamerFuture.cancel(true); 
            }
        }

        private void closeClientSocket() {
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                    System.out.println(TAG + ": Client socket closed.");
                } catch (IOException e) {
                    System.err.println(TAG + ": Error closing client socket: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 日志流任务：获取zygote日志并通过Socket发送
     * 已修改为支持两种模式
     */
    private static class LogStreamerTask implements Runnable {
        private final PrintWriter out;
        private final Socket clientSocket;
        private final LogMode mode; // 新增：日志获取模式
        private Process logcatProcess;

        public LogStreamerTask(PrintWriter out, Socket clientSocket, LogMode mode) {
            this.out = out;
            this.clientSocket = clientSocket;
            this.mode = mode; // 接收模式
        }

        @Override
        public void run() {
            try {
                // 根据模式选择不同的 logcat 命令
                if (mode == LogMode.PID) {
                    streamLogsByPid();
                } else if (mode == LogMode.GREP) {
                    streamLogsByGrep();
                }
            } catch (IOException e) {
                // 当任务被中断（例如通过 future.cancel(true)），readLine() 可能会抛出 IOException
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println(TAG + ": LogStreamerTask was interrupted and is stopping.");
                } else {
                    System.err.println(TAG + ": Log streaming I/O error: " + e.getMessage());
                }
            } finally {
                // 清理资源
                if (logcatProcess != null) {
                    logcatProcess.destroy();
                    try {
                        logcatProcess.waitFor();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println(TAG + ": Logcat process destroyed in finally block.");
                }
                System.out.println(TAG + ": LogStreamerTask for mode " + mode + " finished.");
            }
        }

        /**
         * 模式一：通过 PID 获取日志（原始方法）
         */
        private void streamLogsByPid() throws IOException {
            String zygotePid = getZygotePid();
            if (zygotePid == null) {
                out.println("ERROR: Could not find zygote PID. You can try the 'TRY_GREP' command as an alternative.");
                return;
            }
            out.println("INFO: Zygote PID found: " + zygotePid + ". Streaming logs using PID mode.");
            System.out.println(TAG + ": Streaming logs for zygote PID: " + zygotePid);

            String[] command = {"logcat", "-v", "threadtime", "--pid=" + zygotePid};
            logcatProcess = Runtime.getRuntime().exec(command);
            streamProcessOutput(logcatProcess);
        }

        /**
         * 模式二：通过 grep 过滤关键字获取日志（新方法）
         */
        private void streamLogsByGrep() throws IOException {
            out.println("INFO: PID not found or alternative mode requested. Streaming logs using grep 'zygote' mode.");
            System.out.println(TAG + ": Streaming logs using grep for 'zygote'.");
            
            // 使用 sh -c 来执行包含管道符 | 的命令
            String[] command = {
                "/system/bin/sh",
                "-c",
                "logcat -v threadtime | grep -i zygote"
            };
            logcatProcess = Runtime.getRuntime().exec(command);
            streamProcessOutput(logcatProcess);
        }

        /**
         * 公共的流处理逻辑
         */
        private void streamProcessOutput(Process process) throws IOException {
            BufferedReader logcatReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while (!Thread.currentThread().isInterrupted() && (line = logcatReader.readLine()) != null) {
                out.println(line);
                if (out.checkError()) {
                    System.err.println(TAG + ": Client connection lost during log streaming.");
                    break;
                }
            }
            System.out.println(TAG + ": Log streaming loop ended.");
        }

        private String getZygotePid() {
            String[] zygoteNames = {"zygote64", "zygote"};
            for (String name : zygoteNames) {
                try {
                    Process pidofProcess = Runtime.getRuntime().exec(new String[]{"pidof", name});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(pidofProcess.getInputStream()));
                    String pid = reader.readLine();
                    int exitCode = pidofProcess.waitFor();
                    if (exitCode == 0 && pid != null && !pid.isEmpty()) {
                        return pid.trim();
                    }
                } catch (IOException | InterruptedException e) {
                    System.err.println(TAG + ": Error executing pidof for " + name + ": " + e.getMessage());
                    // 如果被中断，则设置中断标志并退出
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return null;
        }
    }
}