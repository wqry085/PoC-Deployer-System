package com.wqry085.deployesystem;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.IOException;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.io.InputStream;

public class hellodream extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT));
        ImageView imageView = new ImageView(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("hello.png");
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        rootLayout.addView(imageView);
        setContentView(rootLayout);
        zygote();
    }
    

void zygote(){
    MediaPlayer mediaPlayer = null;
    
    try {
        mediaPlayer = new MediaPlayer();
        android.content.res.AssetFileDescriptor afd = getAssets().openFd("zygote/dream.mp3");
        mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        mediaPlayer.prepare();
        mediaPlayer.setLooping(true);
        mediaPlayer.start();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
            }
        });
        
    } catch (IOException e) {
        e.printStackTrace();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    } catch (Exception e) {
        e.printStackTrace();
        
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}
}