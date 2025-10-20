package com.wqry085.deployesystem.next;

import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;

public class CopyAppData {

    private static final String TAG = "CopyAppData";

    public static void main(String[] args) {
        String appData = null;
        String pocProt = null;

        for (String arg : args) {
            if (arg.startsWith("--app-data=")) {
                appData = arg.substring("--app-data=".length());
            } else if (arg.startsWith("--poc-prot=")) {
                pocProt = arg.substring("--poc-prot=".length());
            }
        }

        System.out.println("App Data: " + appData);
        System.out.println("PoC Prot: " + pocProt);

        // null
    }
}
