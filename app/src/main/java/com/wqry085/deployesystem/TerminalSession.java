package com.wqry085.deployesystem;

import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TerminalSession {
    private static final String TAG = "TerminalSession";
    
    static {
        System.loadLibrary("terminal-wqry");
    }
    
    private native long nativeStartSession(String shell, String[] args, String[] env);
    private native void nativeWriteInput(long sessionPtr, byte[] data);
    private native void nativeStopSession(long sessionPtr);
    private native boolean nativeIsRunning(long sessionPtr);
    
    public interface OutputCallback {
        void onOutput(byte[] data, int size);
    }
    
    private long sessionPtr;
    private OutputCallback outputCallback;
    
    public boolean startSession(String shell, String[] args, Map<String, String> env) {
        String[] envArray = {
            "TERM=xterm-256color",
            "HOME=/data/local/tmp",
            "USER=shell",
            "PATH=/system/bin:/system/xbin:/vendor/bin:/sbin"
        };
        
        sessionPtr = nativeStartSession(shell, new String[]{"-i"}, envArray);
        return sessionPtr != 0;
    }
    
    public void writeInput(String input) {
        if (isRunning() && input != null) {
            nativeWriteInput(sessionPtr, input.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    public void stopSession() {
        if (sessionPtr != 0) {
            nativeStopSession(sessionPtr);
            sessionPtr = 0;
        }
    }
    
    public void setOutputCallback(OutputCallback callback) {
        this.outputCallback = callback;
    }
    
    public boolean isRunning() {
        return sessionPtr != 0 && nativeIsRunning(sessionPtr);
    }
    
    private void onNativeOutput(byte[] data, int size) {
        if (outputCallback != null) {
            outputCallback.onOutput(data, size);
        }
    }
}