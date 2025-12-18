package com.wqry085.deployesystem.next;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatTextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义日志显示器控件 - 增强版
 * 
 * 功能特性：
 * - 双指缩放字体大小
 * - 双击暂停/恢复
 * - 日志级别高亮显示
 * - 日志过滤
 * - 搜索高亮
 * - 多主题支持
 * - 导出功能
 */
public class LogView extends FrameLayout {

    // ==================== 日志级别枚举 ====================
    public enum LogLevel {
        VERBOSE("V", 0),
        DEBUG("D", 1),
        INFO("I", 2),
        WARNING("W", 3),
        ERROR("E", 4),
        FATAL("F", 5);

        private final String tag;
        private final int priority;

        LogLevel(String tag, int priority) {
            this.tag = tag;
            this.priority = priority;
        }

        public String getTag() { return tag; }
        public int getPriority() { return priority; }

        public static LogLevel fromTag(String tag) {
            for (LogLevel level : values()) {
                if (level.tag.equalsIgnoreCase(tag)) {
                    return level;
                }
            }
            return INFO;
        }
    }

    // ==================== 日志条目类 ====================
    public static class LogEntry {
        public final long timestamp;
        public final String time;
        public final LogLevel level;
        public final String tag;
        public final String message;
        public final String rawLog;
        public final int pid;
        public final int tid;

        public LogEntry(String time, LogLevel level, String tag, String message,
                        int pid, int tid, String rawLog) {
            this.timestamp = System.currentTimeMillis();
            this.time = time;
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.pid = pid;
            this.tid = tid;
            this.rawLog = rawLog;
        }

        public LogEntry(String time, LogLevel level, String tag, String message) {
            this(time, level, tag, message, 0, 0, null);
        }
    }

    // ==================== 主题配置 ====================
    public static class Theme {
        public int backgroundColor;
        public int textColor;
        public int verboseColor;
        public int debugColor;
        public int infoColor;
        public int warningColor;
        public int errorColor;
        public int fatalColor;
        public int timeColor;
        public int tagColor;
        public int searchHighlightColor;
        public int searchHighlightTextColor;
        public int lineNumberColor;
        public int indicatorBackgroundColor;
        public int indicatorTextColor;

        public static Theme dark() {
            Theme theme = new Theme();
            theme.backgroundColor = Color.parseColor("#1E1E1E");
            theme.textColor = Color.WHITE;
            theme.verboseColor = Color.parseColor("#BBBBBB");
            theme.debugColor = Color.parseColor("#33B5E5");
            theme.infoColor = Color.parseColor("#99CC00");
            theme.warningColor = Color.parseColor("#FFBB33");
            theme.errorColor = Color.parseColor("#FF4444");
            theme.fatalColor = Color.parseColor("#CC0000");
            theme.timeColor = Color.parseColor("#888888");
            theme.tagColor = Color.parseColor("#AA66CC");
            theme.searchHighlightColor = Color.parseColor("#FFFF00");
            theme.searchHighlightTextColor = Color.BLACK;
            theme.lineNumberColor = Color.parseColor("#666666");
            theme.indicatorBackgroundColor = Color.parseColor("#AA000000");
            theme.indicatorTextColor = Color.WHITE;
            return theme;
        }

        public static Theme light() {
            Theme theme = new Theme();
            theme.backgroundColor = Color.parseColor("#FFFFFF");
            theme.textColor = Color.parseColor("#333333");
            theme.verboseColor = Color.parseColor("#666666");
            theme.debugColor = Color.parseColor("#0066CC");
            theme.infoColor = Color.parseColor("#008800");
            theme.warningColor = Color.parseColor("#CC6600");
            theme.errorColor = Color.parseColor("#CC0000");
            theme.fatalColor = Color.parseColor("#990000");
            theme.timeColor = Color.parseColor("#999999");
            theme.tagColor = Color.parseColor("#6633CC");
            theme.searchHighlightColor = Color.parseColor("#FFFF00");
            theme.searchHighlightTextColor = Color.BLACK;
            theme.lineNumberColor = Color.parseColor("#CCCCCC");
            theme.indicatorBackgroundColor = Color.parseColor("#AA333333");
            theme.indicatorTextColor = Color.WHITE;
            return theme;
        }

        public static Theme monokai() {
            Theme theme = new Theme();
            theme.backgroundColor = Color.parseColor("#272822");
            theme.textColor = Color.parseColor("#F8F8F2");
            theme.verboseColor = Color.parseColor("#75715E");
            theme.debugColor = Color.parseColor("#66D9EF");
            theme.infoColor = Color.parseColor("#A6E22E");
            theme.warningColor = Color.parseColor("#E6DB74");
            theme.errorColor = Color.parseColor("#F92672");
            theme.fatalColor = Color.parseColor("#FD971F");
            theme.timeColor = Color.parseColor("#75715E");
            theme.tagColor = Color.parseColor("#AE81FF");
            theme.searchHighlightColor = Color.parseColor("#49483E");
            theme.searchHighlightTextColor = Color.parseColor("#F8F8F2");
            theme.lineNumberColor = Color.parseColor("#464741");
            theme.indicatorBackgroundColor = Color.parseColor("#AA000000");
            theme.indicatorTextColor = Color.parseColor("#F8F8F2");
            return theme;
        }
    }

    // ==================== 回调接口 ====================
    public interface OnLogFilterChangeListener {
        void onFilterChanged(int visibleCount, int totalCount);
    }

    public interface OnExportCompleteListener {
        void onExportComplete(boolean success, String filePath, String error);
    }

    public interface OnScaleChangeListener {
        void onScaleChanged(float textSize, float scaleFactor);
    }

    // ==================== 缩放指示器视图 ====================
    private class ZoomIndicatorView extends View {
        private Paint backgroundPaint;
        private Paint textPaint;
        private RectF rect;
        private String text = "";
        private float alpha = 0f;

        public ZoomIndicatorView(Context context) {
            super(context);
            init();
        }

        private void init() {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(currentTheme.indicatorBackgroundColor);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(currentTheme.indicatorTextColor);
            textPaint.setTextSize(36);
            textPaint.setTextAlign(Paint.Align.CENTER);

            rect = new RectF();
        }

        public void show(String text) {
            this.text = text;
            this.alpha = 1f;
            invalidate();
            setVisibility(VISIBLE);
        }

        public void hide() {
            animateHide();
        }

        private void animateHide() {
            ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
            animator.setDuration(300);
            animator.addUpdateListener(animation -> {
                alpha = (float) animation.getAnimatedValue();
                invalidate();
                if (alpha == 0f) {
                    setVisibility(GONE);
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (alpha <= 0) return;

            float width = textPaint.measureText(text) + 60;
            float height = 80;
            float left = (getWidth() - width) / 2;
            float top = (getHeight() - height) / 2;
            rect.set(left, top, left + width, top + height);

            backgroundPaint.setAlpha((int) (alpha * 170));
            canvas.drawRoundRect(rect, 20, 20, backgroundPaint);

            textPaint.setAlpha((int) (alpha * 255));
            float textY = top + height / 2 + textPaint.getTextSize() / 3;
            canvas.drawText(text, getWidth() / 2f, textY, textPaint);
        }

        public void updateTheme() {
            backgroundPaint.setColor(currentTheme.indicatorBackgroundColor);
            textPaint.setColor(currentTheme.indicatorTextColor);
        }
    }

    // ==================== 成员变量 ====================
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
    private LinearLayout contentLayout;
    private AppCompatTextView lineNumberView;
    private AppCompatTextView textView;
    private AppCompatTextView statusBar;
    private ZoomIndicatorView zoomIndicator;

    private final CopyOnWriteArrayList<LogEntry> allLogs = new CopyOnWriteArrayList<>();
    private final List<LogEntry> filteredLogs = new ArrayList<>();
    private SpannableStringBuilder logBuilder;
    private SpannableStringBuilder lineNumberBuilder;
    private SimpleDateFormat timeFormat;
    private Theme currentTheme;

    // 缩放相关
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private float currentTextSize = 12f;
    private float minTextSize = 6f;
    private float maxTextSize = 32f;
    private float defaultTextSize = 12f;
    private boolean isScaling = false;
    private OnScaleChangeListener scaleChangeListener;

    // 缩放指示器隐藏延迟
    private final Handler indicatorHandler = new Handler(Looper.getMainLooper());
    private Runnable hideIndicatorRunnable;

    // 过滤设置
    private EnumSet<LogLevel> enabledLevels = EnumSet.allOf(LogLevel.class);
    private String tagFilter = "";
    private String searchKeyword = "";
    private LogLevel minLevel = LogLevel.VERBOSE;

    // 显示设置
    private boolean showTimestamp = true;
    private boolean showTag = true;
    private boolean showPidTid = false;
    private boolean showLineNumbers = true;
    private boolean autoScroll = true;
    private boolean isPaused = false;
    private int maxLogEntries = 10000;

    // 统计信息
    private int[] levelCounts = new int[LogLevel.values().length];

    // 监听器
    private OnLogFilterChangeListener filterChangeListener;

    // 工具
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 批量更新
    private final List<LogEntry> pendingLogs = new ArrayList<>();
    private boolean isUpdatePending = false;
    private static final int BATCH_UPDATE_DELAY = 50;

    // 正则表达式
    private static final Pattern LOGCAT_PATTERN = Pattern.compile(
            "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+([^:]+):\\s*(.*)$"
    );

    private static final Pattern SIMPLE_LOGCAT_PATTERN = Pattern.compile(
            "^([VDIWEF])/([^:]+):\\s*(.*)$"
    );

    // ==================== 构造函数 ====================
    public LogView(Context context) {
        super(context);
        init(context);
    }

    public LogView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LogView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // ==================== 初始化 ====================
    private void init(Context context) {
        currentTheme = Theme.dark();
        logBuilder = new SpannableStringBuilder();
        lineNumberBuilder = new SpannableStringBuilder();
        timeFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());

        setupViews(context);
        setupGestureDetectors(context);
        applyTheme();
    }

    private void setupViews(Context context) {
        // 主容器
        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 状态栏
        statusBar = new AppCompatTextView(context);
        statusBar.setTextSize(10);
        statusBar.setPadding(16, 8, 16, 8);
        statusBar.setVisibility(GONE);
        mainContainer.addView(statusBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 自定义可拦截触摸的 ScrollView
        scrollView = new ScrollView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                // 双指时不拦截，让缩放手势处理
                if (ev.getPointerCount() > 1) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                // 缩放时不处理滚动
                if (isScaling) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }
        };
        scrollView.setFillViewport(true);

        // 自定义可拦截触摸的 HorizontalScrollView
        horizontalScrollView = new HorizontalScrollView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getPointerCount() > 1) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (isScaling) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }
        };
        horizontalScrollView.setFillViewport(true);

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);

        // 行号视图
        lineNumberView = new AppCompatTextView(context);
        lineNumberView.setTextSize(currentTextSize);
        lineNumberView.setTypeface(Typeface.MONOSPACE);
        lineNumberView.setPadding(8, 16, 8, 16);
        lineNumberView.setGravity(Gravity.END);
        contentLayout.addView(lineNumberView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 分隔线
        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#444444"));
        contentLayout.addView(divider, new LinearLayout.LayoutParams(
                2, LinearLayout.LayoutParams.MATCH_PARENT));

        // 日志文本视图
        textView = new AppCompatTextView(context);
        textView.setTextSize(currentTextSize);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setPadding(12, 16, 16, 16);
        textView.setTextIsSelectable(true);
        contentLayout.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        horizontalScrollView.addView(contentLayout);
        scrollView.addView(horizontalScrollView);
        mainContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        addView(mainContainer);

        // 缩放指示器（覆盖在最上层）
        zoomIndicator = new ZoomIndicatorView(context);
        zoomIndicator.setVisibility(GONE);
        addView(zoomIndicator, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 滚动监听
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            View child = scrollView.getChildAt(0);
            if (child != null) {
                int diff = child.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());
                autoScroll = diff <= 100;
            }
        });
    }

    private void setupGestureDetectors(Context context) {
        // 缩放手势检测器
        scaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    private float startTextSize;

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        isScaling = true;
                        startTextSize = currentTextSize;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        float newSize = startTextSize * scaleFactor;

                        // 限制范围
                        newSize = Math.max(minTextSize, Math.min(maxTextSize, newSize));

                        if (Math.abs(newSize - currentTextSize) > 0.1f) {
                            setTextSizeInternal(newSize);
                            showZoomIndicator();
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        isScaling = false;
                        scheduleHideZoomIndicator();
                    }
                });

        // 设置不使用 span 缩放 (Android 11+)
        try {
            scaleGestureDetector.setQuickScaleEnabled(false);
        } catch (Exception ignored) {}

        // 普通手势检测器
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        togglePause();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTapEvent(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        showQuickActions();
                    }
                });

        // 设置触摸监听
        setOnTouchListener((v, event) -> {
            boolean scaleHandled = scaleGestureDetector.onTouchEvent(event);
            boolean gestureHandled = gestureDetector.onTouchEvent(event);

            // 如果是缩放手势，消费事件
            if (event.getPointerCount() > 1 || isScaling) {
                return true;
            }

            return scaleHandled || gestureHandled;
        });
    }

    // ==================== 缩放相关方法 ====================
    private void setTextSizeInternal(float size) {
        currentTextSize = size;
        textView.setTextSize(size);
        lineNumberView.setTextSize(size);

        if (scaleChangeListener != null) {
            scaleChangeListener.onScaleChanged(size, size / defaultTextSize);
        }
    }

    private void showZoomIndicator() {
        int percentage = Math.round((currentTextSize / defaultTextSize) * 100);
        String text = percentage + "%";
        zoomIndicator.show(text);

        // 取消之前的隐藏任务
        if (hideIndicatorRunnable != null) {
            indicatorHandler.removeCallbacks(hideIndicatorRunnable);
        }
    }

    private void scheduleHideZoomIndicator() {
        hideIndicatorRunnable = () -> zoomIndicator.hide();
        indicatorHandler.postDelayed(hideIndicatorRunnable, 1000);
    }

    /**
     * 设置字体大小
     */
    public void setTextSize(float size) {
        this.defaultTextSize = size;
        setTextSizeInternal(size);
    }

    /**
     * 获取当前字体大小
     */
    public float getTextSize() {
        return currentTextSize;
    }

    /**
     * 设置缩放范围
     */
    public void setScaleRange(float minSize, float maxSize) {
        this.minTextSize = minSize;
        this.maxTextSize = maxSize;
    }

    /**
     * 重置缩放
     */
    public void resetZoom() {
        setTextSizeInternal(defaultTextSize);
        showZoomIndicator();
        scheduleHideZoomIndicator();
    }

    /**
     * 放大
     */
    public void zoomIn() {
        float newSize = Math.min(currentTextSize + 2, maxTextSize);
        setTextSizeInternal(newSize);
        showZoomIndicator();
        scheduleHideZoomIndicator();
    }

    /**
     * 缩小
     */
    public void zoomOut() {
        float newSize = Math.max(currentTextSize - 2, minTextSize);
        setTextSizeInternal(newSize);
        showZoomIndicator();
        scheduleHideZoomIndicator();
    }

    /**
     * 设置缩放监听器
     */
    public void setOnScaleChangeListener(OnScaleChangeListener listener) {
        this.scaleChangeListener = listener;
    }

    // ==================== 快捷操作 ====================
    private void showQuickActions() {
        // 震动反馈
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

        // 这里可以显示一个 PopupMenu 或自定义对话框
        // 目前简单地显示一个 Toast 提示
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Copy | 🔍 Search | 🗑️ Clear\n");
        sb.append("Current: ").append(filteredLogs.size()).append("/").append(allLogs.size());
        showToast(sb.toString());
    }

    // ==================== 主题方法 ====================
    public void setTheme(Theme theme) {
        this.currentTheme = theme;
        applyTheme();
        zoomIndicator.updateTheme();
        refreshDisplay();
    }

    public void setDarkTheme() {
        setTheme(Theme.dark());
    }

    public void setLightTheme() {
        setTheme(Theme.light());
    }

    public void setMonokaiTheme() {
        setTheme(Theme.monokai());
    }

    private void applyTheme() {
        setBackgroundColor(currentTheme.backgroundColor);
        scrollView.setBackgroundColor(currentTheme.backgroundColor);
        horizontalScrollView.setBackgroundColor(currentTheme.backgroundColor);
        contentLayout.setBackgroundColor(currentTheme.backgroundColor);
        textView.setBackgroundColor(currentTheme.backgroundColor);
        textView.setTextColor(currentTheme.textColor);
        lineNumberView.setBackgroundColor(currentTheme.backgroundColor);
        lineNumberView.setTextColor(currentTheme.lineNumberColor);
        statusBar.setBackgroundColor(Color.parseColor("#333333"));
        statusBar.setTextColor(Color.WHITE);
    }

    private int getLevelColor(LogLevel level) {
        switch (level) {
            case VERBOSE: return currentTheme.verboseColor;
            case DEBUG: return currentTheme.debugColor;
            case INFO: return currentTheme.infoColor;
            case WARNING: return currentTheme.warningColor;
            case ERROR: return currentTheme.errorColor;
            case FATAL: return currentTheme.fatalColor;
            default: return currentTheme.textColor;
        }
    }

    // ==================== 日志添加方法 ====================
    public void appendLog(String log) {
        if (TextUtils.isEmpty(log)) return;
        LogEntry entry = parseLogEntry(log);
        addLogEntry(entry);
    }

    public void appendLogs(List<String> logs) {
        if (logs == null || logs.isEmpty()) return;

        List<LogEntry> entries = new ArrayList<>();
        for (String log : logs) {
            if (!TextUtils.isEmpty(log)) {
                entries.add(parseLogEntry(log));
            }
        }
        addLogEntries(entries);
    }

    private LogEntry parseLogEntry(String log) {
        Matcher matcher = LOGCAT_PATTERN.matcher(log);
        if (matcher.matches()) {
            String time = matcher.group(1);
            int pid = Integer.parseInt(matcher.group(2));
            int tid = Integer.parseInt(matcher.group(3));
            LogLevel level = LogLevel.fromTag(matcher.group(4));
            String tag = matcher.group(5).trim();
            String message = matcher.group(6);
            return new LogEntry(time, level, tag, message, pid, tid, log);
        }

        Matcher simpleMatcher = SIMPLE_LOGCAT_PATTERN.matcher(log);
        if (simpleMatcher.matches()) {
            LogLevel level = LogLevel.fromTag(simpleMatcher.group(1));
            String tag = simpleMatcher.group(2).trim();
            String message = simpleMatcher.group(3);
            String time = timeFormat.format(new Date());
            return new LogEntry(time, level, tag, message, 0, 0, log);
        }

        LogLevel level = detectLevel(log);
        String time = timeFormat.format(new Date());
        return new LogEntry(time, level, "", log, 0, 0, log);
    }

    private LogLevel detectLevel(String log) {
        String upper = log.toUpperCase();
        if (upper.contains("FATAL") || upper.contains("ASSERT")) return LogLevel.FATAL;
        if (upper.contains("ERROR") || upper.contains("ERR ")) return LogLevel.ERROR;
        if (upper.contains("WARN") || upper.contains("WARNING")) return LogLevel.WARNING;
        if (upper.contains("INFO")) return LogLevel.INFO;
        if (upper.contains("DEBUG") || upper.contains("DBG")) return LogLevel.DEBUG;
        if (upper.contains("VERBOSE") || upper.contains("TRACE")) return LogLevel.VERBOSE;
        return LogLevel.INFO;
    }

    private void addLogEntry(LogEntry entry) {
        synchronized (pendingLogs) {
            pendingLogs.add(entry);
            scheduleBatchUpdate();
        }
    }

    private void addLogEntries(List<LogEntry> entries) {
        synchronized (pendingLogs) {
            pendingLogs.addAll(entries);
            scheduleBatchUpdate();
        }
    }

    private void scheduleBatchUpdate() {
        if (!isUpdatePending) {
            isUpdatePending = true;
            mainHandler.postDelayed(this::processPendingLogs, BATCH_UPDATE_DELAY);
        }
    }

    private void processPendingLogs() {
        List<LogEntry> toProcess;
        synchronized (pendingLogs) {
            toProcess = new ArrayList<>(pendingLogs);
            pendingLogs.clear();
            isUpdatePending = false;
        }

        if (toProcess.isEmpty()) return;

        for (LogEntry entry : toProcess) {
            allLogs.add(entry);
            levelCounts[entry.level.ordinal()]++;
        }

        trimLogs();
        applyFilter();
    }

    private void trimLogs() {
        while (allLogs.size() > maxLogEntries) {
            LogEntry removed = allLogs.remove(0);
            levelCounts[removed.level.ordinal()]--;
        }
    }

    // ==================== 便捷日志方法 ====================
    public void v(String tag, String message) {
        addLog(LogLevel.VERBOSE, tag, message);
    }

    public void d(String tag, String message) {
        addLog(LogLevel.DEBUG, tag, message);
    }

    public void i(String tag, String message) {
        addLog(LogLevel.INFO, tag, message);
    }

    public void w(String tag, String message) {
        addLog(LogLevel.WARNING, tag, message);
    }

    public void e(String tag, String message) {
        addLog(LogLevel.ERROR, tag, message);
    }

    public void e(String tag, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n").append(throwable.toString());
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\n    at ").append(element.toString());
        }
        addLog(LogLevel.ERROR, tag, sb.toString());
    }

    public void f(String tag, String message) {
        addLog(LogLevel.FATAL, tag, message);
    }

    private void addLog(LogLevel level, String tag, String message) {
        String time = timeFormat.format(new Date());
        LogEntry entry = new LogEntry(time, level, tag, message);
        addLogEntry(entry);
    }

    // ==================== 过滤方法 ====================
    public void setMinLevel(LogLevel level) {
        this.minLevel = level;
        applyFilter();
    }

    public void setLevelEnabled(LogLevel level, boolean enabled) {
        if (enabled) {
            enabledLevels.add(level);
        } else {
            enabledLevels.remove(level);
        }
        applyFilter();
    }

    public void setTagFilter(String tag) {
        this.tagFilter = tag != null ? tag : "";
        applyFilter();
    }

    public void search(String keyword) {
        this.searchKeyword = keyword != null ? keyword : "";
        applyFilter();
    }

    public void clearSearch() {
        search("");
    }

    private void applyFilter() {
        if (isPaused) return;

        executor.execute(() -> {
            List<LogEntry> newFiltered = new ArrayList<>();

            for (LogEntry entry : allLogs) {
                if (matchesFilter(entry)) {
                    newFiltered.add(entry);
                }
            }

            mainHandler.post(() -> {
                filteredLogs.clear();
                filteredLogs.addAll(newFiltered);
                refreshDisplay();

                if (filterChangeListener != null) {
                    filterChangeListener.onFilterChanged(filteredLogs.size(), allLogs.size());
                }
            });
        });
    }

    private boolean matchesFilter(LogEntry entry) {
        if (entry.level.getPriority() < minLevel.getPriority()) {
            return false;
        }

        if (!enabledLevels.contains(entry.level)) {
            return false;
        }

        if (!TextUtils.isEmpty(tagFilter)) {
            if (!entry.tag.toLowerCase().contains(tagFilter.toLowerCase())) {
                return false;
            }
        }

        if (!TextUtils.isEmpty(searchKeyword)) {
            String lowerKeyword = searchKeyword.toLowerCase();
            boolean found = entry.message.toLowerCase().contains(lowerKeyword) ||
                    entry.tag.toLowerCase().contains(lowerKeyword);
            if (!found) return false;
        }

        return true;
    }

    // ==================== 显示刷新 ====================
    private void refreshDisplay() {
        logBuilder.clear();
        lineNumberBuilder.clear();

        int lineNumber = 1;
        for (LogEntry entry : filteredLogs) {
            appendFormattedEntry(entry, lineNumber++);
        }

        textView.setText(logBuilder);
        lineNumberView.setText(lineNumberBuilder);
        lineNumberView.setVisibility(showLineNumbers ? VISIBLE : GONE);

        updateStatusBar();

        if (autoScroll && !isPaused) {
            scrollToBottom();
        }
    }

    private void appendFormattedEntry(LogEntry entry, int lineNumber) {
        // 行号
        if (showLineNumbers) {
            int start = lineNumberBuilder.length();
            lineNumberBuilder.append(String.format(Locale.getDefault(), "%5d", lineNumber));
            lineNumberBuilder.append("\n");
            lineNumberBuilder.setSpan(
                    new ForegroundColorSpan(currentTheme.lineNumberColor),
                    start, lineNumberBuilder.length() - 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int logStart = logBuilder.length();
        int levelColor = getLevelColor(entry.level);

        // 时间戳
        if (showTimestamp) {
            int start = logBuilder.length();
            logBuilder.append(entry.time);
            logBuilder.setSpan(
                    new ForegroundColorSpan(currentTheme.timeColor),
                    start, logBuilder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            logBuilder.append(" ");
        }

        // PID/TID
        if (showPidTid && entry.pid > 0) {
            int start = logBuilder.length();
            logBuilder.append(String.format(Locale.getDefault(), "%d-%d", entry.pid, entry.tid));
            logBuilder.setSpan(
                    new ForegroundColorSpan(currentTheme.timeColor),
                    start, logBuilder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            logBuilder.append(" ");
        }

        // 日志级别
        int levelStart = logBuilder.length();
        logBuilder.append(entry.level.getTag());
        logBuilder.setSpan(
                new ForegroundColorSpan(levelColor),
                levelStart, logBuilder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logBuilder.setSpan(
                new StyleSpan(Typeface.BOLD),
                levelStart, logBuilder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logBuilder.append(" ");

        // 标签
        if (showTag && !TextUtils.isEmpty(entry.tag)) {
            int start = logBuilder.length();
            logBuilder.append("[").append(entry.tag).append("]");
            logBuilder.setSpan(
                    new ForegroundColorSpan(currentTheme.tagColor),
                    start, logBuilder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            logBuilder.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    start, logBuilder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            logBuilder.append(": ");
        }

        // 消息
        int msgStart = logBuilder.length();
        logBuilder.append(entry.message);
        logBuilder.setSpan(
                new ForegroundColorSpan(currentTheme.textColor),
                msgStart, logBuilder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 搜索高亮
        if (!TextUtils.isEmpty(searchKeyword)) {
            highlightSearchKeyword(logStart);
        }

        logBuilder.append("\n");
    }

    private void highlightSearchKeyword(int entryStart) {
        String text = logBuilder.toString().substring(entryStart);
        String lowerText = text.toLowerCase();
        String lowerKeyword = searchKeyword.toLowerCase();

        int index = 0;
        while ((index = lowerText.indexOf(lowerKeyword, index)) != -1) {
            int start = entryStart + index;
            int end = start + searchKeyword.length();

            logBuilder.setSpan(
                    new BackgroundColorSpan(currentTheme.searchHighlightColor),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            logBuilder.setSpan(
                    new ForegroundColorSpan(currentTheme.searchHighlightTextColor),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            index += searchKeyword.length();
        }
    }

    private void updateStatusBar() {
        if (statusBar.getVisibility() != VISIBLE) return;

        StringBuilder sb = new StringBuilder();
        sb.append("📊 ").append(filteredLogs.size()).append("/").append(allLogs.size());
        sb.append(" | 🔤 ").append(Math.round(currentTextSize)).append("sp");

        if (isPaused) {
            sb.append(" | ⏸️ PAUSED");
        }

        if (!TextUtils.isEmpty(searchKeyword)) {
            sb.append(" | 🔍 \"").append(searchKeyword).append("\"");
        }

        statusBar.setText(sb.toString());
    }

    // ==================== 滚动控制 ====================
    public void scrollToBottom() {
        mainHandler.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    public void scrollToTop() {
        mainHandler.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP));
    }

    public void setAutoScroll(boolean enabled) {
        this.autoScroll = enabled;
    }

    public boolean isAutoScrollEnabled() {
        return autoScroll;
    }

    public void togglePause() {
        isPaused = !isPaused;
        if (!isPaused) {
            applyFilter();
        }
        updateStatusBar();
        showToast(isPaused ? "⏸️ Log paused (double-tap to resume)" : "▶️ Log resumed");
    }

    public boolean isPaused() {
        return isPaused;
    }

    // ==================== 显示设置 ====================
    public void setShowTimestamp(boolean show) {
        this.showTimestamp = show;
        refreshDisplay();
    }

    public void setShowTag(boolean show) {
        this.showTag = show;
        refreshDisplay();
    }

    public void setShowPidTid(boolean show) {
        this.showPidTid = show;
        refreshDisplay();
    }

    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
        refreshDisplay();
    }

    public void setShowStatusBar(boolean show) {
        statusBar.setVisibility(show ? VISIBLE : GONE);
        updateStatusBar();
    }

    public void setTypeface(Typeface typeface) {
        textView.setTypeface(typeface);
        lineNumberView.setTypeface(typeface);
    }

    public void setMaxLogEntries(int max) {
        this.maxLogEntries = max;
        trimLogs();
        applyFilter();
    }

    // ==================== 导出和复制 ====================
    public void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            String text = getLogText();
            ClipData clip = ClipData.newPlainText("Log", text);
            clipboard.setPrimaryClip(clip);
            showToast("📋 Log copied to clipboard");
        }
    }

    public void copyFilteredToClipboard() {
        ClipboardManager clipboard = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            String text = getFilteredLogText();
            ClipData clip = ClipData.newPlainText("Log", text);
            clipboard.setPrimaryClip(clip);
            showToast("📋 Filtered log copied");
        }
    }

    public void exportToFile(File file, OnExportCompleteListener listener) {
        executor.execute(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (LogEntry entry : allLogs) {
                    writer.write(formatEntryForExport(entry));
                    writer.newLine();
                }
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onExportComplete(true, file.getAbsolutePath(), null);
                    }
                    showToast("💾 Exported: " + file.getName());
                });
            } catch (IOException e) {
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onExportComplete(false, null, e.getMessage());
                    }
                    showToast("❌ Export failed: " + e.getMessage());
                });
            }
        });
    }

    private String formatEntryForExport(LogEntry entry) {
        return String.format("%s %s/%s: %s",
                entry.time, entry.level.getTag(), entry.tag, entry.message);
    }

    // ==================== 获取数据 ====================
    public String getLogText() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : allLogs) {
            sb.append(formatEntryForExport(entry)).append("\n");
        }
        return sb.toString();
    }

    public String getFilteredLogText() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : filteredLogs) {
            sb.append(formatEntryForExport(entry)).append("\n");
        }
        return sb.toString();
    }

    public List<LogEntry> getAllLogs() {
        return new ArrayList<>(allLogs);
    }

    public List<LogEntry> getFilteredLogs() {
        return new ArrayList<>(filteredLogs);
    }

    public int getTotalLogCount() {
        return allLogs.size();
    }

    public int getFilteredLogCount() {
        return filteredLogs.size();
    }

    public int getLevelCount(LogLevel level) {
        return levelCounts[level.ordinal()];
    }

    // ==================== 清空 ====================
    public void clear() {
        allLogs.clear();
        filteredLogs.clear();
        levelCounts = new int[LogLevel.values().length];
        logBuilder.clear();
        lineNumberBuilder.clear();
        textView.setText("");
        lineNumberView.setText("");
        updateStatusBar();
    }

    // ==================== 监听器 ====================
    public void setOnLogFilterChangeListener(OnLogFilterChangeListener listener) {
        this.filterChangeListener = listener;
    }

    // ==================== 工具方法 ====================
    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    // ==================== 生命周期 ====================
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mainHandler.removeCallbacksAndMessages(null);
        indicatorHandler.removeCallbacksAndMessages(null);
    }

    public void destroy() {
        executor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
        indicatorHandler.removeCallbacksAndMessages(null);
        clear();
    }
}