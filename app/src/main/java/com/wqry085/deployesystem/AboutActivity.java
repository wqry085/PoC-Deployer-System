package com.wqry085.deployesystem;

import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.drakeet.about.AbsAboutActivity;
import com.drakeet.about.Card;
import com.drakeet.about.Category;
import com.drakeet.about.Contributor;
import com.drakeet.about.License;

import java.util.List;

public class AboutActivity extends AbsAboutActivity {

    @Override
    protected void onCreateHeader(@NonNull ImageView icon,
                                  @NonNull TextView slogan,
                                  @NonNull TextView version) {
        // 应用图标
        icon.setImageResource(R.mipmap.ic_launcher);

        // 版本号
        version.setText("v1.4-alpha02");
    }

    @Override
    protected void onItemsCreated(@NonNull List<Object> items) {
        // 关于应用
        items.add(new Category("关于应用"));
        items.add(new Card("漏洞 CVE-2024-31317 使用工具"));
        items.add(new Card("此版本为测试版，目的是收集报告。\n" +
                "请将你遇到的问题反馈至：wqry085super@gmail.com"));

        // 开发者信息
        items.add(new Category("开发者"));
        items.add(new Contributor(
                R.drawable.ic_wa,
                "wqry085",
                "开发者酷安主页",
                "http://www.coolapk.com/u/21820733"
        ));

        // 项目信息
        items.add(new Category("项目"));
        items.add(new License(
                "PoC-Deployer-System",
                "wqry085",
                License.MIT,
                "https://codeberg.org/wqry085/PoC-Deployer-System"
        ));
    }
}