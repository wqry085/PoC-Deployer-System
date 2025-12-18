package com.wqry085.deployesystem;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthorizationListFragment extends Fragment {

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private List<AppInfo> fullAppList = new ArrayList<>();
    private SearchView searchView;
    private MaterialSwitch showSystemAppsSwitch;
    private MaterialSwitch enableWhitelistSwitch;
    private ProgressBar progressBar;
    private TextView daemonStatusText;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_authorization_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        searchView = view.findViewById(R.id.search_view_apps);
        showSystemAppsSwitch = view.findViewById(R.id.switch_show_system_apps);
        enableWhitelistSwitch = view.findViewById(R.id.switch_enable_whitelist);
        progressBar = view.findViewById(R.id.progress_bar);
        daemonStatusText = view.findViewById(R.id.daemon_status_text);

        adapter = new AppListAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // 检查守护进程状态
        checkDaemonStatus();

        // 搜索监听
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });

        // 显示系统应用开关
        showSystemAppsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            loadApps();
        });

        // 启用/禁用白名单开关
        enableWhitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setWhitelistEnabled(isChecked);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkDaemonStatus();
    }

    /**
     * 检查策略守护进程状态
     */
    private void checkDaemonStatus() {
        if (daemonStatusText != null) {
            daemonStatusText.setText("检查守护进程状态...");
            daemonStatusText.setVisibility(View.VISIBLE);
        }

        PolicyClient.isAliveAsync(alive -> {
            if (!isAdded()) return;

            if (alive) {
                if (daemonStatusText != null) {
                    daemonStatusText.setVisibility(View.GONE);
                }
                // 守护进程运行中，加载数据
                loadWhitelistState();
                loadApps();
            } else {
                if (daemonStatusText != null) {
                    daemonStatusText.setText("⚠️ 策略守护进程未运行");
                    daemonStatusText.setVisibility(View.VISIBLE);
                }
                enableWhitelistSwitch.setEnabled(false);
                progressBar.setVisibility(View.GONE);
                
                Toast.makeText(getContext(), 
                    "策略守护进程未运行，请先启动服务", 
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 加载白名单启用状态
     */
    private void loadWhitelistState() {
        PolicyClient.isEnabledAsync(enabled -> {
            if (!isAdded()) return;
            
            enableWhitelistSwitch.setOnCheckedChangeListener(null);
            enableWhitelistSwitch.setChecked(enabled);
            enableWhitelistSwitch.setEnabled(true);
            enableWhitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setWhitelistEnabled(isChecked);
            });
        });
    }

    /**
     * 设置白名单启用状态
     */
    private void setWhitelistEnabled(boolean enabled) {
        enableWhitelistSwitch.setEnabled(false);

        PolicyClient.BooleanCallback callback = success -> {
            if (!isAdded()) return;
            
            enableWhitelistSwitch.setEnabled(true);
            
            if (success) {
                Toast.makeText(getContext(),
                    enabled ? "白名单已启用" : "白名单已禁用",
                    Toast.LENGTH_SHORT).show();
            } else {
                // 恢复开关状态
                enableWhitelistSwitch.setOnCheckedChangeListener(null);
                enableWhitelistSwitch.setChecked(!enabled);
                enableWhitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    setWhitelistEnabled(isChecked);
                });
                
                Toast.makeText(getContext(), "操作失败", Toast.LENGTH_SHORT).show();
            }
        };

        if (enabled) {
            PolicyClient.enableAsync(callback);
        } else {
            PolicyClient.disableAsync(callback);
        }
    }

    /**
     * 加载应用列表
     */
    private void loadApps() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        executor.execute(() -> {
            // 获取白名单
            Set<Integer> whitelistedUids = PolicyClient.getWhitelistedUids();
            
            // 获取应用列表
            List<AppInfo> newAppList = new ArrayList<>();
            
            if (getContext() == null) return;
            
            PackageManager pm = getContext().getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);

            boolean showSystem = showSystemAppsSwitch.isChecked();

            for (PackageInfo packageInfo : packages) {
                boolean isSystemApp = (packageInfo.applicationInfo.flags 
                    & ApplicationInfo.FLAG_SYSTEM) != 0;
                
                if (!showSystem && isSystemApp) {
                    continue;
                }

                AppInfo appInfo = new AppInfo();
                appInfo.setName(packageInfo.applicationInfo.loadLabel(pm).toString());
                appInfo.setPackageName(packageInfo.packageName);
                appInfo.setIcon(packageInfo.applicationInfo.loadIcon(pm));
                appInfo.setUid(packageInfo.applicationInfo.uid);
                appInfo.setAuthorized(whitelistedUids.contains(appInfo.getUid()));
                appInfo.setSystemApp(isSystemApp);
                newAppList.add(appInfo);
            }

            // 按名称排序
            newAppList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            handler.post(() -> {
                if (!isAdded()) return;
                
                fullAppList = newAppList;
                filter(searchView.getQuery().toString());
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            });
        });
    }

    /**
     * 过滤应用列表
     */
    private void filter(String text) {
        List<AppInfo> filteredList = new ArrayList<>();
        String lowerText = text.toLowerCase();
        
        for (AppInfo item : fullAppList) {
            if (item.getName().toLowerCase().contains(lowerText) ||
                item.getPackageName().toLowerCase().contains(lowerText)) {
                filteredList.add(item);
            }
        }
        adapter.updateList(filteredList);
    }

    /**
     * 应用信息类
     */
    public static class AppInfo {
        private String name;
        private String packageName;
        private Drawable icon;
        private int uid;
        private boolean isAuthorized;
        private boolean isSystemApp;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public Drawable getIcon() { return icon; }
        public void setIcon(Drawable icon) { this.icon = icon; }
        public int getUid() { return uid; }
        public void setUid(int uid) { this.uid = uid; }
        public boolean isAuthorized() { return isAuthorized; }
        public void setAuthorized(boolean authorized) { isAuthorized = authorized; }
        public boolean isSystemApp() { return isSystemApp; }
        public void setSystemApp(boolean systemApp) { isSystemApp = systemApp; }
    }

    /**
     * 应用列表适配器
     */
    public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private List<AppInfo> appList;

        public AppListAdapter(List<AppInfo> appList) {
            this.appList = appList;
        }

        public void updateList(List<AppInfo> newList) {
            this.appList = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = appList.get(position);
            
            holder.appName.setText(app.getName());
            holder.appPackage.setText(String.format("%s (UID: %d)", 
                app.getPackageName(), app.getUid()));
            holder.appIcon.setImageDrawable(app.getIcon());

            // 防止回调触发
            holder.authorizeSwitch.setOnCheckedChangeListener(null);
            holder.authorizeSwitch.setChecked(app.isAuthorized());

            holder.authorizeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                holder.authorizeSwitch.setEnabled(false);
                
                PolicyClient.BooleanCallback callback = success -> {
                    holder.authorizeSwitch.setEnabled(true);
                    
                    if (success) {
                        app.setAuthorized(isChecked);
                        // 保存更改
                        PolicyClient.saveAsync(saved -> {});
                    } else {
                        // 恢复状态
                        holder.authorizeSwitch.setOnCheckedChangeListener(null);
                        holder.authorizeSwitch.setChecked(!isChecked);
                        holder.authorizeSwitch.setOnCheckedChangeListener(
                            (btn, checked) -> onBindViewHolder(holder, position));
                        
                        Toast.makeText(getContext(), "操作失败", Toast.LENGTH_SHORT).show();
                    }
                };

                if (isChecked) {
                    PolicyClient.addUidAsync(app.getUid(), callback);
                } else {
                    PolicyClient.removeUidAsync(app.getUid(), callback);
                }
            });
        }

        @Override
        public int getItemCount() {
            return appList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ImageView appIcon;
            public TextView appName;
            public TextView appPackage;
            public MaterialSwitch authorizeSwitch;

            public ViewHolder(View view) {
                super(view);
                appIcon = view.findViewById(R.id.app_icon);
                appName = view.findViewById(R.id.app_name);
                appPackage = view.findViewById(R.id.app_package);
                authorizeSwitch = view.findViewById(R.id.switch_authorize);
            }
        }
    }
}