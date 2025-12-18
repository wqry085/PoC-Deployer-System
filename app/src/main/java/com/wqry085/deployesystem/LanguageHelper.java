package com.wqry085.deployesystem;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import androidx.preference.PreferenceManager;
import java.util.Locale;

public class LanguageHelper {

    private static final String LANGUAGE_KEY = "language";
    private static final String LANGUAGE_SYSTEM = "system";
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String LANGUAGE_CHINESE_SIMPLIFIED = "zh-CN";
    private static final String LANGUAGE_CHINESE_TRADITIONAL = "zh-TW";
    private static final String LANGUAGE_JAPANESE = "ja";
    private static final String LANGUAGE_RUSSIAN = "ru";
    private static final String LANGUAGE_SPANISH = "es";
    private static final String LANGUAGE_FRENCH = "fr";

    /**
     * 应用语言设置（用于 attachBaseContext）
     */
    public static void applyLanguage(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String languageCode = prefs.getString(LANGUAGE_KEY, LANGUAGE_SYSTEM);

        if (!LANGUAGE_SYSTEM.equals(languageCode)) {
            Locale locale = getLocaleFromCode(languageCode);
            setLocale(context, locale);
        }
    }

    /**
     * 创建带有语言配置的 Context（用于 attachBaseContext）
     */
    public static Context attachBaseContext(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String languageCode = prefs.getString(LANGUAGE_KEY, LANGUAGE_SYSTEM);

        if (LANGUAGE_SYSTEM.equals(languageCode)) {
            return context;
        }

        Locale locale = getLocaleFromCode(languageCode);
        return updateContextLocale(context, locale);
    }

    /**
     * 更新 Context 的 Locale
     */
    private static Context updateContextLocale(Context context, Locale locale) {
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            resources.updateConfiguration(config, resources.getDisplayMetrics());
            return context;
        }
    }

    /**
     * 设置语言
     */
    public static void setLanguage(Context context, String languageCode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(LANGUAGE_KEY, languageCode).apply();

        if (LANGUAGE_SYSTEM.equals(languageCode)) {
            applyLanguage(context);
        } else {
            Locale locale = getLocaleFromCode(languageCode);
            setLocale(context, locale);
        }
    }

    /**
     * 获取当前设置的语言代码
     */
    public static String getCurrentLanguageCode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(LANGUAGE_KEY, LANGUAGE_SYSTEM);
    }

    /**
     * 从语言代码获取 Locale
     */
    private static Locale getLocaleFromCode(String languageCode) {
        switch (languageCode) {
            case LANGUAGE_ENGLISH:
                return Locale.ENGLISH;
            case LANGUAGE_CHINESE_SIMPLIFIED:
                return Locale.SIMPLIFIED_CHINESE;
            case LANGUAGE_CHINESE_TRADITIONAL:
                return Locale.TRADITIONAL_CHINESE;
            case LANGUAGE_JAPANESE:
                return Locale.JAPANESE;
            case LANGUAGE_RUSSIAN:
                return new Locale("ru");
            case LANGUAGE_SPANISH:
                return new Locale("es");
            case LANGUAGE_FRENCH:
                return Locale.FRENCH;
            default:
                return Locale.getDefault();
        }
    }

    /**
     * 设置 Locale
     */
    private static void setLocale(Context context, Locale locale) {
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            context.createConfigurationContext(config);
        } else {
            config.locale = locale;
        }

        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /**
     * 重启 Activity 以应用语言更改
     */
    public static void restartActivity(Activity activity) {
        activity.recreate();
    }
}
