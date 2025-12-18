package com.wqry085.deployesystem;

public class ZygoteArguments {
    public static String CALCULATE="8\n";
  /*  public static String ACTIVITYTHREAD="android.app.ActivityThread";
    public static String ACTIVITYTHREAD1213="android.app.ActivityThread,";*/
    public static String SET_UID="--setuid=\n";
    public static String SET_GID="--setgid=\n";
    // public static String SET_GROUPS="--setgroups="; 写的多余的一个poc会自动补全
    public static String ARGS="--runtime-args\n";
    public static String SEINFN="--seinfo=\n";
    public static String RUNTIME_FLAGS="--runtime-flags=";
    public static String PROC_NAME="--nice-name=";
    public static String EXEC_WITH="--invoke-with\n";
}
