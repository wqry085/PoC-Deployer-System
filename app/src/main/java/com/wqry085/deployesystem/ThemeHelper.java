package com.wqry085.deployesystem;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;


public class ThemeHelper {

    private static final String PREF_THEME = "app_theme";
    private static final String THEME_DEFAULT = "default";
    private static final String THEME_TRANSPARENT = "transparent";

    
    public static void applyTheme(Activity activity) {
        String theme = getTheme(activity);

        if (THEME_TRANSPARENT.equals(theme)) {
            applyTransparentTheme(activity);
        } else {
            applyDefaultTheme(activity);
        }
    }

    
    private static void applyDefaultTheme(Activity activity) {
        activity.setTheme(R.style.AppTheme);

        
    }

    
    private static void applyTransparentTheme(Activity activity) {
        activity.setTheme(R.style.TransparentTheme);

        Window window = activity.getWindow();

        
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            
            int blurRadiusPx = (int) (100 * activity.getResources().getDisplayMetrics().density);
            window.setBackgroundBlurRadius(blurRadiusPx);
        }

        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        
        
    }

    
    
    public static String getTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_THEME, THEME_DEFAULT);
    }

    
    public static void setTheme(Context context, String theme) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREF_THEME, theme).apply();
    }

    
    public static boolean isTransparentTheme(Context context) {
        return THEME_TRANSPARENT.equals(getTheme(context));
    }
}
