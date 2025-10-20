package com.wqry085.deployesystem;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.appcompat.widget.AppCompatEditText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalView extends AppCompatEditText {
    private static final String TAG = "TerminalView";
    private String currentPrompt = "";
    private InputListener inputListener;
    private boolean isProcessingOutput = false;

    public interface InputListener {
        void onInput(String input);
    }

    public TerminalView(Context context) {
        super(context);
        init();
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setTypeface(Typeface.MONOSPACE);
        setTextColor(Color.WHITE);
        setBackgroundColor(Color.BLACK);
        setTextSize(14);

        setText(currentPrompt);
        setSelection(currentPrompt.length());

        addTextChangedListener(new TextWatcher() {
            private String lastText;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                lastText = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isProcessingOutput) return;

                String text = s.toString();
                if (!text.startsWith(currentPrompt)) {
                    // 只在必要时恢复，不要无限 setText
                    isProcessingOutput = true;
                    setText(currentPrompt);
                    setSelection(currentPrompt.length());
                    isProcessingOutput = false;
                } else if (text.length() < currentPrompt.length()) {
                    isProcessingOutput = true;
                    setText(currentPrompt);
                    setSelection(currentPrompt.length());
                    isProcessingOutput = false;
                }
            }
        });
    }

    public void appendOutput(String output) {
        if (output == null || output.isEmpty()) return;

        post(() -> {
            isProcessingOutput = true;
            try {
                // 清理输出
                String cleanOutput = cleanTerminalOutput(output);

                // 检测提示符变化
                detectPromptFromOutput(cleanOutput);

                // 追加到 EditText
                append(cleanOutput);

                // 移动光标到底部
                setSelection(getText().length());

            } catch (Exception e) {
                Log.e(TAG, "Error in appendOutput: " + e.getMessage(), e);
            } finally {
                isProcessingOutput = false;
            }
        });
    }

    private String cleanTerminalOutput(String output) {
        if (output == null) return "";

        // 移除 ANSI 控制符
        String cleaned = output.replaceAll("\\x1B\\[[0-9;]*[mK]", "");

        // 确保结尾换行
        if (!cleaned.endsWith("\n")) {
            cleaned += "\n";
        }
        return cleaned;
    }

    private void detectPromptFromOutput(String output) {
        if (output == null) return;

        Pattern promptPattern = Pattern.compile("([\\w@:/~.-]+)\\s*([$#])\\s*$");
        Matcher matcher = promptPattern.matcher(output);

        if (matcher.find()) {
            String newPrompt = matcher.group(1) + matcher.group(2) + " ";
            if (!newPrompt.equals(currentPrompt)) {
                currentPrompt = newPrompt;
                Log.d(TAG, "Prompt changed to: " + currentPrompt);
            }
        }
    }

    public void setInputListener(InputListener listener) {
        this.inputListener = listener;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            handleEnter();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleEnter() {
    String fullText = getText().toString();
    // 取出最后一个 prompt 后的用户输入
    int lastPromptIndex = fullText.lastIndexOf(currentPrompt);
    String input = "";
    if (lastPromptIndex >= 0) {
        input = fullText.substring(lastPromptIndex + currentPrompt.length());
    }

    if (inputListener != null) {
        // 确保以换行结尾
        if (!input.endsWith("\n")) {
            input += "\n";
        }
        inputListener.onInput(input);
    }

    // ⚠️ 不要把 input 再写回去（避免回显两次）
    // 只在末尾追加换行和新的 prompt
    isProcessingOutput = true;
    append("\n" + currentPrompt);
    setSelection(getText().length());
    isProcessingOutput = false;
}
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT |
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return super.onCreateInputConnection(outAttrs);
    }

    public void clearTerminal() {
        isProcessingOutput = true;
        setText(currentPrompt);
        setSelection(currentPrompt.length());
        isProcessingOutput = false;
    }
}