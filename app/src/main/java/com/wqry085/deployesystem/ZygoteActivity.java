package com.wqry085.deployesystem;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // ✅ 新增
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wqry085.deployesystem.databinding.ZygoteActivityBinding;
import com.wqry085.deployesystem.sockey.FolderReceiver;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import rikka.shizuku.Shizuku;

public class ZygoteActivity extends AppCompatActivity {

    private ZygoteActivityBinding binding;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    // ✅ 新增：持久化存储键名
    private static final String PREF_NAME = "settings";
    private static final String KEY_CHECK_UPDATE = "check_update_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        setContentView(R.layout.zygote_activity);
        
SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
boolean shouldCheckUpdate = prefs.getBoolean(KEY_CHECK_UPDATE, true);

if (shouldCheckUpdate) {
    checkForUpdates();
}
        Toolbar tox = findViewById(R.id.tox);
        setSupportActionBar(tox);

        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);

        // ✅ 设置适配器
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new ZygoteFragment();
                    case 1:
                        return new PluginFragment();
                    default:
                        return new ZygoteFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        // ✅ 滑动时同步底部导航
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0)
                    bottomNav.setSelectedItemId(R.id.nav_home);
                else if (position == 1)
                    bottomNav.setSelectedItemId(R.id.nav_second);
            }
        });

        // ✅ 点击底部导航时切换页面
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                viewPager.setCurrentItem(0, true);
                return true;
            } else if (item.getItemId() == R.id.nav_second) {
                viewPager.setCurrentItem(1, true);
                return true;
            }
            return false;
        });

        // ✅ 启动文件接收监听
        FolderReceiver receiver = new FolderReceiver(ZygoteActivity.this);
        receiver.startReceiving();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_art, menu);

        // ✅ 从 SharedPreferences 恢复开关状态
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_CHECK_UPDATE, true);

        MenuItem updateItem = menu.findItem(R.id.action_check_update);
        if (updateItem != null) {
            updateItem.setCheckable(true);
            updateItem.setChecked(enabled);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;

        // ✅ 新增：检查更新开关逻辑
        } else if (id == R.id.action_check_update) {
            boolean newState = !item.isChecked();
            item.setChecked(newState);

            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_CHECK_UPDATE, newState);
            editor.apply();
            return true;

        } else if (id == R.id.action_zygote) {
            Context context = this;
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            LayoutInflater inflater = LayoutInflater.from(context);
            View dialogView = inflater.inflate(R.layout.zygote_log, null);

            MaterialSwitch logSwitch = dialogView.findViewById(R.id.log_switch);

            checkPort13568Async(isPortAvailable -> {
                logSwitch.setChecked(isPortAvailable);
            });

            logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {});

            builder.setView(dialogView);
            builder.setPositiveButton("应用", (dialog, which) -> {
                if (logSwitch.isChecked()) {
                    new Thread(() -> {
                        if (Shizuku.getUid() == 0) {
                            ZygoteFragment.ShizukuExec(getApplicationInfo().nativeLibraryDir +
                                    "/libzygote_term.so 0 --package=com.wqry085.deployesystem --Classjava-socket=com.wqry085.deployesystem.next.ZygoteLog > /dev/null 2>&1 &");
                        } else {
                            ZygoteFragment.ShizukuExec(getApplicationInfo().nativeLibraryDir +
                                    "/libzygote_term.so 2000 --package=com.wqry085.deployesystem --Classjava-socket=com.wqry085.deployesystem.next.ZygoteLog > /dev/null 2>&1 &");
                        }
                    }).start();
                } else {
                    new Thread(() -> {
                        ZygoteFragment.ShizukuExec("echo \"STOP\" | netcat 127.0.0.1 13568");
                    }).start();
                }
            });

            builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            return true;

        } else if (id == R.id.action_about) {
            Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, AboutActivity.class);
            startActivity(xh);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public interface PortCheckCallback {
        void onResult(boolean isPortAvailable);
    }

    public static void checkPort13568Async(PortCheckCallback callback) {
        new Thread(() -> {
            boolean result = checkPort13568Sync();
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
        }).start();
    }

    public static boolean checkPort13568Sync() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 13568), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }


void checkForUpdates() {
    new Thread(() -> {
        try {
            String jsonUrl = "https://codeberg.org/wqry085/PoC-Deployer-System/raw/branch/main/appdata.json";

            String jsonText = Jsoup.connect(jsonUrl)
                    .ignoreContentType(true)
                    .timeout(5000)
                    .get()
                    .body()
                    .text();

            JSONObject json = new JSONObject(jsonText);
            int latestCode = json.getInt("version_code");
            String latestVersion = json.getString("latest_version");
            String changelog = json.optString("update_log", "无更新说明");
            String downloadLink = json.optString("update_url", "");
            boolean force = json.optBoolean("force_update", false);

            int currentCode = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionCode;

            File apkFile = new File(getExternalFilesDir(null), "update_" + latestVersion + ".apk");

            if (latestCode > currentCode) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (apkFile.exists()) {
                        showInstallDialog(latestVersion, changelog, apkFile, force, true);
                    } else {
                        showUpdateDialog(latestVersion, changelog, downloadLink, force);
                    }
                });
            } else {
                new Handler(Looper.getMainLooper()).post(() ->
                        showToast("当前已是最新版本"));
            }

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() ->
                    showToast("检查更新失败：" + e.getMessage()));
        }
    }).start();
}

private void showUpdateDialog(String version, String log, String url, boolean force) {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle("发现新版本 v" + version);
    builder.setMessage(log);

    builder.setPositiveButton("立即下载", (dialog, which) -> {
        startDownload(url, version, force);
    });

    if (!force) {
        builder.setNegativeButton("稍后再说", (dialog, which) -> dialog.dismiss());
    } else {
        builder.setCancelable(false);
    }

    builder.show();
}

private void startDownload(String url, String version, boolean force) {
    // MD3 风格进度对话框
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle("正在下载更新 v" + version);
    builder.setCancelable(false);

    LinearProgressIndicator progressIndicator = new LinearProgressIndicator(this);
    progressIndicator.setIndeterminate(false);
    progressIndicator.setProgress(0);
    progressIndicator.setMax(100);
    progressIndicator.setPadding(50, 40, 50, 40);
    builder.setView(progressIndicator);

    AlertDialog progressDialog = builder.show();

    File outputFile = new File(getExternalFilesDir(null), "update_" + version + ".apk");

    new Thread(() -> {
        try {
            URL downloadUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
            conn.connect();

            int total = conn.getContentLength();
            InputStream in = new BufferedInputStream(conn.getInputStream());
            OutputStream out = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            int count;
            long downloaded = 0;
            while ((count = in.read(buffer)) != -1) {
                downloaded += count;
                out.write(buffer, 0, count);
                int progress = (int) ((downloaded * 100) / total);
                new Handler(Looper.getMainLooper()).post(() ->
                        progressIndicator.setProgress(progress, true));
            }

            out.flush();
            out.close();
            in.close();
            conn.disconnect();

            new Handler(Looper.getMainLooper()).post(() -> {
                progressDialog.dismiss();
                showInstallDialog(version, "下载完成！是否立即安装？", outputFile, force, false);
            });

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> {
                progressDialog.dismiss();
                showToast("下载失败：" + e.getMessage());
            });
        }
    }).start();
}

private void showInstallDialog(String version, String log, File apkFile, boolean force, boolean cached) {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle(cached ? "已下载更新 v" + version : "下载完成");
    builder.setMessage(cached ? "检测到已下载的更新包，是否安装？" : log);

    builder.setPositiveButton("安装", (dialog, which) -> {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            showToast("无法启动安装：" + e.getMessage());
        }
    });

    if (!force) {
        builder.setNegativeButton("稍后安装", (dialog, which) -> dialog.dismiss());
    } else {
        builder.setCancelable(false);
    }

    builder.show();
}
}