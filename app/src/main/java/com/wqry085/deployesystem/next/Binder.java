package com.wqry085.deployesystem.next;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Binder {
    private static final String TAG = "BinderCLI";
    private static final String VERSION = "2.0.0";
    
    private static Map<String, String> serviceStubMap = new HashMap<>();
    
    private static Map<String, Class<?>> typeAliases = new HashMap<>();
    
    private static Map<String, Object> constantsMap = new HashMap<>();
    
    static {
        serviceStubMap.put("activity", "android.app.IActivityManager$Stub");
        serviceStubMap.put("package", "android.content.pm.IPackageManager$Stub");
        serviceStubMap.put("window", "android.view.IWindowManager$Stub");
        serviceStubMap.put("power", "android.os.IPowerManager$Stub");
        serviceStubMap.put("wifi", "android.net.wifi.IWifiManager$Stub");
        serviceStubMap.put("audio", "android.media.IAudioService$Stub");
        serviceStubMap.put("telephony", "com.android.internal.telephony.ITelephony$Stub");
        serviceStubMap.put("notification", "android.app.INotificationManager$Stub");
        serviceStubMap.put("alarm", "android.app.IAlarmManager$Stub");
        serviceStubMap.put("vibrator", "android.os.IVibratorService$Stub");
        serviceStubMap.put("clipboard", "android.content.IClipboard$Stub");
        serviceStubMap.put("input", "android.hardware.input.IInputManager$Stub");
        serviceStubMap.put("display", "android.hardware.display.IDisplayManager$Stub");
        serviceStubMap.put("statusbar", "com.android.internal.statusbar.IStatusBarService$Stub");
        serviceStubMap.put("usb", "android.hardware.usb.IUsbManager$Stub");
        serviceStubMap.put("bluetooth", "android.bluetooth.IBluetoothManager$Stub");
        serviceStubMap.put("location", "android.location.ILocationManager$Stub");
        serviceStubMap.put("sensor", "android.hardware.ISensorServer$Stub");
        serviceStubMap.put("camera", "android.hardware.ICameraService$Stub");
        serviceStubMap.put("media_session", "android.media.session.ISessionManager$Stub");
        serviceStubMap.put("accessibility", "android.view.accessibility.IAccessibilityManager$Stub");
        serviceStubMap.put("device_policy", "android.app.admin.IDevicePolicyManager$Stub");
        serviceStubMap.put("user", "android.os.IUserManager$Stub");
        serviceStubMap.put("appops", "com.android.internal.app.IAppOpsService$Stub");
        serviceStubMap.put("job_scheduler", "android.app.job.IJobScheduler$Stub");
        typeAliases.put("int", int.class);
        typeAliases.put("integer", Integer.class);
        typeAliases.put("long", long.class);
        typeAliases.put("float", float.class);
        typeAliases.put("double", double.class);
        typeAliases.put("boolean", boolean.class);
        typeAliases.put("bool", boolean.class);
        typeAliases.put("string", String.class);
        typeAliases.put("str", String.class);
        typeAliases.put("byte", byte.class);
        typeAliases.put("short", short.class);
        typeAliases.put("char", char.class);
        typeAliases.put("void", void.class);
        constantsMap.put("null", null);
        constantsMap.put("true", true);
        constantsMap.put("false", false);
        constantsMap.put("FLAG_ACTIVITY_NEW_TASK", 0x10000000);
        constantsMap.put("FLAG_ACTIVITY_CLEAR_TOP", 0x04000000);
        constantsMap.put("FLAG_ACTIVITY_SINGLE_TOP", 0x20000000);
        constantsMap.put("IMPORTANCE_FOREGROUND", 100);
        constantsMap.put("IMPORTANCE_VISIBLE", 200);
        constantsMap.put("IMPORTANCE_SERVICE", 300);
        constantsMap.put("IMPORTANCE_BACKGROUND", 400);
        constantsMap.put("USER_CURRENT", -2);
        constantsMap.put("USER_ALL", -1);
        constantsMap.put("USER_SYSTEM", 0);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        try {
            String command = args[0];
            switch (command) {
                case "list":
                case "ls":
                    handleListCommand(getExtraArgs(args, 1));
                    break;
                case "call":
                case "c":
                    handleCallCommand(getExtraArgs(args, 1));
                    break;
                case "methods":
                case "m":
                    handleMethodsCommand(getExtraArgs(args, 1));
                    break;
                case "info":
                case "i":
                    handleInfoCommand(getExtraArgs(args, 1));
                    break;
                case "ping":
                case "p":
                    handlePingCommand(getExtraArgs(args, 1));
                    break;
                case "dump":
                case "d":
                    handleDumpCommand(getExtraArgs(args, 1));
                    break;
                case "stats":
                    printBinderStats();
                    break;
                case "monitor":
                    handleMonitorCommand(getExtraArgs(args, 1));
                    break;
                case "transaction":
                case "transact":
                    handleTransactionCommand(getExtraArgs(args, 1));
                    break;
                case "search":
                case "find":
                    handleSearchCommand(getExtraArgs(args, 1));
                    break;
                case "interface":
                case "iface":
                    handleInterfaceCommand(getExtraArgs(args, 1));
                    break;
                case "constants":
                case "const":
                    handleConstantsCommand(getExtraArgs(args, 1));
                    break;
                case "batch":
                    handleBatchCommand(getExtraArgs(args, 1));
                    break;
                case "shell":
                    startInteractiveShell();
                    break;
                case "export":
                    handleExportCommand(getExtraArgs(args, 1));
                    break;
                case "--wqry085":
                    handleEasterEgg();
                    break;
                case "version":
                case "-v":
                case "--version":
                    System.out.println("Binder CLI v" + VERSION);
                    break;
                case "help":
                case "-h":
                case "--help":
                    if (args.length > 1) {
                        printCommandHelp(args[1]);
                    } else {
                        printUsage();
                    }
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    System.err.println("Use 'binder help' for usage information.");
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (isDebugMode(args)) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    // ==================== 命令处理 ====================
    
    private static void handleListCommand(String[] args) throws Exception {
        boolean showAll = false;
        boolean showDead = false;
        boolean compact = false;
        String filter = null;
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-a":
                case "--all":
                    showAll = true;
                    break;
                case "-d":
                case "--dead":
                    showDead = true;
                    break;
                case "-c":
                case "--compact":
                    compact = true;
                    break;
                case "-f":
                case "--filter":
                    if (i + 1 < args.length) {
                        filter = args[++i];
                    }
                    break;
            }
        }
        
        listServices(showAll, showDead, compact, filter);
    }
    
    private static void handleCallCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: binder call <service> <method> [options] [args...]");
            System.err.println("Options:");
            System.err.println("  -t, --types <types>    Specify parameter types (comma-separated)");
            System.err.println("  -u, --user <userId>    Specify user ID");
            System.err.println("  -j, --json             Output result as JSON");
            System.err.println("  -r, --raw              Output raw result");
            return;
        }
        
        String serviceName = args[0];
        String methodName = args[1];
        String[] types = null;
        int userId = -1;
        boolean jsonOutput = false;
        boolean rawOutput = false;
        List<String> methodArgs = new ArrayList<>();
        
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "-t":
                case "--types":
                    if (i + 1 < args.length) {
                        types = args[++i].split(",");
                    }
                    break;
                case "-u":
                case "--user":
                    if (i + 1 < args.length) {
                        userId = parseIntArg(args[++i]);
                    }
                    break;
                case "-j":
                case "--json":
                    jsonOutput = true;
                    break;
                case "-r":
                case "--raw":
                    rawOutput = true;
                    break;
                default:
                    methodArgs.add(args[i]);
            }
        }
        
        callService(serviceName, methodName, methodArgs.toArray(new String[0]), 
                   types, userId, jsonOutput, rawOutput);
    }
    
    private static void handleMethodsCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder methods <service> [options]");
            System.err.println("Options:");
            System.err.println("  -f, --filter <pattern>  Filter methods by name pattern");
            System.err.println("  -p, --params            Show parameter names");
            System.err.println("  -r, --return            Show return types");
            System.err.println("  -s, --signature         Show full method signatures");
            return;
        }
        
        String serviceName = args[0];
        String filter = null;
        boolean showParams = false;
        boolean showReturn = false;
        boolean showSignature = false;
        
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-f":
                case "--filter":
                    if (i + 1 < args.length) {
                        filter = args[++i];
                    }
                    break;
                case "-p":
                case "--params":
                    showParams = true;
                    break;
                case "-r":
                case "--return":
                    showReturn = true;
                    break;
                case "-s":
                case "--signature":
                    showSignature = true;
                    break;
            }
        }
        
        listServiceMethods(serviceName, filter, showParams, showReturn, showSignature);
    }
    
    private static void handleInfoCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder info <service> [options]");
            System.err.println("Options:");
            System.err.println("  -v, --verbose    Show verbose information");
            System.err.println("  -j, --json       Output as JSON");
            return;
        }
        
        boolean verbose = hasFlag(args, "-v", "--verbose");
        boolean json = hasFlag(args, "-j", "--json");
        
        getServiceInfo(args[0], verbose, json);
    }
    
    private static void handlePingCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder ping <service> [options]");
            System.err.println("Options:");
            System.err.println("  -c, --count <n>      Ping n times");
            System.err.println("  -i, --interval <ms>  Interval between pings");
            return;
        }
        
        String serviceName = args[0];
        int count = getIntFlag(args, "-c", "--count", 1);
        int interval = getIntFlag(args, "-i", "--interval", 1000);
        
        pingService(serviceName, count, interval);
    }
    
    private static void handleDumpCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder dump <service> [dump-args...]");
            return;
        }
        dumpService(args[0], getExtraArgs(args, 1));
    }
    
    private static void handleMonitorCommand(String[] args) throws Exception {
        System.out.println("Monitoring Binder services...");
        System.out.println("Press Ctrl+C to stop");
        System.out.println("==========================================");
        
        Set<String> previousServices = new HashSet<>();
        
        while (true) {
            try {
                Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
                Method listServicesMethod = serviceManagerClass.getDeclaredMethod("listServices");
                String[] services = (String[]) listServicesMethod.invoke(null);
                Set<String> currentServices = new HashSet<>(Arrays.asList(services));
                
                // 检查新增服务
                for (String service : currentServices) {
                    if (!previousServices.isEmpty() && !previousServices.contains(service)) {
                        System.out.println("[+] New service: " + service);
                    }
                }
                
                // 检查移除服务
                for (String service : previousServices) {
                    if (!currentServices.contains(service)) {
                        System.out.println("[-] Removed service: " + service);
                    }
                }
                
                previousServices = currentServices;
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    private static void handleTransactionCommand(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: binder transact <service> <code> <data>");
            System.err.println("  code: Transaction code (integer)");
            System.err.println("  data: Hex-encoded data or 'empty' for empty parcel");
            return;
        }
        
        String serviceName = args[0];
        int code = parseIntArg(args[1]);
        String data = args[2];
        
        sendRawTransaction(serviceName, code, data);
    }
    
    private static void handleSearchCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder search <pattern> [options]");
            System.err.println("Options:");
            System.err.println("  -m, --methods    Search in method names");
            System.err.println("  -i, --interface  Search in interface names");
            return;
        }
        
        String pattern = args[0];
        boolean searchMethods = hasFlag(args, "-m", "--methods");
        boolean searchInterface = hasFlag(args, "-i", "--interface");
        
        if (!searchMethods && !searchInterface) {
            searchMethods = true;
            searchInterface = true;
        }
        
        searchServices(pattern, searchMethods, searchInterface);
    }
    
    private static void handleInterfaceCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder interface <interface-name>");
            System.err.println("Example: binder interface android.app.IActivityManager");
            return;
        }
        
        String interfaceName = args[0];
        inspectInterface(interfaceName);
    }
    
    private static void handleConstantsCommand(String[] args) throws Exception {
        if (args.length < 1) {
            // 显示所有已知常量
            System.out.println("Known Constants:");
            System.out.println("==========================================");
            for (Map.Entry<String, Object> entry : constantsMap.entrySet()) {
                System.out.printf("  %-30s = %s%n", entry.getKey(), entry.getValue());
            }
            return;
        }
        
        // 从类中提取常量
        String className = args[0];
        extractConstants(className);
    }
    
    private static void handleBatchCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder batch <command-file>");
            System.err.println("  Execute multiple commands from a file");
            return;
        }
        
        System.err.println("Batch execution from file: " + args[0]);
        System.err.println("(File reading not implemented in this example)");
    }
    
    private static void handleExportCommand(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: binder export <service> [options]");
            System.err.println("Options:");
            System.err.println("  -f, --format <format>  Output format (java|kotlin|json)");
            System.err.println("  -o, --output <file>    Output file");
            return;
        }
        
        String serviceName = args[0];
        String format = getStringFlag(args, "-f", "--format", "java");
        
        exportServiceInterface(serviceName, format);
    }
    
    private static void handleEasterEgg() {
        System.out.println("ok >>>>");
        try {
            Runtime.getRuntime().exec("/system/bin/am start -a android.intent.action.VIEW -d \"https://b23.tv/c1tB6G9\"");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== 核心功能实现 ====================
    
    private static void listServices(boolean showAll, boolean showDead, 
                                    boolean compact, String filter) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method listServicesMethod = serviceManagerClass.getDeclaredMethod("listServices");
        String[] services = (String[]) listServicesMethod.invoke(null);
        
        Arrays.sort(services);
        
        Pattern filterPattern = filter != null ? 
            Pattern.compile(filter, Pattern.CASE_INSENSITIVE) : null;
        
        int aliveCount = 0;
        int deadCount = 0;
        int shownCount = 0;
        
        if (!compact) {
            System.out.println("Available Binder Services:");
            System.out.println("==========================================");
        }
        
        for (String service : services) {
            if (filterPattern != null && !filterPattern.matcher(service).find()) {
                continue;
            }
            
            try {
                Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
                Object binder = getServiceMethod.invoke(null, service);
                
                if (binder != null) {
                    Method pingBinderMethod = binder.getClass().getMethod("pingBinder");
                    boolean isAlive = (Boolean) pingBinderMethod.invoke(binder);
                    
                    if (isAlive) {
                        aliveCount++;
                    } else {
                        deadCount++;
                    }
                    
                    if (showDead && isAlive) continue;
                    if (!showAll && !isAlive) continue;
                    
                    shownCount++;
                    
                    if (compact) {
                        System.out.println(service);
                    } else {
                        String status = isAlive ? "✓" : "✗";
                        String descriptor = getServiceDescriptor(binder);
                        System.out.printf("%s %-35s %s%n", status, service, 
                                         shortenDescriptor(descriptor));
                    }
                } else {
                    deadCount++;
                    if (showDead || showAll) {
                        shownCount++;
                        if (compact) {
                            System.out.println(service);
                        } else {
                            System.out.printf("✗ %-35s %s%n", service, "[Not Found]");
                        }
                    }
                }
            } catch (Exception e) {
                if (showAll) {
                    shownCount++;
                    System.out.printf("? %-35s %s%n", service, "[Error]");
                }
            }
        }
        
        if (!compact) {
            System.out.println("==========================================");
            System.out.printf("Shown: %d | Alive: %d | Dead: %d | Total: %d%n",
                             shownCount, aliveCount, deadCount, services.length);
        }
    }

    private static void listServiceMethods(String serviceName, String filter,
                                          boolean showParams, boolean showReturn,
                                          boolean showSignature) throws Exception {
        Object service = getServiceProxy(serviceName);
        
        System.out.println("Methods for service: " + serviceName);
        System.out.println("==========================================");
        
        Pattern filterPattern = filter != null ? 
            Pattern.compile(filter, Pattern.CASE_INSENSITIVE) : null;
        
        Method[] methods = service.getClass().getDeclaredMethods();
        Arrays.sort(methods, (m1, m2) -> m1.getName().compareTo(m2.getName()));
        
        int count = 0;
        for (Method method : methods) {
            String name = method.getName();
            
            // 跳过内部方法
            if (name.startsWith("$") || name.equals("asBinder") || 
                name.equals("toString") || name.equals("hashCode") ||
                name.equals("equals") || name.equals("getClass") ||
                name.equals("notify") || name.equals("notifyAll") ||
                name.equals("wait")) {
                continue;
            }
            
            if (filterPattern != null && !filterPattern.matcher(name).find()) {
                continue;
            }
            
            count++;
            
            if (showSignature) {
                System.out.println(getFullMethodSignature(method));
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(name);
                
                if (showParams || showReturn) {
                    sb.append("(");
                    Class<?>[] paramTypes = method.getParameterTypes();
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(getSimpleTypeName(paramTypes[i]));
                    }
                    sb.append(")");
                }
                
                if (showReturn) {
                    sb.append(" -> ");
                    sb.append(getSimpleTypeName(method.getReturnType()));
                }
                
                System.out.println("  " + sb.toString());
            }
        }
        
        System.out.println("==========================================");
        System.out.println("Total: " + count + " methods");
    }

    private static void callService(String serviceName, String methodName, 
                                   String[] args, String[] types, int userId,
                                   boolean jsonOutput, boolean rawOutput) throws Exception {
        Object service = getServiceProxy(serviceName);
        
        if (!rawOutput && !jsonOutput) {
            System.out.println("Service: " + serviceName);
            System.out.println("Method: " + methodName);
            System.out.println("Arguments: " + Arrays.toString(args));
            System.out.println("------------------------------------------");
        }
        
        Method targetMethod = findBestMethod(service.getClass(), methodName, args, types);
        
        if (targetMethod == null) {
            System.err.println("Method not found: " + methodName);
            System.err.println("\nAvailable methods with similar names:");
            for (Method method : service.getClass().getDeclaredMethods()) {
                if (method.getName().toLowerCase().contains(methodName.toLowerCase())) {
                    System.err.println("  " + getFullMethodSignature(method));
                }
            }
            throw new RuntimeException("Method not found: " + methodName);
        }
        
        Object[] convertedArgs = convertArgs(targetMethod, args, types);
        
        long startTime = System.currentTimeMillis();
        Object result = targetMethod.invoke(service, convertedArgs);
        long endTime = System.currentTimeMillis();
        
        if (jsonOutput) {
            System.out.println(toJson(result));
        } else if (rawOutput) {
            System.out.println(result);
        } else {
            System.out.println("------------------------------------------");
            System.out.println("Result: " + formatResult(result, 0));
            System.out.println("Time: " + (endTime - startTime) + "ms");
        }
    }

    private static void getServiceInfo(String serviceName, boolean verbose, 
                                      boolean json) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        Object binder = getServiceMethod.invoke(null, serviceName);
        
        if (binder == null) {
            throw new RuntimeException("Service not found: " + serviceName);
        }
        
        String descriptor = getServiceDescriptor(binder);
        boolean isAlive = pingBinderObject(binder);
        
        if (json) {
            System.out.println("{");
            System.out.println("  \"name\": \"" + serviceName + "\",");
            System.out.println("  \"interface\": \"" + descriptor + "\",");
            System.out.println("  \"alive\": " + isAlive + ",");
            System.out.println("  \"class\": \"" + binder.getClass().getName() + "\"");
            System.out.println("}");
            return;
        }
        
        System.out.println("Service Information: " + serviceName);
        System.out.println("==========================================");
        System.out.println("  Name:      " + serviceName);
        System.out.println("  Interface: " + descriptor);
        System.out.println("  Status:    " + (isAlive ? "✓ ALIVE" : "✗ DEAD"));
        System.out.println("  Class:     " + binder.getClass().getName());
        
        if (verbose) {
            System.out.println("  Binder:    " + binder);
            
            try {
                Object service = getServiceProxy(serviceName);
                Method[] methods = service.getClass().getDeclaredMethods();
                int publicMethods = 0;
                for (Method m : methods) {
                    if (Modifier.isPublic(m.getModifiers()) && !m.getName().startsWith("$")) {
                        publicMethods++;
                    }
                }
                System.out.println("  Methods:   " + publicMethods);
            } catch (Exception e) {
                System.out.println("  Methods:   [Unable to count]");
            }
            
            if (serviceStubMap.containsKey(serviceName)) {
                System.out.println("  Stub:      " + serviceStubMap.get(serviceName));
            }
        }
    }

    private static void pingService(String serviceName, int count, 
                                   int interval) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        Object binder = getServiceMethod.invoke(null, serviceName);
        
        if (binder == null) {
            System.out.println("✗ Service not found: " + serviceName);
            return;
        }
        
        int successCount = 0;
        long totalTime = 0;
        
        for (int i = 0; i < count; i++) {
            long start = System.nanoTime();
            boolean isAlive = pingBinderObject(binder);
            long elapsed = (System.nanoTime() - start) / 1000000;
            totalTime += elapsed;
            
            if (isAlive) {
                successCount++;
                System.out.printf("ping %s: alive time=%dms%n", serviceName, elapsed);
            } else {
                System.out.printf("ping %s: dead%n", serviceName);
            }
            
            if (i < count - 1 && interval > 0) {
                Thread.sleep(interval);
            }
        }
        
        if (count > 1) {
            System.out.println("---");
            System.out.printf("%d pings, %d success, avg time=%.2fms%n", 
                             count, successCount, (double) totalTime / count);
        }
    }

    private static void dumpService(String serviceName, String[] args) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        Object binder = getServiceMethod.invoke(null, serviceName);
        
        if (binder == null) {
            throw new RuntimeException("Service not found: " + serviceName);
        }
        
        System.out.println("Dump: " + serviceName);
        System.out.println("==========================================");
        
        // 尝试多种dump方法
        boolean dumped = false;
        
        // 方法1: dump(FileDescriptor, PrintWriter, String[])
        try {
            Method dumpMethod = binder.getClass().getMethod("dump", 
                java.io.FileDescriptor.class, PrintWriter.class, String[].class);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            dumpMethod.invoke(binder, null, pw, args);
            pw.flush();
            String output = sw.toString();
            if (!output.isEmpty()) {
                System.out.println(output);
                dumped = true;
            }
        } catch (NoSuchMethodException e) {
            // 继续尝试其他方法
        }
        
        // 方法2: dump(FileDescriptor, String[])
        if (!dumped) {
            try {
                Method dumpMethod = binder.getClass().getMethod("dump", 
                    java.io.FileDescriptor.class, String[].class);
                dumpMethod.invoke(binder, null, args);
                System.out.println("(Output may be in logcat)");
                dumped = true;
            } catch (NoSuchMethodException e) {
                // 继续
            }
        }
        
        if (!dumped) {
            System.out.println("This service does not support dump operation");
        }
    }

    private static void sendRawTransaction(String serviceName, int code, 
                                          String data) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        Object binder = getServiceMethod.invoke(null, serviceName);
        
        if (binder == null) {
            throw new RuntimeException("Service not found: " + serviceName);
        }
        
        System.out.println("Sending raw transaction to " + serviceName);
        System.out.println("Code: " + code);
        System.out.println("Data: " + data);
        
        Class<?> parcelClass = Class.forName("android.os.Parcel");
        Method obtainMethod = parcelClass.getMethod("obtain");
        Object dataParcel = obtainMethod.invoke(null);
        Object replyParcel = obtainMethod.invoke(null);
        
        try {
            // 写入interface token
            String descriptor = getServiceDescriptor(binder);
            Method writeInterfaceToken = parcelClass.getMethod("writeInterfaceToken", String.class);
            writeInterfaceToken.invoke(dataParcel, descriptor);
            if (!data.equals("empty") && !data.isEmpty()) {
                byte[] bytes = hexToBytes(data);
                Method writeByteArray = parcelClass.getMethod("writeByteArray", byte[].class);
                writeByteArray.invoke(dataParcel, (Object) bytes);
            }
            Class<?> iBinderClass = Class.forName("android.os.IBinder");
            Method transactMethod = iBinderClass.getMethod("transact", 
                int.class, parcelClass, parcelClass, int.class);
            Boolean result = (Boolean) transactMethod.invoke(binder, code, dataParcel, replyParcel, 0);
            
            System.out.println("------------------------------------------");
            System.out.println("Transaction result: " + result);
            Method dataSize = parcelClass.getMethod("dataSize");
            int replySize = (Integer) dataSize.invoke(replyParcel);
            System.out.println("Reply size: " + replySize + " bytes");
            
            if (replySize > 0) {
                Method setDataPosition = parcelClass.getMethod("setDataPosition", int.class);
                setDataPosition.invoke(replyParcel, 0);
                
                Method marshall = parcelClass.getMethod("marshall");
                byte[] replyBytes = (byte[]) marshall.invoke(replyParcel);
                System.out.println("Reply data: " + bytesToHex(replyBytes));
            }
        } finally {
            Method recycleMethod = parcelClass.getMethod("recycle");
            recycleMethod.invoke(dataParcel);
            recycleMethod.invoke(replyParcel);
        }
    }

    private static void searchServices(String pattern, boolean searchMethods,
                                      boolean searchInterface) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method listServicesMethod = serviceManagerClass.getDeclaredMethod("listServices");
        String[] services = (String[]) listServicesMethod.invoke(null);
        
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        
        System.out.println("Search results for: " + pattern);
        System.out.println("==========================================");
        
        int matchCount = 0;
        
        for (String serviceName : services) {
            try {
                Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
                Object binder = getServiceMethod.invoke(null, serviceName);
                
                if (binder == null) continue;
                
                boolean matched = false;
                List<String> matchedMethods = new ArrayList<>();
                
                // 搜索接口名
                if (searchInterface) {
                    String descriptor = getServiceDescriptor(binder);
                    if (p.matcher(serviceName).find() || 
                        (descriptor != null && p.matcher(descriptor).find())) {
                        matched = true;
                    }
                }
                
                // 搜索方法名
                if (searchMethods) {
                    try {
                        Object service = getServiceProxy(serviceName);
                        for (Method method : service.getClass().getDeclaredMethods()) {
                            if (p.matcher(method.getName()).find()) {
                                matched = true;
                                matchedMethods.add(method.getName());
                            }
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }
                
                if (matched) {
                    matchCount++;
                    System.out.println("\n[" + serviceName + "]");
                    if (!matchedMethods.isEmpty()) {
                        System.out.println("  Matched methods: " + String.join(", ", matchedMethods));
                    }
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
        
        System.out.println("\n==========================================");
        System.out.println("Found " + matchCount + " matches");
    }

    private static void inspectInterface(String interfaceName) throws Exception {
        String stubName = interfaceName + "$Stub";
        
        try {
            Class<?> stubClass = Class.forName(stubName);
            Class<?> interfaceClass = Class.forName(interfaceName);
            
            System.out.println("Interface: " + interfaceName);
            System.out.println("==========================================");
            
            // 显示transaction codes
            System.out.println("\nTransaction Codes:");
            for (java.lang.reflect.Field field : stubClass.getDeclaredFields()) {
                if (field.getName().startsWith("TRANSACTION_")) {
                    field.setAccessible(true);
                    int code = field.getInt(null);
                    String methodName = field.getName().substring("TRANSACTION_".length());
                    System.out.printf("  %d: %s%n", code, methodName);
                }
            }
            
            // 显示方法
            System.out.println("\nMethods:");
            for (Method method : interfaceClass.getDeclaredMethods()) {
                System.out.println("  " + getFullMethodSignature(method));
            }
            
        } catch (ClassNotFoundException e) {
            System.err.println("Interface not found: " + interfaceName);
        }
    }

    private static void extractConstants(String className) throws Exception {
        try {
            Class<?> clazz = Class.forName(className);
            
            System.out.println("Constants from: " + className);
            System.out.println("==========================================");
            
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(null);
                        System.out.printf("  %-40s = %s%n", field.getName(), value);
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found: " + className);
        }
    }

    private static void exportServiceInterface(String serviceName, 
                                              String format) throws Exception {
        Object service = getServiceProxy(serviceName);
        String descriptor = getServiceDescriptor(getBinderObject(serviceName));
        
        System.out.println("// Generated interface for: " + serviceName);
        System.out.println("// Interface: " + descriptor);
        System.out.println();
        
        switch (format.toLowerCase()) {
            case "java":
                exportAsJava(service, descriptor);
                break;
            case "kotlin":
                exportAsKotlin(service, descriptor);
                break;
            case "json":
                exportAsJson(service, descriptor);
                break;
            default:
                System.err.println("Unknown format: " + format);
        }
    }
    
    private static void exportAsJava(Object service, String descriptor) {
        String interfaceName = descriptor != null ? 
            descriptor.substring(descriptor.lastIndexOf('.') + 1) : "IUnknownService";
        
        System.out.println("public interface " + interfaceName + " {");
        
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("$") || method.getName().equals("asBinder")) {
                continue;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("    ");
            sb.append(getSimpleTypeName(method.getReturnType()));
            sb.append(" ");
            sb.append(method.getName());
            sb.append("(");
            
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(getSimpleTypeName(params[i]));
                sb.append(" arg").append(i);
            }
            
            sb.append(") throws RemoteException;");
            System.out.println(sb.toString());
        }
        
        System.out.println("}");
    }
    
    private static void exportAsKotlin(Object service, String descriptor) {
        String interfaceName = descriptor != null ? 
            descriptor.substring(descriptor.lastIndexOf('.') + 1) : "IUnknownService";
        
        System.out.println("interface " + interfaceName + " {");
        
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("$") || method.getName().equals("asBinder")) {
                continue;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("    fun ");
            sb.append(method.getName());
            sb.append("(");
            
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("arg").append(i).append(": ");
                sb.append(toKotlinType(params[i]));
            }
            
            sb.append("): ");
            sb.append(toKotlinType(method.getReturnType()));
            System.out.println(sb.toString());
        }
        
        System.out.println("}");
    }
    
    private static void exportAsJson(Object service, String descriptor) {
        System.out.println("{");
        System.out.println("  \"interface\": \"" + descriptor + "\",");
        System.out.println("  \"methods\": [");
        
        Method[] methods = service.getClass().getDeclaredMethods();
        boolean first = true;
        
        for (Method method : methods) {
            if (method.getName().startsWith("$") || method.getName().equals("asBinder")) {
                continue;
            }
            
            if (!first) System.out.println(",");
            first = false;
            
            System.out.println("    {");
            System.out.println("      \"name\": \"" + method.getName() + "\",");
            System.out.println("      \"returnType\": \"" + method.getReturnType().getName() + "\",");
            System.out.print("      \"parameters\": [");
            
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) System.out.print(", ");
                System.out.print("\"" + params[i].getName() + "\"");
            }
            
            System.out.println("]");
            System.out.print("    }");
        }
        
        System.out.println("\n  ]");
        System.out.println("}");
    }

    private static void startInteractiveShell() throws Exception {
        System.out.println("Binder Interactive Shell v" + VERSION);
        System.out.println("Type 'help' for commands, 'exit' to quit");
        System.out.println();
        
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        
        while (true) {
            System.out.print("binder> ");
            String line = scanner.nextLine().trim();
            
            if (line.isEmpty()) continue;
            
            if (line.equals("exit") || line.equals("quit")) {
                break;
            }
            
            String[] shellArgs = parseShellArgs(line);
            
            try {
                main(shellArgs);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
            
            System.out.println();
        }
        
        scanner.close();
        System.out.println("Goodbye!");
    }

    // ==================== 辅助方法 ====================
    
    private static Object getServiceProxy(String serviceName) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        Object binder = getServiceMethod.invoke(null, serviceName);
        
        if (binder == null) {
            throw new RuntimeException("Service not found: " + serviceName);
        }
        
        String stubClassName = getStubClassName(serviceName, binder);
        Class<?> stubClass = Class.forName(stubClassName);
        Class<?> ibinderClass = Class.forName("android.os.IBinder");
        Method asInterfaceMethod = stubClass.getMethod("asInterface", ibinderClass);
        return asInterfaceMethod.invoke(null, binder);
    }
    
    private static Object getBinderObject(String serviceName) throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        return getServiceMethod.invoke(null, serviceName);
    }

    private static String getStubClassName(String serviceName, Object binder) throws Exception {
        if (serviceStubMap.containsKey(serviceName)) {
            return serviceStubMap.get(serviceName);
        }
        
        String descriptor = getServiceDescriptor(binder);
        
        if (descriptor != null) {
            String stubName = descriptor + "$Stub";
            try {
                Class.forName(stubName);
                return stubName;
            } catch (ClassNotFoundException e) {
                // 继续尝试其他方式
            }
        }
        
        String[] prefixes = {
            "android.os.I",
            "android.app.I",
            "android.content.I",
            "android.content.pm.I",
            "android.view.I",
            "android.hardware.I",
            "android.media.I",
            "android.net.I",
            "android.net.wifi.I",
            "com.android.internal.telephony.I",
            "com.android.internal.app.I",
            "com.android.internal.statusbar.I"
        };
        
        String capitalizedName = capitalize(serviceName);
        
        for (String prefix : prefixes) {
            String className = prefix + capitalizedName + "$Stub";
            try {
                Class.forName(className);
                return className;
            } catch (ClassNotFoundException e) {
                // 继续
            }
            
            // 尝试带Manager后缀
            className = prefix + capitalizedName + "Manager$Stub";
            try {
                Class.forName(className);
                return className;
            } catch (ClassNotFoundException e) {
                // 继续
            }
        }
        
        throw new RuntimeException("Cannot find Stub class for service: " + serviceName + 
                                  " (descriptor: " + descriptor + ")");
    }

    private static String getServiceDescriptor(Object binder) {
        try {
            Method getInterfaceDescriptor = binder.getClass().getMethod("getInterfaceDescriptor");
            return (String) getInterfaceDescriptor.invoke(binder);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static boolean pingBinderObject(Object binder) {
        try {
            Method pingBinderMethod = binder.getClass().getMethod("pingBinder");
            return (Boolean) pingBinderMethod.invoke(binder);
        } catch (Exception e) {
            return false;
        }
    }

    private static Method findBestMethod(Class<?> clazz, String methodName, 
                                        String[] args, String[] types) {
        List<Method> candidates = new ArrayList<>();
        
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                if (method.getParameterCount() == args.length) {
                    candidates.add(method);
                }
            }
        }
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        // 如果指定了类型，根据类型匹配
        if (types != null && types.length > 0) {
            for (Method method : candidates) {
                Class<?>[] paramTypes = method.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < types.length && i < paramTypes.length; i++) {
                    Class<?> expectedType = resolveType(types[i]);
                    if (expectedType != null && !paramTypes[i].isAssignableFrom(expectedType) &&
                        paramTypes[i] != expectedType) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return method;
                }
            }
        }
        
        // 尝试根据参数值推断类型
        for (Method method : candidates) {
            Class<?>[] paramTypes = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                if (!canConvert(args[i], paramTypes[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return method;
            }
        }
        
        return candidates.get(0);
    }

    private static Class<?> resolveType(String typeName) {
        if (typeAliases.containsKey(typeName.toLowerCase())) {
            return typeAliases.get(typeName.toLowerCase());
        }
        
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    private static boolean canConvert(String arg, Class<?> type) {
        if (type == String.class) return true;
        if (arg.equals("null")) return !type.isPrimitive();
        
        try {
            if (type == int.class || type == Integer.class) {
                Integer.parseInt(arg);
                return true;
            }
            if (type == long.class || type == Long.class) {
                Long.parseLong(arg);
                return true;
            }
            if (type == boolean.class || type == Boolean.class) {
                return arg.equalsIgnoreCase("true") || arg.equalsIgnoreCase("false");
            }
            if (type == float.class || type == Float.class) {
                Float.parseFloat(arg);
                return true;
            }
            if (type == double.class || type == Double.class) {
                Double.parseDouble(arg);
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        
        return false;
    }

    private static Object[] convertArgs(Method method, String[] args, String[] types) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] converted = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            String arg = i < args.length ? args[i] : "null";
            Class<?> targetType = paramTypes[i];
            
            // 如果指定了类型，优先使用指定的类型
            if (types != null && i < types.length && !types[i].isEmpty()) {
                Class<?> specifiedType = resolveType(types[i]);
                if (specifiedType != null) {
                    targetType = specifiedType;
                }
            }
            
            converted[i] = convertArg(arg, targetType, paramTypes[i]);
        }
        
        return converted;
    }

    private static Object convertArg(String arg, Class<?> targetType, Class<?> paramType) {
        // 检查常量
        if (constantsMap.containsKey(arg)) {
            Object constant = constantsMap.get(arg);
            if (constant == null) return null;
            
            // 尝试类型转换
            if (paramType.isInstance(constant)) {
                return constant;
            }
            if (paramType == int.class || paramType == Integer.class) {
                if (constant instanceof Number) {
                    return ((Number) constant).intValue();
                }
            }
        }
        
        // null处理
        if (arg.equals("null") || arg.equals("NULL")) {
            return null;
        }
        
        // 基本类型转换
        if (paramType == String.class) {
            // 处理转义字符串
            if (arg.startsWith("\"") && arg.endsWith("\"")) {
                return arg.substring(1, arg.length() - 1);
            }
            return arg;
        }
        
        if (paramType == int.class || paramType == Integer.class) {
            return parseIntArg(arg);
        }
        
        if (paramType == long.class || paramType == Long.class) {
            return parseLongArg(arg);
        }
        
        if (paramType == boolean.class || paramType == Boolean.class) {
            return parseBooleanArg(arg);
        }
        
        if (paramType == float.class || paramType == Float.class) {
            return Float.parseFloat(arg);
        }
        
        if (paramType == double.class || paramType == Double.class) {
            return Double.parseDouble(arg);
        }
        
        if (paramType == byte.class || paramType == Byte.class) {
            return Byte.parseByte(arg);
        }
        
        if (paramType == short.class || paramType == Short.class) {
            return Short.parseShort(arg);
        }
        
        if (paramType == char.class || paramType == Character.class) {
            return arg.length() > 0 ? arg.charAt(0) : '\0';
        }
        
        // 数组类型
        if (paramType.isArray()) {
            return parseArray(arg, paramType.getComponentType());
        }
        
        // List类型
        if (List.class.isAssignableFrom(paramType)) {
            return parseList(arg);
        }
        
        // ComponentName
        if (paramType.getName().equals("android.content.ComponentName")) {
            return parseComponentName(arg);
        }
        
        // Intent
        if (paramType.getName().equals("android.content.Intent")) {
            return parseIntent(arg);
        }
        
        // Bundle
        if (paramType.getName().equals("android.os.Bundle")) {
            return parseBundle(arg);
        }
        
        // Uri
        if (paramType.getName().equals("android.net.Uri")) {
            return parseUri(arg);
        }
        
        // 尝试直接返回字符串
        return arg;
    }
    
    private static int parseIntArg(String arg) {
        if (constantsMap.containsKey(arg)) {
            Object val = constantsMap.get(arg);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        }
        
        // 支持十六进制
        if (arg.startsWith("0x") || arg.startsWith("0X")) {
            return Integer.parseInt(arg.substring(2), 16);
        }
        
        // 支持二进制
        if (arg.startsWith("0b") || arg.startsWith("0B")) {
            return Integer.parseInt(arg.substring(2), 2);
        }
        
        // 支持八进制
        if (arg.startsWith("0") && arg.length() > 1 && !arg.contains(".")) {
            try {
                return Integer.parseInt(arg.substring(1), 8);
            } catch (NumberFormatException e) {
                // 继续尝试十进制
            }
        }
        
        return Integer.parseInt(arg);
    }
    
    private static long parseLongArg(String arg) {
        String s = arg;
        if (s.endsWith("L") || s.endsWith("l")) {
            s = s.substring(0, s.length() - 1);
        }
        
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseLong(s.substring(2), 16);
        }
        
        return Long.parseLong(s);
    }
    
    private static boolean parseBooleanArg(String arg) {
        if (arg.equalsIgnoreCase("true") || arg.equals("1")) {
            return true;
        }
        if (arg.equalsIgnoreCase("false") || arg.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value: " + arg);
    }
    
    private static Object parseArray(String arg, Class<?> componentType) {
        // 格式: [elem1,elem2,elem3] 或 空数组 []
        if (arg.equals("[]") || arg.isEmpty()) {
            return Array.newInstance(componentType, 0);
        }
        
        String content = arg;
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        
        if (content.isEmpty()) {
            return Array.newInstance(componentType, 0);
        }
        
        String[] parts = content.split(",");
        Object array = Array.newInstance(componentType, parts.length);
        
        for (int i = 0; i < parts.length; i++) {
            Object value = convertArg(parts[i].trim(), componentType, componentType);
            Array.set(array, i, value);
        }
        
        return array;
    }
    
    private static List<?> parseList(String arg) {
        if (arg.equals("[]") || arg.isEmpty()) {
            return new ArrayList<>();
        }
        
        String content = arg;
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        
        List<String> list = new ArrayList<>();
        for (String part : content.split(",")) {
            list.add(part.trim());
        }
        return list;
    }
    
    private static Object parseComponentName(String arg) {
        try {
            Class<?> cnClass = Class.forName("android.content.ComponentName");
            
            // 格式: package/class 或 package/.class
            if (arg.contains("/")) {
                String[] parts = arg.split("/");
                String pkg = parts[0];
                String cls = parts[1];
                if (cls.startsWith(".")) {
                    cls = pkg + cls;
                }
                return cnClass.getConstructor(String.class, String.class)
                             .newInstance(pkg, cls);
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Object parseIntent(String arg) {
        try {
            Class<?> intentClass = Class.forName("android.content.Intent");
            Object intent = intentClass.newInstance();
            
            // 简单格式: action 或 action;uri
            if (arg.contains(";")) {
                String[] parts = arg.split(";");
                Method setAction = intentClass.getMethod("setAction", String.class);
                setAction.invoke(intent, parts[0]);
                
                if (parts.length > 1) {
                    Object uri = parseUri(parts[1]);
                    if (uri != null) {
                        Method setData = intentClass.getMethod("setData", 
                            Class.forName("android.net.Uri"));
                        setData.invoke(intent, uri);
                    }
                }
            } else {
                Method setAction = intentClass.getMethod("setAction", String.class);
                setAction.invoke(intent, arg);
            }
            
            return intent;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Object parseBundle(String arg) {
        try {
            Class<?> bundleClass = Class.forName("android.os.Bundle");
            Object bundle = bundleClass.newInstance();
            
            if (arg.equals("{}") || arg.isEmpty()) {
                return bundle;
            }
            
            // 格式: {key1:value1,key2:value2}
            String content = arg;
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            Method putString = bundleClass.getMethod("putString", String.class, String.class);
            
            for (String pair : content.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    putString.invoke(bundle, kv[0].trim(), kv[1].trim());
                }
            }
            
            return bundle;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Object parseUri(String arg) {
        try {
            Class<?> uriClass = Class.forName("android.net.Uri");
            Method parse = uriClass.getMethod("parse", String.class);
            return parse.invoke(null, arg);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatResult(Object result, int indent) {
        if (result == null) {
            return "null";
        }
        
        String prefix = repeat("  ", indent);
        
        // 数组处理
        if (result.getClass().isArray()) {
            int length = Array.getLength(result);
            if (length == 0) {
                return "[]";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < length; i++) {
                sb.append(prefix).append("  [").append(i).append("] ");
                sb.append(formatResult(Array.get(result, i), indent + 1));
                if (i < length - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(prefix).append("]");
            return sb.toString();
        }
        
        // List处理
        if (result instanceof List) {
            List<?> list = (List<?>) result;
            if (list.isEmpty()) {
                return "[]";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            int i = 0;
            for (Object item : list) {
                sb.append(prefix).append("  [").append(i++).append("] ");
                sb.append(formatResult(item, indent + 1));
                sb.append("\n");
            }
            sb.append(prefix).append("]");
            return sb.toString();
        }
        
        // Map处理
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            if (map.isEmpty()) {
                return "{}";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(prefix).append("  ");
                sb.append(entry.getKey()).append(": ");
                sb.append(formatResult(entry.getValue(), indent + 1));
                sb.append("\n");
            }
            sb.append(prefix).append("}");
            return sb.toString();
        }
        
        // 尝试反射获取字段信息
        Class<?> clazz = result.getClass();
        if (!clazz.isPrimitive() && !clazz.getName().startsWith("java.lang")) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(clazz.getSimpleName()).append(" {\n");
                
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    
                    field.setAccessible(true);
                    Object value = field.get(result);
                    sb.append(prefix).append("  ");
                    sb.append(field.getName()).append(": ");
                    sb.append(value);
                    sb.append("\n");
                }
                
                sb.append(prefix).append("}");
                return sb.toString();
            } catch (Exception e) {
                // 回退到toString
            }
        }
        
        return result.toString();
    }

    private static String toJson(Object obj) {
        if (obj == null) return "null";
        
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }
        
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        
        return "\"" + escapeJson(obj.toString()) + "\"";
    }
    
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void printBinderStats() throws Exception {
        System.out.println("Binder Statistics");
        System.out.println("==========================================");
        
        // 尝试获取Binder统计信息
        try {
            Class<?> binderClass = Class.forName("android.os.Binder");
            
            // 获取当前进程的Binder调用统计
            Method getCallingPid = binderClass.getMethod("getCallingPid");
            Method getCallingUid = binderClass.getMethod("getCallingUid");
            
            System.out.println("Current Process:");
            System.out.println("  Calling PID: " + getCallingPid.invoke(null));
            System.out.println("  Calling UID: " + getCallingUid.invoke(null));
        } catch (Exception e) {
            System.out.println("Limited statistics available: " + e.getMessage());
        }
        
        // 列出服务数量
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method listServicesMethod = serviceManagerClass.getDeclaredMethod("listServices");
            String[] services = (String[]) listServicesMethod.invoke(null);
            System.out.println("  Total Services: " + services.length);
        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== 工具方法 ====================
    
    private static void printUsage() {
        System.out.println("Binder Command Line Tool v" + VERSION);
        System.out.println("==========================================");
        System.out.println("Usage: binder <command> [options] [args...]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  list, ls              List all Binder services");
        System.out.println("  methods, m <service>  List available methods");
        System.out.println("  call, c <service> <method> [args]");
        System.out.println("                        Call service method");
        System.out.println("  info, i <service>     Get service information");
        System.out.println("  ping, p <service>     Check if service is alive");
        System.out.println("  dump, d <service>     Dump service state");
        System.out.println("  monitor               Monitor service changes");
        System.out.println("  transact <service> <code> <data>");
        System.out.println("                        Send raw transaction");
        System.out.println("  search <pattern>      Search services/methods");
        System.out.println("  interface <name>      Inspect interface");
        System.out.println("  constants [class]     Show/extract constants");
        System.out.println("  export <service>      Export interface definition");
        System.out.println("  shell                 Start interactive shell");
        System.out.println("  stats                 Show Binder statistics");
        System.out.println("  help [command]        Show help");
        System.out.println("  version               Show version");
        System.out.println();
        System.out.println("Use 'binder help <command>' for more info.");
    }
    
    private static void printCommandHelp(String command) {
        switch (command) {
            case "list":
            case "ls":
                System.out.println("binder list [options]");
                System.out.println();
                System.out.println("List all registered Binder services.");
                System.out.println();
                System.out.println("Options:");
                System.out.println("  -a, --all       Show all services (including dead)");
                System.out.println("  -d, --dead      Show only dead services");
                System.out.println("  -c, --compact   Compact output (names only)");
                System.out.println("  -f, --filter <pattern>");
                System.out.println("                  Filter by name pattern (regex)");
                break;
                
            case "call":
            case "c":
                System.out.println("binder call <service> <method> [options] [args...]");
                System.out.println();
                System.out.println("Call a method on a Binder service.");
                System.out.println();
                System.out.println("Options:");
                System.out.println("  -t, --types <types>");
                System.out.println("                  Specify parameter types (comma-separated)");
                System.out.println("  -u, --user <id> Specify user ID");
                System.out.println("  -j, --json      Output result as JSON");
                System.out.println("  -r, --raw       Output raw result");
                System.out.println();
                System.out.println("Argument Formats:");
                System.out.println("  Integers:   123, 0xFF, 0b1010");
                System.out.println("  Longs:      123L, 0xFFL");
                System.out.println("  Booleans:   true, false, 1, 0");
                System.out.println("  Strings:    hello, \"hello world\"");
                System.out.println("  null:       null, NULL");
                System.out.println("  Arrays:     [1,2,3], []");
                System.out.println("  Component:  com.example/.MyActivity");
                System.out.println("  Uri:        content://..., file://...");
                System.out.println();
                System.out.println("Constants:");
                System.out.println("  FLAG_ACTIVITY_NEW_TASK, USER_CURRENT, etc.");
                break;
                
            case "methods":
            case "m":
                System.out.println("binder methods <service> [options]");
                System.out.println();
                System.out.println("List available methods for a service.");
                System.out.println();
                System.out.println("Options:");
                System.out.println("  -f, --filter <pattern>");
                System.out.println("                  Filter methods by name");
                System.out.println("  -p, --params    Show parameter types");
                System.out.println("  -r, --return    Show return types");
                System.out.println("  -s, --signature Show full signatures");
                break;
                
            default:
                System.out.println("No detailed help for: " + command);
                printUsage();
        }
    }

    private static String[] getExtraArgs(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return new String[0];
        }
        String[] extra = new String[args.length - startIndex];
        System.arraycopy(args, startIndex, extra, 0, extra.length);
        return extra;
    }
    
    private static boolean hasFlag(String[] args, String... flags) {
        for (String arg : args) {
            for (String flag : flags) {
                if (arg.equals(flag)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static int getIntFlag(String[] args, String shortFlag, String longFlag, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(shortFlag) || args[i].equals(longFlag)) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }
    
    private static String getStringFlag(String[] args, String shortFlag, String longFlag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(shortFlag) || args[i].equals(longFlag)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
    
    private static boolean isDebugMode(String[] args) {
        return hasFlag(args, "--debug", "-D");
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static String shortenDescriptor(String descriptor) {
        if (descriptor == null) return "[null]";
        if (descriptor.length() > 45) {
            return "..." + descriptor.substring(descriptor.length() - 42);
        }
        return descriptor;
    }

    private static String getSimpleTypeName(Class<?> type) {
        if (type.isArray()) {
            return getSimpleTypeName(type.getComponentType()) + "[]";
        }
        String name = type.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }
    
        private static String getFullMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(getSimpleTypeName(method.getReturnType()));
        sb.append(" ");
        sb.append(method.getName());
        sb.append("(");
        
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(getSimpleTypeName(params[i]));
        }
        
        sb.append(")");
        
        // 添加异常信息
        Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0) {
            sb.append(" throws ");
            for (int i = 0; i < exceptions.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(getSimpleTypeName(exceptions[i]));
            }
        }
        
        return sb.toString();
    }
    
    private static String toKotlinType(Class<?> type) {
        if (type == void.class) return "Unit";
        if (type == int.class) return "Int";
        if (type == long.class) return "Long";
        if (type == float.class) return "Float";
        if (type == double.class) return "Double";
        if (type == boolean.class) return "Boolean";
        if (type == byte.class) return "Byte";
        if (type == short.class) return "Short";
        if (type == char.class) return "Char";
        if (type == Integer.class) return "Int?";
        if (type == Long.class) return "Long?";
        if (type == Float.class) return "Float?";
        if (type == Double.class) return "Double?";
        if (type == Boolean.class) return "Boolean?";
        if (type == String.class) return "String?";
        
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (componentType == int.class) return "IntArray";
            if (componentType == long.class) return "LongArray";
            if (componentType == float.class) return "FloatArray";
            if (componentType == double.class) return "DoubleArray";
            if (componentType == boolean.class) return "BooleanArray";
            if (componentType == byte.class) return "ByteArray";
            if (componentType == short.class) return "ShortArray";
            if (componentType == char.class) return "CharArray";
            return "Array<" + toKotlinType(componentType) + ">";
        }
        
        return getSimpleTypeName(type) + "?";
    }
    
    private static String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    private static byte[] hexToBytes(String hex) {
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        
        int len = hex.length();
        byte[] data = new byte[len / 2];
        
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        
        return data;
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private static String[] parseShellArgs(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        boolean escaped = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"' || c == '\'') {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                    quoteChar = 0;
                } else {
                    current.append(c);
                }
                continue;
            }
            
            if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current = new StringBuilder();
                }
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            args.add(current.toString());
        }
        
        return args.toArray(new String[0]);
    }
    
    // ==================== 额外功能方法 ====================
    
    /**
     * 获取服务的所有transaction codes
     */
    private static Map<Integer, String> getTransactionCodes(String serviceName) throws Exception {
        Map<Integer, String> codes = new HashMap<>();
        
        Object binder = getBinderObject(serviceName);
        if (binder == null) return codes;
        
        String stubClassName = getStubClassName(serviceName, binder);
        Class<?> stubClass = Class.forName(stubClassName);
        
        for (java.lang.reflect.Field field : stubClass.getDeclaredFields()) {
            if (field.getName().startsWith("TRANSACTION_")) {
                field.setAccessible(true);
                int code = field.getInt(null);
                String methodName = field.getName().substring("TRANSACTION_".length());
                codes.put(code, methodName);
            }
        }
        
        return codes;
    }
    
    /**
     * 比较两个服务的接口差异
     */
    public static void compareServices(String service1, String service2) throws Exception {
        Object proxy1 = getServiceProxy(service1);
        Object proxy2 = getServiceProxy(service2);
        
        Set<String> methods1 = new HashSet<>();
        Set<String> methods2 = new HashSet<>();
        
        for (Method m : proxy1.getClass().getDeclaredMethods()) {
            if (!m.getName().startsWith("$")) {
                methods1.add(m.getName());
            }
        }
        
        for (Method m : proxy2.getClass().getDeclaredMethods()) {
            if (!m.getName().startsWith("$")) {
                methods2.add(m.getName());
            }
        }
        
        System.out.println("Service Comparison: " + service1 + " vs " + service2);
        System.out.println("==========================================");
        
        // 仅在service1中
        Set<String> only1 = new HashSet<>(methods1);
        only1.removeAll(methods2);
        if (!only1.isEmpty()) {
            System.out.println("\nOnly in " + service1 + ":");
            for (String m : only1) {
                System.out.println("  + " + m);
            }
        }
        
        // 仅在service2中
        Set<String> only2 = new HashSet<>(methods2);
        only2.removeAll(methods1);
        if (!only2.isEmpty()) {
            System.out.println("\nOnly in " + service2 + ":");
            for (String m : only2) {
                System.out.println("  + " + m);
            }
        }
        
        // 共同的方法
        Set<String> common = new HashSet<>(methods1);
        common.retainAll(methods2);
        System.out.println("\nCommon methods: " + common.size());
    }
    
    /**
     * 获取服务的权限要求（如果可用）
     */
    public static void getServicePermissions(String serviceName) throws Exception {
        System.out.println("Permission requirements for: " + serviceName);
        System.out.println("==========================================");
        System.out.println("Note: Permission detection requires runtime analysis");
        System.out.println("      or access to service implementation.");
        
        // 常见服务的已知权限
        Map<String, String[]> knownPermissions = new HashMap<>();
        knownPermissions.put("activity", new String[]{
            "android.permission.GET_TASKS",
            "android.permission.INTERACT_ACROSS_USERS",
            "android.permission.MANAGE_ACTIVITY_STACKS"
        });
        knownPermissions.put("package", new String[]{
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.QUERY_ALL_PACKAGES"
        });
        knownPermissions.put("wifi", new String[]{
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE"
        });
        knownPermissions.put("power", new String[]{
            "android.permission.WAKE_LOCK",
            "android.permission.DEVICE_POWER",
            "android.permission.REBOOT"
        });
        knownPermissions.put("notification", new String[]{
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.MANAGE_NOTIFICATIONS"
        });
        knownPermissions.put("location", new String[]{
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        });
        
        if (knownPermissions.containsKey(serviceName)) {
            System.out.println("\nKnown permissions:");
            for (String perm : knownPermissions.get(serviceName)) {
                System.out.println("  • " + perm);
            }
        } else {
            System.out.println("\nNo known permission data for this service.");
        }
    }
    
    /**
     * 测试服务方法的调用（dry run模式）
     */
    public static void testServiceMethod(String serviceName, String methodName) throws Exception {
        Object service = getServiceProxy(serviceName);
        
        System.out.println("Test Information for: " + serviceName + "." + methodName);
        System.out.println("==========================================");
        
        boolean found = false;
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                found = true;
                System.out.println("\nMethod: " + getFullMethodSignature(method));
                System.out.println("Parameters:");
                
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 0) {
                    System.out.println("  (none)");
                } else {
                    for (int i = 0; i < paramTypes.length; i++) {
                        Class<?> type = paramTypes[i];
                        String defaultValue = getDefaultValueString(type);
                        System.out.printf("  [%d] %s (default: %s)%n", 
                                         i, getSimpleTypeName(type), defaultValue);
                    }
                }
                
                System.out.println("Return type: " + getSimpleTypeName(method.getReturnType()));
                
                Class<?>[] exceptions = method.getExceptionTypes();
                if (exceptions.length > 0) {
                    System.out.println("Throws:");
                    for (Class<?> ex : exceptions) {
                        System.out.println("  • " + getSimpleTypeName(ex));
                    }
                }
                System.out.println();
            }
        }
        
        if (!found) {
            System.out.println("Method not found: " + methodName);
        }
    }
    
    private static String getDefaultValueString(Class<?> type) {
        if (type == int.class || type == Integer.class) return "0";
        if (type == long.class || type == Long.class) return "0L";
        if (type == float.class || type == Float.class) return "0.0f";
        if (type == double.class || type == Double.class) return "0.0";
        if (type == boolean.class || type == Boolean.class) return "false";
        if (type == byte.class || type == Byte.class) return "0";
        if (type == short.class || type == Short.class) return "0";
        if (type == char.class || type == Character.class) return "'\\0'";
        if (type == String.class) return "\"\"";
        if (type.isArray()) return "[]";
        return "null";
    }
    
    /**
     * 生成服务调用示例代码
     */
    public static void generateExample(String serviceName, String methodName) throws Exception {
        Object service = getServiceProxy(serviceName);
        String descriptor = getServiceDescriptor(getBinderObject(serviceName));
        
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                System.out.println("// Example code for calling " + serviceName + "." + methodName);
                System.out.println();
                System.out.println("// Java:");
                System.out.println("IBinder binder = ServiceManager.getService(\"" + serviceName + "\");");
                
                String interfaceName = descriptor != null ? 
                    descriptor.substring(descriptor.lastIndexOf('.') + 1) : "IService";
                String stubName = interfaceName + ".Stub";
                
                System.out.println(interfaceName + " service = " + stubName + ".asInterface(binder);");
                
                StringBuilder call = new StringBuilder();
                call.append("service.").append(methodName).append("(");
                
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) call.append(", ");
                    call.append(getDefaultValueString(params[i]));
                }
                call.append(");");
                
                if (method.getReturnType() != void.class) {
                    System.out.println(getSimpleTypeName(method.getReturnType()) + 
                                      " result = " + call.toString());
                } else {
                    System.out.println(call.toString());
                }
                
                System.out.println();
                System.out.println("// Shell (using this tool):");
                System.out.print("binder call " + serviceName + " " + methodName);
                for (int i = 0; i < params.length; i++) {
                    System.out.print(" " + getDefaultValueString(params[i]).replace("\"", ""));
                }
                System.out.println();
                
                return;
            }
        }
        
        System.out.println("Method not found: " + methodName);
    }
    
    /**
     * 监控特定服务的方法调用（需要root或调试权限）
     */
    public static void traceService(String serviceName, int durationSeconds) throws Exception {
        System.out.println("Tracing service: " + serviceName);
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("==========================================");
        System.out.println("Note: Full tracing requires systrace or perfetto.");
        System.out.println("This is a simplified version that monitors service state.");
        System.out.println();
        
        Object binder = getBinderObject(serviceName);
        if (binder == null) {
            System.out.println("Service not found!");
            return;
        }
        
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        int checkCount = 0;
        
        while (System.currentTimeMillis() < endTime) {
            boolean alive = pingBinderObject(binder);
            System.out.printf("[%d] Service %s: %s%n", 
                             checkCount++, serviceName, alive ? "alive" : "dead");
            Thread.sleep(500);
        }
        
        System.out.println();
        System.out.println("Trace completed. " + checkCount + " checks performed.");
    }
}