package com.wqry085.deployesystem;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import rikka.shizuku.Shizuku;

public class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.ViewHolder> {

    private final List<PluginItem> pluginList;
    private final Context context;

    public PluginAdapter(List<PluginItem> pluginList, Context context) {
        this.pluginList = pluginList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_plugin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PluginItem plugin = pluginList.get(position);
        holder.name.setText(plugin.name + "  v" + plugin.version);
        holder.description.setText(plugin.description);

        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);

        File file = new File(Environment.getExternalStoragePublicDirectory("Download/POC插件"),
                plugin.name + "_" + plugin.version + ".plugin");

        if (file.exists()) {
            holder.downloadButton.setVisibility(View.GONE);
            holder.executeButton.setVisibility(View.VISIBLE);
        } else {
            holder.downloadButton.setVisibility(View.VISIBLE);
            holder.executeButton.setVisibility(View.GONE);
        }

        holder.downloadButton.setOnClickListener(v -> downloadPluginWithDialog(plugin, position));

        holder.executeButton.setOnClickListener(v -> {
    if (!file.exists()) {
        Toast.makeText(context, "插件文件不存在", Toast.LENGTH_SHORT).show();
        notifyItemChanged(position);
        return;
    }

    // 弹窗选择执行方式
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle("选择操作方式");
    builder.setMessage("你希望如何处理该插件？");
    builder.setPositiveButton("直接执行", (dialog, which) -> {
        Toast.makeText(context, "执行插件中", Toast.LENGTH_SHORT).show();
        ShizukuExecWithDialog(context, "/system/bin/sh \"" + file.getPath() + "\"");
    });
    builder.setNeutralButton("分享到其他应用", (dialog, which) -> {
        try {
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("*/*");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM,
                    androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".provider",
                            file
                    ));
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(android.content.Intent.createChooser(shareIntent, "分享到"));
        } catch (Exception e) {
            Toast.makeText(context, "分享失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    });
    builder.setNegativeButton("取消", null);
    builder.show();
});
    }

    private void downloadPluginWithDialog(PluginItem plugin, int position) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("下载中").setCancelable(false);

            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            builder.setView(progressBar);

            AlertDialog dialog = builder.create();
            dialog.show();

            // 开启线程下载
            new Thread(() -> downloadPlugin(plugin, position, dialog, progressBar)).start();
        });
    }

    private void downloadPlugin(PluginItem plugin, int position, AlertDialog dialog, ProgressBar progressBar) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory("Download"), "POC插件");
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, plugin.name + "_" + plugin.version + ".plugin");

            URL url = new URL(plugin.downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int total = conn.getContentLength();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(outFile);

            byte[] buffer = new byte[4096];
            int len;
            int downloaded = 0;

            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
                downloaded += len;
                if (total > 0) {
                    int progress = (int) ((downloaded * 100L) / total);
                    mainHandler.post(() -> progressBar.setProgress(progress));
                }
            }

            fos.flush();
            fos.close();
            is.close();

            // 下载完成
            mainHandler.post(() -> {
                dialog.dismiss();
                AlertDialog finishedDialog = new AlertDialog.Builder(context)
                        .setTitle("下载完成")
                        .setMessage(plugin.name + " 下载完成！")
                        .setPositiveButton("确定", null)
                        .create();
                finishedDialog.show();

                notifyItemChanged(position); // 刷新列表显示执行按钮
            });

        } catch (Exception e) {
            e.printStackTrace();
            mainHandler.post(() -> {
                dialog.dismiss();
                Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
            });
        }
    }
public static void ShizukuExecWithDialog(Context context, String cmd) {
    // 创建一个弹窗布局
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(30, 30, 30, 30);
    layout.setBackgroundColor(Color.BLACK);

    ScrollView scrollView = new ScrollView(context);
    TextView outputText = new TextView(context);
    outputText.setTextColor(Color.GREEN);
    outputText.setTextSize(14);
    scrollView.addView(outputText);
    layout.addView(scrollView);

    AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("执行插件中")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("关闭", (d, which) -> d.dismiss())
            .create();
    dialog.show();

    // 异步执行命令
    new Thread(() -> {
        try {
            Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
            OutputStream out = p.getOutputStream();
            out.write((cmd + "\nexit\n").getBytes());
            out.flush();
            out.close();

            BufferedReader mReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String inline;

            Handler handler = new Handler(Looper.getMainLooper());

            while ((inline = mReader.readLine()) != null) {
                final String line = inline;
                handler.post(() -> outputText.append(line + "\n"));
            }

            while ((inline = errorReader.readLine()) != null) {
                final String line = inline;
                handler.post(() -> {
                    Spannable spannable = new SpannableString(line + "\n");
                    spannable.setSpan(new ForegroundColorSpan(Color.RED), 0, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    outputText.append(spannable);
                });
            }

            int exitCode = p.waitFor();
            handler.post(() -> outputText.append("\n命令结束, exitCode: " + exitCode));

        } catch (Exception e) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                Spannable spannable = new SpannableString(e.toString());
                spannable.setSpan(new ForegroundColorSpan(Color.RED), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                outputText.append(spannable);
            });
        }
    }).start();
}
    @Override
    public int getItemCount() {
        return pluginList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, description;
        Button downloadButton, executeButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.pluginName);
            description = itemView.findViewById(R.id.pluginDescription);
            downloadButton = itemView.findViewById(R.id.pluginDownload);
            executeButton = itemView.findViewById(R.id.pluginExecute);
        }
    }
}