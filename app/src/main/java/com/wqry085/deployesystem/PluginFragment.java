package com.wqry085.deployesystem;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PluginFragment extends Fragment {

    private RecyclerView recyclerView;
    private PluginAdapter adapter;
    private final List<PluginItem> pluginList = new ArrayList<>();

    private LinearLayout loadingLayout, errorLayout;
    private Button retryButton;
    private SwipeRefreshLayout swipeRefresh;

    private static final String PLUGIN_URL = "https://codeberg.org/wqry085/PoC-Deployer-System/raw/branch/main/poc_plugin.json";

    public PluginFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_plugin, container, false);

        recyclerView = view.findViewById(R.id.pluginRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PluginAdapter(pluginList, getContext());
        recyclerView.setAdapter(adapter);

        loadingLayout = view.findViewById(R.id.loadingLayout);
        errorLayout = view.findViewById(R.id.errorLayout);
        retryButton = view.findViewById(R.id.retryButton);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        swipeRefresh.setOnRefreshListener(this::loadPlugins);
        retryButton.setOnClickListener(v -> loadPlugins());

        loadPlugins();
        return view;
    }

    private void loadPlugins() {
        loadingLayout.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        new LoadPluginsTask().execute(PLUGIN_URL);
    }

    private class LoadPluginsTask extends AsyncTask<String, Void, List<PluginItem>> {

        private boolean error = false;

        @Override
        protected List<PluginItem> doInBackground(String... urls) {
            List<PluginItem> list = new ArrayList<>();
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray plugins = root.getJSONArray("plugins");
                for (int i = 0; i < plugins.length(); i++) {
                    JSONObject obj = plugins.getJSONObject(i);
                    list.add(new PluginItem(
                            obj.getString("name"),
                            obj.getString("version"),
                            obj.getString("description"),
                            obj.getString("download")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = true;
            }
            return list;
        }

        @Override
        protected void onPostExecute(List<PluginItem> result) {
            swipeRefresh.setRefreshing(false);
            loadingLayout.setVisibility(View.GONE);

            if (error || result.isEmpty()) {
                errorLayout.setVisibility(View.VISIBLE);
            } else {
                pluginList.clear();
                pluginList.addAll(result);
                adapter.notifyDataSetChanged();
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }
}