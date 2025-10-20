package com.wqry085.deployesystem;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.AdapterView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MaterialDialogHelper {

    private Context context;
    private String title;
    private String message;
    private String positiveButtonText;
    private String negativeButtonText;
    private String neutralButtonText;
    private DialogInterface.OnClickListener positiveButtonListener;
    private DialogInterface.OnClickListener negativeButtonListener;
    private DialogInterface.OnClickListener neutralButtonListener;
    private Drawable icon;
    private View customView;
    private boolean cancelable = true;
    private DialogInterface.OnCancelListener cancelListener;
    private DialogInterface.OnDismissListener dismissListener;
    private DialogInterface.OnShowListener showListener;
    private DialogInterface.OnKeyListener keyListener;
    private CharSequence[] items;
    private int checkedItem = -1;
    private DialogInterface.OnClickListener itemsClickListener;
    private CharSequence[] multiChoiceItems;
    private boolean[] multiChoiceCheckedItems;
    private DialogInterface.OnMultiChoiceClickListener multiChoiceClickListener;
    private AdapterView.OnItemSelectedListener itemSelectedListener;
    private List<DialogInterface.OnClickListener> buttonListeners = new ArrayList<>();

    public MaterialDialogHelper(Context context) {
        this.context = context;
    }

    public MaterialDialogHelper setTitle(String title) {
        this.title = title;
        return this;
    }

    public MaterialDialogHelper setTitle(@StringRes int titleResId) {
        this.title = context.getString(titleResId);
        return this;
    }

    public MaterialDialogHelper setMessage(String message) {
        this.message = message;
        return this;
    }

    public MaterialDialogHelper setMessage(@StringRes int messageResId) {
        this.message = context.getString(messageResId);
        return this;
    }

    public MaterialDialogHelper setPositiveButton(String text, DialogInterface.OnClickListener listener) {
        this.positiveButtonText = text;
        this.positiveButtonListener = listener;
        return this;
    }

    public MaterialDialogHelper setPositiveButton(@StringRes int textResId, DialogInterface.OnClickListener listener) {
        this.positiveButtonText = context.getString(textResId);
        this.positiveButtonListener = listener;
        return this;
    }

    public MaterialDialogHelper setNegativeButton(String text, DialogInterface.OnClickListener listener) {
        this.negativeButtonText = text;
        this.negativeButtonListener = listener;
        return this;
    }

    public MaterialDialogHelper setNegativeButton(@StringRes int textResId, DialogInterface.OnClickListener listener) {
        this.negativeButtonText = context.getString(textResId);
        this.negativeButtonListener = listener;
        return this;
    }

    public MaterialDialogHelper setNeutralButton(String text, DialogInterface.OnClickListener listener) {
        this.neutralButtonText = text;
        this.neutralButtonListener = listener;
        return this;
    }

    public MaterialDialogHelper setNeutralButton(@StringRes int textResId, DialogInterface.OnClickListener listener) {
        this.neutralButtonText = context.getString(textResId);
        this.neutralButtonListener = listener;
        return this;
    }

    public MaterialDialogHelper setIcon(Drawable icon) {
        this.icon = icon;
        return this;
    }

    public MaterialDialogHelper setIcon(@DrawableRes int iconResId) {
        this.icon = context.getResources().getDrawable(iconResId);
        return this;
    }

    public MaterialDialogHelper setCustomView(View view) {
        this.customView = view;
        return this;
    }

    public MaterialDialogHelper setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public MaterialDialogHelper setOnCancelListener(DialogInterface.OnCancelListener listener) {
        this.cancelListener = listener;
        return this;
    }

    public MaterialDialogHelper setOnDismissListener(DialogInterface.OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public MaterialDialogHelper setOnShowListener(DialogInterface.OnShowListener listener) {
        this.showListener = listener;
        return this;
    }

    public MaterialDialogHelper setOnKeyListener(DialogInterface.OnKeyListener listener) {
        this.keyListener = listener;
        return this;
    }

    public MaterialDialogHelper setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
        this.items = items;
        this.itemsClickListener = listener;
        return this;
    }

    public MaterialDialogHelper setSingleChoiceItems(CharSequence[] items, int checkedItem, DialogInterface.OnClickListener listener) {
        this.items = items;
        this.checkedItem = checkedItem;
        this.itemsClickListener = listener;
        return this;
    }

    public MaterialDialogHelper setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
        this.multiChoiceItems = items;
        this.multiChoiceCheckedItems = checkedItems;
        this.multiChoiceClickListener = listener;
        return this;
    }

    public MaterialDialogHelper setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        this.itemSelectedListener = listener;
        return this;
    }

    public AlertDialog create() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        if (title != null) {
            builder.setTitle(title);
        }
        
        if (message != null) {
            builder.setMessage(message);
        }
        
        if (positiveButtonText != null) {
            builder.setPositiveButton(positiveButtonText, positiveButtonListener);
        }
        
        if (negativeButtonText != null) {
            builder.setNegativeButton(negativeButtonText, negativeButtonListener);
        }
        
        if (neutralButtonText != null) {
            builder.setNeutralButton(neutralButtonText, neutralButtonListener);
        }
        
        if (icon != null) {
            builder.setIcon(icon);
        }
        
        if (customView != null) {
            builder.setView(customView);
        }
        
        if (items != null && itemsClickListener != null) {
            if (checkedItem >= 0) {
                builder.setSingleChoiceItems(items, checkedItem, itemsClickListener);
            } else {
                builder.setItems(items, itemsClickListener);
            }
        }
        
        if (multiChoiceItems != null && multiChoiceClickListener != null) {
            builder.setMultiChoiceItems(multiChoiceItems, multiChoiceCheckedItems, multiChoiceClickListener);
        }
        
        builder.setCancelable(cancelable);
        
        if (cancelListener != null) {
            builder.setOnCancelListener(cancelListener);
        }
        
        if (dismissListener != null) {
            builder.setOnDismissListener(dismissListener);
        }
        
        if (keyListener != null) {
            builder.setOnKeyListener(keyListener);
        }
        
        AlertDialog dialog = builder.create();
        
        if (showListener != null) {
            dialog.setOnShowListener(showListener);
        }
        
        return dialog;
    }

    public void show() {
        create().show();
    }

    public static MaterialDialogHelper with(Context context) {
        return new MaterialDialogHelper(context);
    }

    public static void showSimpleDialog(Context context, String title, String message) {
        new MaterialDialogHelper(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    public static void showConfirmDialog(Context context, String title, String message, 
                                        DialogInterface.OnClickListener confirmListener) {
        new MaterialDialogHelper(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确认", confirmListener)
                .setNegativeButton("取消", null)
                .show();
    }
}