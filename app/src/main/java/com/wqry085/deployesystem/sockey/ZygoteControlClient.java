package com.wqry085.deployesystem.sockey;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZygoteControlClient {
    private static final String TAG = "ZygoteControlClient";
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String host;
    private int port;
    private ZygoteControlListener listener;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isAuthenticated = new AtomicBoolean(false);
    private ReceiveThread receiveThread;
    
    // 用于累积多行响应
    private StringBuilder statusResponse = new StringBuilder();
    private StringBuilder historyResponse = new StringBuilder();
    private List<String> currentHistoryLines = new ArrayList<>();
    private boolean isReceivingStatus = false;
    private boolean isReceivingHistory = false;

    public ZygoteControlClient(String host, int port, ZygoteControlListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connect() {
        if (isConnected.get()) {
            Log.w(TAG, "Already connected");
            return;
        }
        
        new ConnectTask().execute();
    }

    public void disconnect() {
        if (!isConnected.get()) {
            return;
        }
        
        sendCommand(ControlCommands.EXIT);
        closeConnection();
    }

    public void getHistory() {
        sendCommand(ControlCommands.GET_HISTORY);
    }

    public void execshell(String cmd) {
        sendCommand(ControlCommands.EXEC+cmd);
    }

    public void terminateSystem() {
        sendCommand(ControlCommands.TERMINATE);
    }

    public void getStatus() {
        sendCommand(ControlCommands.STATUS);
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    private void sendCommand(final String command) {
        if (!isConnected.get() || !isAuthenticated.get()) {
            Log.e(TAG, "Not connected or authenticated");
            if (listener != null) {
                listener.onError("Not connected or authenticated");
            }
            return;
        }

        // 重置响应状态
        if (command.equals(ControlCommands.STATUS)) {
            isReceivingStatus = true;
            statusResponse.setLength(0);
        } else if (command.equals(ControlCommands.GET_HISTORY)) {
            isReceivingHistory = true;
            historyResponse.setLength(0);
            currentHistoryLines.clear();
        }

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    out.println(command);
                    out.flush();
                    Log.d(TAG, "Command sent: " + command);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Error sending command: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (!success && listener != null) {
                    listener.onError("Failed to send command: " + command);
                }
            }
        }.execute();
    }

    private class ConnectTask extends AsyncTask<Void, Void, Boolean> {
        private String errorMessage;

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                socket = new Socket(host, port);
                socket.setSoTimeout(30000); // 30秒超时
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // 发送认证密钥
                out.println(ControlCommands.CONTROL_KEY);
                out.flush();
                
                // 读取认证响应
                String response = in.readLine();
                if (response != null && response.equals(ControlCommands.AUTH_SUCCESS)) {
                    // 读取连接成功消息
                    String connectedMsg = in.readLine();
                    if (connectedMsg != null && connectedMsg.equals(ControlCommands.CONTROL_CONNECTED)) {
                        isAuthenticated.set(true);
                        return true;
                    }
                }
                errorMessage = "Authentication failed";
                return false;
            } catch (UnknownHostException e) {
                errorMessage = "Unknown host: " + e.getMessage();
                return false;
            } catch (IOException e) {
                errorMessage = "IO Error: " + e.getMessage();
                return false;
            } catch (Exception e) {
                errorMessage = "Connection error: " + e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                isConnected.set(true);
                startReceiveThread();
                if (listener != null) {
                    listener.onConnected();
                    listener.onAuthSuccess();
                }
                Log.i(TAG, "Connected and authenticated successfully");
            } else {
                closeConnection();
                if (listener != null) {
                    listener.onError(errorMessage);
                    listener.onAuthFailed();
                }
                Log.e(TAG, "Connection failed: " + errorMessage);
            }
        }
    }

    private void startReceiveThread() {
        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
        }
        receiveThread = new ReceiveThread();
        receiveThread.start();
    }

    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            try {
                String response;
                while (isConnected.get() && (response = in.readLine()) != null) {
                    Log.d(TAG, "Received: " + response);
                    handleResponse(response);
                    
                    // 检查连接是否应该关闭
                    if (response.equals(ControlCommands.CONTROL_EXIT_ACK)) {
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Receive thread error: " + e.getMessage());
                if (listener != null) {
                    listener.onError("Connection lost: " + e.getMessage());
                }
            } finally {
                closeConnection();
            }
        }

        private void handleResponse(String response) {
            if (listener == null) return;

            // 检查响应结束标记
            if (response.equals("END_OF_HISTORY")) {
                if (isReceivingHistory) {
                    isReceivingHistory = false;
                    if (listener != null) {
                        
                        listener.onHistoryComplete(currentHistoryLines);
                    }
                }
                return;
            }
            
            if (response.equals("END_OF_STATUS")) {
                if (isReceivingStatus) {
                    isReceivingStatus = false;
                    if (listener != null) {
                        listener.onStatusComplete(statusResponse.toString());
                    }
                }
                return;
            }
            
            if (response.equals("COMMAND_PROCESSED")) {
                // 命令处理完成，可以忽略或通知
                return;
            }

            // 处理多行响应
            if (isReceivingStatus) {
                statusResponse.append(response).append("\n");
                return;
            }
            
            if (isReceivingHistory) {
                currentHistoryLines.add(response);
                return;
            }

            // 处理单行响应
            switch (response) {
                case ControlCommands.SYSTEM_TERMINATING:
                case ControlCommands.UNKNOWN_COMMAND:
                case ControlCommands.AUTH_SUCCESS:
                case ControlCommands.CONTROL_CONNECTED:
                    if (listener != null) {
                        listener.onCommandResponse(response);
                    }
                    break;
                default:
                    // 可能是其他单行响应
                    if (listener != null) {
                        listener.onCommandResponse(response);
                    }
                    break;
            }
        }
    }

    private synchronized void closeConnection() {
        try {
            if (receiveThread != null && receiveThread.isAlive()) {
                receiveThread.interrupt();
            }
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        } finally {
            isConnected.set(false);
            isAuthenticated.set(false);
            isReceivingStatus = false;
            isReceivingHistory = false;
            if (listener != null) {
                listener.onDisconnected();
            }
            Log.i(TAG, "Connection closed");
        }
    }
}