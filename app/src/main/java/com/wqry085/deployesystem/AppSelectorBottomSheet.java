package com.wqry085.deployesystem;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 应用选择器底部弹窗
 */
public class AppSelectorBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private TextInputEditText searchEditText;
    private LinearLayout loadingLayout;
    private TextView emptyText;
    private AppListAdapter adapter;
    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();
    private OnAppSelectedListener listener;

    /**
     * 应用选择监听器
     */
    public interface OnAppSelectedListener {
        void onAppSelected(String packageName, String appName);
    }

    /**
     * 设置应用选择监听器
     */
    public void setOnAppSelectedListener(OnAppSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_app_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化视图
        recyclerView = view.findViewById(R.id.app_list_recycler_view);
        searchEditText = view.findViewById(R.id.search_edit_text);
        loadingLayout = view.findViewById(R.id.loading_layout);
        emptyText = view.findViewById(R.id.empty_text);

        // 设置 RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AppListAdapter();
        recyclerView.setAdapter(adapter);

        // 异步加载应用列表
        loadInstalledAppsAsync();

        // 搜索功能
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * 异步加载已安装的应用
     */
    private void loadInstalledAppsAsync() {
        // 显示加载状态
        showLoading(true);

        // 在后台线程加载应用
        new Thread(() -> {
            try {
                PackageManager pm = requireContext().getPackageManager();
                List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                List<AppInfo> tempApps = new ArrayList<>();
                for (ApplicationInfo appInfo : packages) {
                    String appName = appInfo.loadLabel(pm).toString();
                    String packageName = appInfo.packageName;
                    Drawable icon = appInfo.loadIcon(pm);

                    tempApps.add(new AppInfo(appName, packageName, icon));
                }

                // 按应用名称排序
                Collections.sort(tempApps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));

                // 在主线程更新UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        allApps.clear();
                        allApps.addAll(tempApps);
                        filteredApps.clear();
                        filteredApps.addAll(allApps);
                        adapter.notifyDataSetChanged();
                        showLoading(false);
                        updateEmptyState();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showLoading(false);
                        updateEmptyState();
                    });
                }
            }
        }).start();
    }

    /**
     * 显示/隐藏加载状态
     */
    private void showLoading(boolean loading) {
        if (loading) {
            loadingLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.GONE);
        } else {
            loadingLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新空状态显示
     */
    private void updateEmptyState() {
        if (filteredApps.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 过滤应用列表
     */
    private void filterApps(String query) {
        filteredApps.clear();

        if (query.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppInfo app : allApps) {
                if (app.appName.toLowerCase().contains(lowerQuery) ||
                    app.packageName.toLowerCase().contains(lowerQuery)) {
                    filteredApps.add(app);
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /**
     * 应用信息类
     */
    private static class AppInfo {
        String appName;
        String packageName;
        Drawable icon;

        AppInfo(String appName, String packageName, Drawable icon) {
            this.appName = appName;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    /**
     * RecyclerView 适配器
     */
    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_info, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = filteredApps.get(position);
            holder.appNameTextView.setText(app.appName);
            holder.packageNameTextView.setText(app.packageName);
            holder.appIconImageView.setImageDrawable(app.icon);

            // 点击事件
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppSelected(app.packageName, app.appName);
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return filteredApps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIconImageView;
            TextView appNameTextView;
            TextView packageNameTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                appIconImageView = itemView.findViewById(R.id.app_icon);
                appNameTextView = itemView.findViewById(R.id.app_name);
                packageNameTextView = itemView.findViewById(R.id.app_package);
            }
        }
    }
}
