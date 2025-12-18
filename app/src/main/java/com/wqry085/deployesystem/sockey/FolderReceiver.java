package com.wqry085.deployesystem.sockey;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.wqry085.deployesystem.R;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

public class FolderReceiver {
    private static final String TAG = "FolderReceiver";
    
    // 协议常量
    private static final String PROTOCOL_MAGIC = "ZFTP";
    private static final int PROTOCOL_VERSION = 0x0002;
    private static final int HEADER_SIZE = 32;
    
    private static final byte TYPE_FILE = 0x01;
    private static final byte TYPE_DIRECTORY = 0x02;
    private static final byte TYPE_END = (byte) 0xFF;
    
    private static final byte CHECKSUM_NONE = 0x00;
    private static final byte CHECKSUM_CRC32 = 0x01;
    
    // 配置
    private static final int DEFAULT_PORT = 56423;
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int BUFFER_SIZE = 64 * 1024;
    
    // 状态
    private final WeakReference<Activity> activityRef;
    private final Context appContext;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final int port;
    
    private volatile boolean isReceiving = false;
    private volatile boolean isCancelled = false;
    private ServerSocket serverSocket;
    private ProgressDialog progressDialog;
    
    private TransferStats currentStats;
    private TransferCallback callback;
    
    // 回调接口
    public interface TransferCallback {
        void onServerStarted(int port);
        void onServerStopped();
        void onTransferStarted(int totalFiles, int totalDirs, long totalSize);
        void onFileReceived(String path, long size, int current, int total);
        void onDirectoryCreated(String path);
        void onTransferComplete(TransferStats stats);
        void onTransferError(String error);
    }
    
    // 简化的回调适配器（可选实现）
    public static class SimpleCallback implements TransferCallback {
        @Override public void onServerStarted(int port) {}
        @Override public void onServerStopped() {}
        @Override public void onTransferStarted(int totalFiles, int totalDirs, long totalSize) {}
        @Override public void onFileReceived(String path, long size, int current, int total) {}
        @Override public void onDirectoryCreated(String path) {}
        @Override public void onTransferComplete(TransferStats stats) {}
        @Override public void onTransferError(String error) {}
    }
    
    // 传输统计类
    public static class TransferStats {
        public int filesReceived;
        public int dirsCreated;
        public long bytesReceived;
        public long duration;
        public int checksumErrors;
        
        public double getSpeedMBps() {
            if (duration <= 0) return 0;
            return (bytesReceived / (1024.0 * 1024.0)) / (duration / 1000.0);
        }
        
        public static String formatSize(long size) {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
        
        @Override
        public String toString() {
            return String.format("Files: %d, Dirs: %d, Size: %s, Speed: %.2f MB/s",
                    filesReceived, dirsCreated, formatSize(bytesReceived), getSpeedMBps());
        }
    }
    
    // ==================== 构造函数 ====================
    
    public FolderReceiver(Activity activity) {
        this(activity, DEFAULT_PORT);
    }
    
    public FolderReceiver(Activity activity, int port) {
        this.activityRef = new WeakReference<>(activity);
        this.appContext = activity.getApplicationContext();
        this.port = port;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FolderReceiver");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
    }
    
    public FolderReceiver(Context context) {
        this(context, DEFAULT_PORT);
    }
    
    public FolderReceiver(Context context, int port) {
        if (context instanceof Activity) {
            this.activityRef = new WeakReference<>((Activity) context);
        } else {
            this.activityRef = new WeakReference<>(null);
        }
        this.appContext = context.getApplicationContext();
        this.port = port;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FolderReceiver");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
    }
    
    public void setCallback(TransferCallback callback) {
        this.callback = callback;
    }
    
    public int getPort() {
        return port;
    }
    
    // ==================== 公开方法 ====================
    
    public void startReceiving() {
        if (isReceiving) {
            showToast(getString(R.string.folder_receiver_service_running));
            return;
        }
        
        isCancelled = false;
        executor.execute(this::receiveLoop);
    }
    
    public void stopReceiving() {
        isCancelled = true;
        isReceiving = false;
        closeServerSocket();
        dismissProgressDialog();
    }
    
    public boolean isReceiving() {
        return isReceiving;
    }
    
    public void release() {
        stopReceiving();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== 接收循环 ====================
    
    private void receiveLoop() {
        isReceiving = true;
        
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(1000);
            
            Log.i(TAG, "Server started on port " + port);
            
            // ✅ 只发通知/回调，不显示弹窗
            notifyServerStarted();
            
            while (!isCancelled) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(SOCKET_TIMEOUT_MS);
                    client.setReceiveBufferSize(256 * 1024);
                    
                    Log.i(TAG, "Client connected: " + client.getInetAddress());
                    handleClient(client);
                    
                } catch (SocketTimeoutException e) {
                    // 正常超时，继续循环
                }
            }
            
        } catch (IOException e) {
            if (!isCancelled) {
                Log.e(TAG, "Server error: " + e.getMessage());
                notifyError("Server error: " + e.getMessage());
            }
        } finally {
            closeServerSocket();
            isReceiving = false;
            notifyServerStopped();
            Log.i(TAG, "Server stopped");
        }
    }
    
    // ==================== 客户端处理 ====================
    
    private void handleClient(Socket client) {
        String saveDir = appContext.getExternalFilesDir(null).getAbsolutePath();
        currentStats = new TransferStats();
        long startTime = System.currentTimeMillis();
        
        try (BufferedInputStream bis = new BufferedInputStream(
                client.getInputStream(), BUFFER_SIZE)) {
            
            // 1. 读取并验证协议头
            ProtocolHeader header = readHeader(bis);
            if (header == null) {
                notifyError("Invalid protocol header");
                return;
            }
            
            Log.i(TAG, String.format("Transfer started: %d files, %d dirs, %s",
                    header.totalFiles, header.totalDirs, 
                    TransferStats.formatSize(header.totalSize)));
            
            // ✅ 开始接收时显示进度弹窗
            notifyTransferStarted(header.totalFiles, header.totalDirs, header.totalSize);
            showProgressDialog(
                    getString(R.string.folder_receiver_starting_file_receive),
                    0, 
                    header.totalFiles + header.totalDirs
            );
            
            // 2. 接收条目
            int entryCount = 0;
            while (!isCancelled) {
                int typeByte = bis.read();
                if (typeByte == -1) {
                    Log.w(TAG, "Unexpected end of stream");
                    break;
                }
                
                byte type = (byte) typeByte;
                
                if (type == TYPE_END) {
                    Log.i(TAG, "End marker received");
                    break;
                }
                
                if (type != TYPE_FILE && type != TYPE_DIRECTORY) {
                    Log.e(TAG, "Unknown entry type: " + type);
                    break;
                }
                
                EntryResult result = readEntry(bis, type, header.checksumType, saveDir);
                if (result == null) {
                    Log.e(TAG, "Failed to read entry");
                    break;
                }
                
                entryCount++;
                updateProgress(result.path, entryCount, header.totalFiles + header.totalDirs);
                
                if (type == TYPE_FILE) {
                    currentStats.filesReceived++;
                    currentStats.bytesReceived += result.size;
                    notifyFileReceived(result.path, result.size, 
                            currentStats.filesReceived, header.totalFiles);
                } else {
                    currentStats.dirsCreated++;
                    notifyDirectoryCreated(result.path);
                }
            }
            
            // 3. 完成
            currentStats.duration = System.currentTimeMillis() - startTime;
            
            // ✅ 接收完成，关闭弹窗
            dismissProgressDialog();
            
            if (!isCancelled) {
                Log.i(TAG, "Transfer complete: " + currentStats);
                notifyTransferComplete(currentStats);
                
                // 显示完成提示
                showToast(String.format(
                        getString(R.string.folder_receiver_receive_complete),
                        currentStats.filesReceived
                ));
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Transfer error: " + e.getMessage());
            dismissProgressDialog();
            notifyError("Transfer error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing client: " + e.getMessage());
            }
            // ✅ 不再显示等待弹窗，服务继续后台运行
        }
    }
    
    // ==================== 协议头解析 ====================
    
    private static class ProtocolHeader {
        int totalFiles;
        int totalDirs;
        long totalSize;
        byte checksumType;
    }
    
    private ProtocolHeader readHeader(InputStream is) throws IOException {
        byte[] headerBytes = readExactly(is, HEADER_SIZE);
        if (headerBytes == null) {
            return null;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
        
        byte[] magic = new byte[4];
        buf.get(magic);
        if (!PROTOCOL_MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
            Log.e(TAG, "Invalid magic: " + new String(magic));
            return null;
        }
        
        int version = buf.getShort() & 0xFFFF;
        if (version > PROTOCOL_VERSION) {
            Log.w(TAG, "Newer protocol version: " + version);
        }
        
        ProtocolHeader header = new ProtocolHeader();
        buf.getShort();  // flags
        header.totalFiles = buf.getInt();
        header.totalDirs = buf.getInt();
        header.totalSize = buf.getLong();
        header.checksumType = buf.get();
        
        return header;
    }
    
    // ==================== 条目解析 ====================
    
    private static class EntryResult {
        String path;
        long size;
        boolean checksumValid;
    }
    
    private EntryResult readEntry(InputStream is, byte type, byte checksumType, 
                                  String baseDir) throws IOException {
        byte[] pathLenBytes = readExactly(is, 2);
        if (pathLenBytes == null) return null;
        
        int pathLength = ByteBuffer.wrap(pathLenBytes)
                .order(ByteOrder.BIG_ENDIAN)
                .getShort() & 0xFFFF;
        
        if (pathLength <= 0 || pathLength > 4096) {
            return null;
        }
        
        byte[] pathBytes = readExactly(is, pathLength);
        if (pathBytes == null) return null;
        
        String relativePath = new String(pathBytes, StandardCharsets.UTF_8);
        
        if (relativePath.contains("..") || relativePath.startsWith("/")) {
            return null;
        }
        
        byte[] dataLenBytes = readExactly(is, 8);
        if (dataLenBytes == null) return null;
        
        long dataLength = ByteBuffer.wrap(dataLenBytes)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
        
        long expectedCrc = 0;
        if (checksumType == CHECKSUM_CRC32) {
            byte[] crcBytes = readExactly(is, 4);
            if (crcBytes == null) return null;
            expectedCrc = ByteBuffer.wrap(crcBytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .getInt() & 0xFFFFFFFFL;
        }
        
        String fullPath = baseDir + File.separator + relativePath;
        EntryResult result = new EntryResult();
        result.path = relativePath;
        result.size = dataLength;
        result.checksumValid = true;
        
        if (type == TYPE_DIRECTORY) {
            File dir = new File(fullPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } else if (type == TYPE_FILE) {
            File file = new File(fullPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            result.checksumValid = receiveFileContent(is, file, dataLength, 
                    checksumType == CHECKSUM_CRC32, expectedCrc);
            
            if (!result.checksumValid) {
                currentStats.checksumErrors++;
            }
        }
        
        return result;
    }
    
    private boolean receiveFileContent(InputStream is, File file, long dataLength,
                                       boolean verifyCrc, long expectedCrc) throws IOException {
        CRC32 crc = verifyCrc ? new CRC32() : null;
        
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = dataLength;
            
            while (remaining > 0 && !isCancelled) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = is.read(buffer, 0, toRead);
                
                if (read == -1) {
                    return false;
                }
                
                bos.write(buffer, 0, read);
                
                if (crc != null) {
                    crc.update(buffer, 0, read);
                }
                
                remaining -= read;
            }
            
            bos.flush();
        }
        
        return crc == null || crc.getValue() == expectedCrc;
    }
    
    // ==================== 工具方法 ====================
    
    private byte[] readExactly(InputStream is, int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        
        while (totalRead < length && !isCancelled) {
            int read = is.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                return null;
            }
            totalRead += read;
        }
        
        return isCancelled ? null : buffer;
    }
    
    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing server: " + e.getMessage());
            }
            serverSocket = null;
        }
    }
    
    private String getString(int resId) {
        return appContext.getString(resId);
    }
    
    private String getString(int resId, Object... args) {
        return appContext.getString(resId, args);
    }
    
    private Activity getActivity() {
        return activityRef.get();
    }
    
    private boolean isActivityValid() {
        Activity activity = getActivity();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
    
    // ==================== UI 方法 ====================
    
    private void showProgressDialog(String message, int progress, int max) {
        mainHandler.post(() -> {
            if (!isActivityValid()) {
                return;
            }
            
            try {
                dismissProgressDialogInternal();
                
                Activity activity = getActivity();
                progressDialog = new ProgressDialog(activity);
                progressDialog.setTitle(getString(R.string.folder_receiver_progress_dialog_title));
                progressDialog.setMessage(message);
                progressDialog.setCancelable(true);
                progressDialog.setOnCancelListener(d -> {
                    isCancelled = true;
                    showToast("Transfer cancelled");
                });
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(max);
                progressDialog.setProgress(progress);
                progressDialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Cannot show progress dialog: " + e.getMessage());
            }
        });
    }
    
    private void updateProgress(String path, int current, int total) {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                // 只显示文件名，不显示完整路径
                String fileName = path;
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    fileName = path.substring(lastSlash + 1);
                }
                progressDialog.setMessage(fileName);
                progressDialog.setProgress(current);
            }
        });
    }
    
    private void dismissProgressDialog() {
        mainHandler.post(this::dismissProgressDialogInternal);
    }
    
    private void dismissProgressDialogInternal() {
        if (progressDialog != null) {
            try {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                // ignore
            }
            progressDialog = null;
        }
    }
    
    private void showToast(String message) {
        mainHandler.post(() -> {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show();
        });
    }
    
    // ==================== 回调通知 ====================
    
    private void notifyServerStarted() {
        if (callback != null) {
            mainHandler.post(() -> callback.onServerStarted(port));
        }
        // 可选：显示 Toast 提示服务已启动
        // showToast("Receiver ready on port " + port);
    }
    
    private void notifyServerStopped() {
        if (callback != null) {
            mainHandler.post(() -> callback.onServerStopped());
        }
    }
    
    private void notifyTransferStarted(int files, int dirs, long size) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTransferStarted(files, dirs, size));
        }
    }
    
    private void notifyFileReceived(String path, long size, int current, int total) {
        if (callback != null) {
            mainHandler.post(() -> callback.onFileReceived(path, size, current, total));
        }
    }
    
    private void notifyDirectoryCreated(String path) {
        if (callback != null) {
            mainHandler.post(() -> callback.onDirectoryCreated(path));
        }
    }
    
    private void notifyTransferComplete(TransferStats stats) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTransferComplete(stats));
        }
    }
    
    private void notifyError(String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTransferError(error));
        }
    }
}