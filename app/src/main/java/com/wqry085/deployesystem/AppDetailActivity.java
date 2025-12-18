package com.wqry085.deployesystem;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.HashSet;
import java.util.Set;

public class AppDetailActivity extends AppCompatActivity {

    private ImageView appIconDetail;
    private TextView appNameDetail;
    private TextView appPackageDetail;
    private MaterialSwitch authorizationSwitch;

    public static final String PREFS_NAME = "AuthorizedApps";
    public static final String KEY_AUTHORIZED_PACKAGES = "authorized_packages";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);

        appIconDetail = findViewById(R.id.app_icon_detail);
        appNameDetail = findViewById(R.id.app_name_detail);
        appPackageDetail = findViewById(R.id.app_package_detail);
        authorizationSwitch = findViewById(R.id.switch_authorization);

        String packageName = getIntent().getStringExtra("packageName");
        if (packageName != null) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                appIconDetail.setImageDrawable(appInfo.loadIcon(pm));
                appNameDetail.setText(appInfo.loadLabel(pm));
                appPackageDetail.setText(packageName);

                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                Set<String> authorizedPackages = prefs.getStringSet(KEY_AUTHORIZED_PACKAGES, new HashSet<>());
                authorizationSwitch.setChecked(authorizedPackages.contains(packageName));

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.app_not_found_toast), Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        authorizationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> authorizedPackages = new HashSet<>(prefs.getStringSet(KEY_AUTHORIZED_PACKAGES, new HashSet<>()));

            if (isChecked) {
                authorizedPackages.add(packageName);
            } else {
                authorizedPackages.remove(packageName);
            }

            prefs.edit().putStringSet(KEY_AUTHORIZED_PACKAGES, authorizedPackages).apply();
            
            String status = isChecked ? getString(R.string.app_authorized) : getString(R.string.app_unauthorized);
            Toast.makeText(this, String.format(getString(R.string.app_status_toast), status), Toast.LENGTH_SHORT).show();

            updateWhitelist();
        });
    }

    private void updateWhitelist() {
        // This method will be responsible for calling the logic in ZygoteActivity
        // to update the native whitelist file using Shizuku.
        ZygoteActivity.updateWhitelist(this);
    }
}
