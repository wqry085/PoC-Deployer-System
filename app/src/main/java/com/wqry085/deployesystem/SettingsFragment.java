package com.wqry085.deployesystem;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        
        ListPreference themePref = findPreference("app_theme");
        if (themePref != null) {
            
            String currentTheme = ThemeHelper.getTheme(requireContext());
            themePref.setValue(currentTheme);

            
            updateThemeSummary(themePref, currentTheme);

            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String theme = (String) newValue;

                
                Toast.makeText(requireContext(), R.string.theme_changed, Toast.LENGTH_SHORT).show();

                
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    
                    ThemeHelper.setTheme(requireContext(), theme);

                    
                    restartApp();
                }, 500);

                return true;
            });
        }

        
        ListPreference languagePref = findPreference("language");
        if (languagePref != null) {
            
            String currentLanguage = LanguageHelper.getCurrentLanguageCode(requireContext());
            languagePref.setValue(currentLanguage);

            
            updateLanguageSummary(languagePref, currentLanguage);

            languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String languageCode = (String) newValue;

                
                Toast.makeText(requireContext(), R.string.language_changed, Toast.LENGTH_SHORT).show();

                
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    
                    LanguageHelper.setLanguage(requireContext(), languageCode);

                    
                    restartApp();
                }, 500);

                return true;
            });
        }

        
        Preference aboutPref = findPreference("about");
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), AboutActivity.class);
                startActivity(intent);
                return true;
            });
        }

    }

    private void updateThemeSummary(ListPreference themePref, String theme) {
        CharSequence[] entries = themePref.getEntries();
        CharSequence[] entryValues = themePref.getEntryValues();

        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].toString().equals(theme)) {
                themePref.setSummary(entries[i]);
                break;
            }
        }
    }

    private void updateLanguageSummary(ListPreference languagePref, String languageCode) {
        CharSequence[] entries = languagePref.getEntries();
        CharSequence[] entryValues = languagePref.getEntryValues();

        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].toString().equals(languageCode)) {
                languagePref.setSummary(entries[i]);
                break;
            }
        }
    }

    private void restartApp() {
        Intent intent = requireActivity().getPackageManager()
                .getLaunchIntentForPackage(requireActivity().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            requireActivity().finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
