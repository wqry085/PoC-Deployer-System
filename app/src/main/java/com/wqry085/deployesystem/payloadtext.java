package com.wqry085.deployesystem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class payloadtext {
    
    /**
     * 高级配置插入方法 - 支持智能位置识别，完全保留原始格式
     */
    public static String smartInsertParameters(String originalText, String positionSpecifier, String[] newParams) {
        if (originalText == null || newParams == null || newParams.length == 0) {
            return originalText;
        }
        
        // 解析位置指示符
        int insertIndex = parsePositionSpecifier(originalText, positionSpecifier);
        if (insertIndex == -1) {
            insertIndex = getParameterCount(originalText);
        }
        
        return insertConfigParameters(originalText, insertIndex, newParams);
    }
    
    /**
     * 解析位置指示符
     */
    private static int parsePositionSpecifier(String originalText, String specifier) {
        if (specifier == null || specifier.trim().isEmpty()) {
            return getParameterCount(originalText);
        }
        
        String spec = specifier.trim().toLowerCase();
        
        // 处理数字索引
        if (spec.matches("\\d+")) {
            int index = Integer.parseInt(spec);
            return Math.min(index, getParameterCount(originalText));
        }
        
        // 处理开头/末尾
        if (spec.equals("start") || spec.equals("beginning") || spec.equals("first")) {
            return 0;
        }
        if (spec.equals("end") || spec.equals("last")) {
            return getParameterCount(originalText);
        }
        
        // 处理相对位置（before/after + 参数）
        Matcher matcher = Pattern.compile("(before|after)\\s+(.+)", Pattern.CASE_INSENSITIVE).matcher(spec);
        if (matcher.find()) {
            String relation = matcher.group(1).toLowerCase();
            String targetParam = matcher.group(2).trim();
            int targetIndex = findParameterIndex(originalText, targetParam);
            
            if (targetIndex != -1) {
                return relation.equals("before") ? targetIndex : targetIndex + 1;
            }
        }
        
        // 处理纯参数名（默认在参数后插入）
        int paramIndex = findParameterIndex(originalText, specifier);
        if (paramIndex != -1) {
            return paramIndex + 1;
        }
        
        return -1;
    }
    
    /**
     * 基础插入方法 - 完全保留原始换行符
     */
    public static String insertConfigParameters(String originalText, int insertIndex, String[] newParams) {
        if (originalText == null || newParams == null || newParams.length == 0) {
            return originalText;
        }
        
        // 使用正则表达式分割，保留末尾的空行
        String[] lines = originalText.split("\\r?\\n", -1);
        if (lines.length < 2) {
            return originalText;
        }
        
        try {
            // 解析当前参数计数（第一行）
            int currentCount = Integer.parseInt(lines[0].trim());
            
            // 收集所有非空参数行和它们的原始格式（包括空行）
            List<String> allLines = new ArrayList<>();
            List<String> paramLines = new ArrayList<>(); // 仅非空参数行
            List<Boolean> isParamLine = new ArrayList<>(); // 标记哪些行是参数行
            
            for (int i = 0; i < lines.length; i++) {
                allLines.add(lines[i]);
                String trimmedLine = lines[i].trim();
                if (i == 0) {
                    // 第一行是计数行
                    isParamLine.add(false);
                } else if (!trimmedLine.isEmpty()) {
                    paramLines.add(lines[i]); // 保留原始格式，包括空格
                    isParamLine.add(true);
                } else {
                    isParamLine.add(false);
                }
            }
            
            // 验证插入位置
            if (insertIndex < 0) insertIndex = 0;
            if (insertIndex > paramLines.size()) insertIndex = paramLines.size();
            
            // 插入新参数到参数列表
            for (int i = 0; i < newParams.length; i++) {
                String param = newParams[i].trim();
                if (!param.isEmpty()) {
                    paramLines.add(insertIndex + i, param);
                }
            }
            
            // 更新参数计数
            int newCount = currentCount + newParams.length;
            
            // 重新构建完整的文本，保留所有原始格式
            StringBuilder result = new StringBuilder();
            result.append(newCount); // 第一行（计数行）
            
            int paramIndex = 0;
            for (int i = 1; i < allLines.size(); i++) {
                if (isParamLine.get(i)) {
                    // 这是参数行，使用更新后的参数
                    if (paramIndex < paramLines.size()) {
                        result.append("\n").append(paramLines.get(paramIndex));
                        paramIndex++;
                    }
                } else {
                    // 这是空行或注释行，保留原样
                    result.append("\n").append(allLines.get(i));
                }
            }
            
            // 添加剩余的新参数（如果有）
            while (paramIndex < paramLines.size()) {
                result.append("\n").append(paramLines.get(paramIndex));
                paramIndex++;
            }
            
            return result.toString();
            
        } catch (NumberFormatException e) {
            return originalText;
        }
    }
    
    /**
     * 获取非空参数数量
     */
    private static int getParameterCount(String configText) {
        if (configText == null) return 0;
        String[] lines = configText.split("\\r?\\n");
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 查找参数索引（基于非空参数行）
     */
    private static int findParameterIndex(String originalText, String targetParam) {
        if (originalText == null || targetParam == null) return -1;
        
        String[] lines = originalText.split("\\r?\\n");
        String target = targetParam.trim();
        int paramCount = 0;
        
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                if (line.equals(target) || line.startsWith(target + "=")) {
                    return paramCount;
                }
                paramCount++;
            }
        }
        return -1;
    }
    
    /**
     * 批量插入方法
     */
    public static String batchInsert(String originalText, String[][] insertions) {
        if (originalText == null || insertions == null) return originalText;
        
        String result = originalText;
        for (String[] insertion : insertions) {
            if (insertion.length >= 2) {
                String position = insertion[0];
                String[] params = new String[insertion.length - 1];
                System.arraycopy(insertion, 1, params, 0, params.length);
                result = smartInsertParameters(result, position, params);
            }
        }
        return result;
    }
    
    /**
     * 验证配置格式
     */
    public static boolean validateConfigFormat(String configText) {
        if (configText == null || configText.trim().isEmpty()) return false;
        
        String[] lines = configText.split("\\r?\\n");
        if (lines.length < 2) return false;
        
        try {
            int count = Integer.parseInt(lines[0].trim());
            int actualCount = 0;
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    actualCount++;
                }
            }
            return count == actualCount;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 获取配置的详细信息（用于调试）
     */
    public static String getConfigInfo(String configText) {
        if (configText == null) return "Null config";
        
        String[] lines = configText.split("\\r?\\n");
        StringBuilder info = new StringBuilder();
        info.append("总行数: ").append(lines.length).append("\n");
        
        int paramCount = 0;
        int emptyLineCount = 0;
        
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                info.append("计数行: '").append(lines[i]).append("'\n");
            } else if (lines[i].trim().isEmpty()) {
                emptyLineCount++;
                info.append("空行 ").append(emptyLineCount).append(": 位置 ").append(i).append("\n");
            } else {
                paramCount++;
                info.append("参数 ").append(paramCount).append(": '").append(lines[i]).append("'\n");
            }
        }
        
        info.append("非空参数行: ").append(paramCount).append("\n");
        info.append("空行: ").append(emptyLineCount).append("\n");
        
        return info.toString();
    }
}