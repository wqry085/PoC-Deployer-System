package com.wqry085.deployesystem.next;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import com.google.android.material.color.DynamicColors;
import com.wqry085.deployesystem.LanguageHelper;

public class init_kernel extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageHelper.attachBaseContext(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LanguageHelper.applyLanguage(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}