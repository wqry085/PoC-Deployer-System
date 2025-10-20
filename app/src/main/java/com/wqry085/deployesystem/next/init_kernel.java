package com.wqry085.deployesystem.next;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class init_kernel extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}