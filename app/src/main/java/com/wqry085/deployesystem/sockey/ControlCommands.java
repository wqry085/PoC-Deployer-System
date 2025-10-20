package com.wqry085.deployesystem.sockey;

public class ControlCommands {
    public static final String CONTROL_KEY = "df2a17ef1e6070522c563bac29933e58";
    
    // 控制命令
    public static final String GET_HISTORY = "GET_HISTORY";
    public static final String EXEC = "EXEC ";  // 注意有空格
    public static final String TERMINATE = "TERMINATE";
    public static final String STATUS = "STATUS";
    public static final String EXIT = "EXIT";
    
    // 服务器响应
    public static final String AUTH_SUCCESS = "AUTH_SUCCESS";
    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String CONTROL_CONNECTED = "CONTROL_CONNECTED";
    public static final String SYSTEM_TERMINATING = "SYSTEM_TERMINATING";
    public static final String UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String CONTROL_EXIT_ACK = "CONTROL_EXIT_ACK";
    public static final String COMMAND_COMPLETED = "COMMAND_COMPLETED";
    public static final String END_OF_HISTORY = "END_OF_HISTORY";
    public static final String END_OF_STATUS = "END_OF_STATUS";
}