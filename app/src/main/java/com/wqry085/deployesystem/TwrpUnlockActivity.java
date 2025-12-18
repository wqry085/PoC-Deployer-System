package com.wqry085.deployesystem;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import com.wqry085.deployesystem.databinding.ActivityTwrpUnlockBinding;
import com.wqry085.deployesystem.next.BootLoader;

public class TwrpUnlockActivity extends AppCompatActivity {

    private ActivityTwrpUnlockBinding binding;
    private static final String REQUIRED_TEXT = "I know the risk";
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_AGREED = "disclaimer_agreed";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_AGREED, false)) {
            startBootLoader();
            return;
        }

        binding = ActivityTwrpUnlockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        
        setSeekbarEnabled(false);

        binding.riskEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                boolean isMatch = input.equals(REQUIRED_TEXT);
                setSeekbarEnabled(isMatch);
                if (isMatch) {
                    hideKeyboard();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.swipeToUnlockSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    
                    float alpha = 1.0f - (progress / 100.0f);
                    binding.swipeTextView.setAlpha(Math.max(0f, alpha));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                binding.swipeTextView.clearAnimation();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.getProgress() >= 85) { 
                    seekBar.setProgress(100);
                    unlock();
                } else { 
                    seekBar.setProgress(0);
                    binding.swipeTextView.setAlpha(1.0f);
                    
                    if (binding.swipeToUnlockSeekBar.isEnabled()) {
                        startPulseAnimation();
                    }
                }
            }
        });
    }

    private void setSeekbarEnabled(boolean enabled) {
        binding.swipeToUnlockSeekBar.setEnabled(enabled);
        if (enabled) {
            binding.swipeToUnlockSeekBar.setAlpha(1.0f);
            binding.swipeTextView.setAlpha(1.0f);
            startPulseAnimation();
        } else {
            binding.swipeToUnlockSeekBar.setAlpha(0.5f);
            binding.swipeTextView.setAlpha(0.5f);
            binding.swipeTextView.clearAnimation();
        }
    }

    private void startPulseAnimation() {
         Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
         binding.swipeTextView.startAnimation(pulse);
    }

    private void unlock() {
        
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AGREED, true)
                .apply();

        
        binding.getRoot().performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        
        startBootLoader();
    }

    private void startBootLoader() {
        
        Intent intent = new Intent(TwrpUnlockActivity.this, BootLoader.class);
        startActivity(intent);
        if (binding != null) { 
             overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        finish();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            binding.riskEditText.clearFocus();
        }
    }
}