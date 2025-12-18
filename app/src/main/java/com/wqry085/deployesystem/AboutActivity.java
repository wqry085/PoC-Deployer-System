package com.wqry085.deployesystem;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageHelper.attachBaseContext(newBase));
    }

    // Apply theme before super.onCreate
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onCreateHeader(@NonNull ImageView icon,
                                  @NonNull TextView slogan,
                                  @NonNull TextView version) {
        // 应用图标 - 使用动画图标
        icon.setImageResource(R.drawable.ic_launcher_splash_animated);

        // 点击图标播放动画
        icon.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Drawable drawable = icon.getDrawable();
                if (drawable instanceof AnimatedVectorDrawable) {
                    AnimatedVectorDrawable animatedDrawable = (AnimatedVectorDrawable) drawable;
                    animatedDrawable.reset();
                    animatedDrawable.start();
                }
            }
        });

        // 自动播放一次动画
        icon.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Drawable drawable = icon.getDrawable();
                if (drawable instanceof AnimatedVectorDrawable) {
                    AnimatedVectorDrawable animatedDrawable = (AnimatedVectorDrawable) drawable;
                    animatedDrawable.start();
                }
            }
        });

        // 版本号
        version.setText("1.5.7");
    }

    @Override
    protected void onItemsCreated(@NonNull List<Object> items) {
        // 关于应用
        items.add(new Category(getString(R.string.about_category)));
        items.add(new Card(getString(R.string.cve_title)));
        items.add(new Card(getString(R.string.beta_desc)));

        // 开发者信息
        items.add(new Category(getString(R.string.dev_category)));
        items.add(new Contributor(
                R.drawable.ic_wa,
                "wqry085",
                getString(R.string.dev_home),
                "http://www.coolapk.com/u/21820733"
        ));

        // 项目信息
        items.add(new Category(getString(R.string.project_category)));
        items.add(new License(
                "PoC-Deployer-System",
                "wqry085",
                License.MIT,
                "https://github.com/wqry085/PoC-Deployer-System"
        ));
    }
}