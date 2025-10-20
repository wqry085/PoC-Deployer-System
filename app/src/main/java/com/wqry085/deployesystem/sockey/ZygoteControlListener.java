package com.wqry085.deployesystem.sockey;

import java.util.List;

public interface ZygoteControlListener {
    // 连接相关回调
    void onConnected();
    void onDisconnected();
    void onAuthSuccess();
    void onAuthFailed();
    void onError(String errorMessage);
    
    // 命令响应回调
    void onCommandResponse(String response);
    
    // 状态查询回调
    void onStatusComplete(String status);
    
    // 历史记录回调
    void onHistoryComplete(List<String> historyLines);
    
    // 实时历史记录更新（可选）
    void onHistoryLineReceived(String line);
}