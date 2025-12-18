package com.wqry085.deployesystem;

import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 图标动画演示 Activity
 * 用于测试和展示 Zygote 图标的动画效果
 */
public class IconAnimationDemo extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建一个 ImageView 来显示动画图标
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.ic_launcher_splash_animated);
        setContentView(imageView);

        // 启动动画
        AnimatedVectorDrawable animatedDrawable =
            (AnimatedVectorDrawable) imageView.getDrawable();
        if (animatedDrawable != null) {
            animatedDrawable.start();
        }
    }
}
