package com.wqry085.deployesystem.sockey;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderReceiver {
    private static final String TAG = "FolderReceiver";
    private static final int PORT = 56423;
    
    private Context context;
    private ProgressDialog progressDialog;
    private AlertDialog resultDialog;
    private volatile boolean isReceiving = false;
    private Handler mainHandler;
    private ServerSocket serverSocket;
    private ExecutorService networkExecutor;
    
    // 协议常量
    private static final byte TYPE_FILE = 0x01;
    private static final byte TYPE_DIRECTORY = 0x02;
    
    public FolderReceiver(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.networkExecutor = Executors.newSingleThreadExecutor();
    }
    
    public void startReceiving() {
        if (isReceiving) {
            showToast("接收服务已在运行中");
            return;
        }
        
        networkExecutor.execute(this::startReceivingInternal);
    }
    
    private void startReceivingInternal() {
        isReceiving = true;
        showWaitingDialog("等待客户端连接端口 " + PORT + "...");
        Log.i(TAG, "开始监听端口 " + PORT);
        
        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setSoTimeout(1000); // 1秒超时，用于检查取消状态
            
            while (isReceiving) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Log.i(TAG, "客户端已连接: " + clientSocket.getInetAddress());
                    
                    // 处理文件传输
                    handleFileTransfer(clientSocket);
                    
                } catch (java.net.SocketTimeoutException e) {
                    // 超时，继续循环检查取消状态
                    continue;
                } catch (IOException e) {
                    if (isReceiving) {
                        Log.e(TAG, "接受连接错误: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "服务器启动失败: " + e.getMessage());
            showToast("服务器启动失败: " + e.getMessage());
        } finally {
            closeServerSocket();
            isReceiving = false;
            dismissWaitingDialog();
            Log.i(TAG, "接收服务已停止");
        }
    }
    
    private void handleFileTransfer(Socket clientSocket) {
        String baseDir = context.getExternalFilesDir(null).getAbsolutePath();
        int totalFiles = 0;
        int receivedFiles = 0;
        
        Log.i(TAG, "开始接收文件，保存路径: " + baseDir);
        updateProgressDialog("开始接收文件...", 0, 100);
        
        try {
            InputStream inputStream = clientSocket.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            
            while (isReceiving) {
                // 读取类型字节
                int typeByte = bis.read();
                if (typeByte == -1) {
                    Log.i(TAG, "流结束，传输完成");
                    break; // 正常结束
                }
                
                byte type = (byte) typeByte;
                Log.d(TAG, "读取类型: " + type);
                
                // 读取路径长度 (4字节，大端序)
                byte[] pathLenBytes = readExactly(bis, 4);
                if (pathLenBytes == null) break;
                
                int pathLength = ByteBuffer.wrap(pathLenBytes)
                                         .order(ByteOrder.BIG_ENDIAN)
                                         .getInt();
                Log.d(TAG, "路径长度: " + pathLength);
                
                if (pathLength <= 0 || pathLength > 8192) {
                    Log.e(TAG, "无效的路径长度: " + pathLength);
                    break;
                }
                
                // 读取路径
                byte[] pathBytes = readExactly(bis, pathLength);
                if (pathBytes == null) break;
                
                String relativePath = new String(pathBytes, "UTF-8");
                Log.d(TAG, "相对路径: " + relativePath);
                
                // 读取数据长度 (8字节，大端序)
                byte[] dataLenBytes = readExactly(bis, 8);
                if (dataLenBytes == null) break;
                
                long dataLength = ByteBuffer.wrap(dataLenBytes)
                                          .order(ByteOrder.BIG_ENDIAN)
                                          .getLong();
                Log.d(TAG, "数据长度: " + dataLength);
                
                String fullPath = baseDir + File.separator + relativePath;
                
                if (type == TYPE_FILE) {
                    totalFiles++;
                    receivedFiles++;
                    
                    // 更新UI
                    final String status = String.format("接收文件: %s (%s)\n进度: %d/%d", 
                            relativePath, formatFileSize(dataLength), receivedFiles, totalFiles);
                    
                    updateProgressDialog(status, (receivedFiles * 100) / Math.max(totalFiles, 1), 100);
                    
                    Log.i(TAG, "接收文件: " + relativePath + " 大小: " + dataLength + " bytes");
                    
                    // 创建父目录
                    File file = new File(fullPath);
                    File parentDir = file.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        if (!parentDir.mkdirs()) {
                            Log.e(TAG, "创建目录失败: " + parentDir.getAbsolutePath());
                            break;
                        }
                    }
                    
                    // 接收文件内容
                    if (!receiveFileContent(bis, file, dataLength)) {
                        Log.e(TAG, "文件内容接收失败: " + relativePath);
                        break;
                    }
                    
                    Log.i(TAG, "文件接收完成: " + relativePath);
                    
                } else if (type == TYPE_DIRECTORY) {
                    Log.i(TAG, "创建目录: " + relativePath);
                    
                    File dir = new File(fullPath);
                    if (!dir.exists() && !dir.mkdirs()) {
                        Log.e(TAG, "创建目录失败: " + fullPath);
                        break;
                    }
                    
                    // 更新UI显示目录创建
                    updateProgressDialog("创建目录: " + relativePath, 
                                       (receivedFiles * 100) / Math.max(totalFiles, 1), 100);
                } else {
                    Log.e(TAG, "未知的类型: " + type);
                    break;
                }
            }
            
            // 传输完成
            if (isReceiving) {
                final int finalReceived = receivedFiles;
                mainHandler.post(() -> {
                    showToast("文件接收完成，共接收 " + finalReceived + " 个文件");
                    // 恢复等待状态
                    showWaitingDialog("准备接收下一次传输...");
                });
                Log.i(TAG, "文件接收完成，总计: " + receivedFiles + " 个文件");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "文件传输错误: " + e.getMessage());
            showToast("文件接收错误: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭客户端socket错误: " + e.getMessage());
            }
        }
    }
    
    private byte[] readExactly(InputStream is, int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        
        while (totalRead < length && isReceiving) {
            int read = is.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                Log.e(TAG, "流提前结束，期望读取 " + length + " 字节，实际读取 " + totalRead + " 字节");
                return null;
            }
            totalRead += read;
        }
        
        return totalRead == length ? buffer : null;
    }
    
    private boolean receiveFileContent(InputStream is, File file, long dataLength) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            long totalRead = 0;
            
            while (totalRead < dataLength && isReceiving) {
                int toRead = (int) Math.min(buffer.length, dataLength - totalRead);
                int read = is.read(buffer, 0, toRead);
                
                if (read == -1) {
                    Log.e(TAG, "文件内容读取提前结束");
                    return false;
                }
                
                fos.write(buffer, 0, read);
                totalRead += read;
                
                // 大文件进度更新（每1MB更新一次）
                if (dataLength > 1024 * 1024 && totalRead % (1024 * 1024) == 0) {
                    int progress = (int) ((totalRead * 100) / dataLength);
                    Log.d(TAG, "文件传输进度: " + progress + "%");
                }
            }
            
            return totalRead == dataLength;
        } catch (IOException e) {
            Log.e(TAG, "写入文件错误: " + e.getMessage());
            return false;
        }
    }
    
    public void stopReceiving() {
        isReceiving = false;
        closeServerSocket();
        dismissWaitingDialog();
    }
    
    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭服务器socket错误: " + e.getMessage());
            }
            serverSocket = null;
        }
    }
    
    private void showWaitingDialog(String message) {
        mainHandler.post(() -> {
            dismissWaitingDialog();
        });
    }
    
    private void updateProgressDialog(String message, int progress, int max) {
        mainHandler.post(() -> {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog(context);
                progressDialog.setTitle("应用数据接收");
                progressDialog.setCancelable(true);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setOnCancelListener(dialog -> stopReceiving());
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(max);
            }
            
            progressDialog.setMessage(message);
            progressDialog.setProgress(progress);
            
            if (!progressDialog.isShowing()) {
                progressDialog.show();
            }
        });
    }
    
    private void dismissWaitingDialog() {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }
    
    private void showToast(String message) {
        mainHandler.post(() -> {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show();
        });
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
    
    public boolean isReceiving() {
        return isReceiving;
    }
    
    public void release() {
        stopReceiving();
        if (networkExecutor != null) {
            networkExecutor.shutdownNow();
        }
    }
}