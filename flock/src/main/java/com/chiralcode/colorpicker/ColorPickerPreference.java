package com.chiralcode.colorpicker;


import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class ColorPickerPreference extends DialogPreference {

    public static final int DEFAULT_COLOR = Color.WHITE;

    private int selectedColor;
    private ColorPicker colorPickerView;

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateDialogView() {

        RelativeLayout relativeLayout = new RelativeLayout(getContext());
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        colorPickerView = new ColorPicker(getContext());
        colorPickerView.setId(1);

        relativeLayout.addView(colorPickerView, layoutParams);

        return relativeLayout;

    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        colorPickerView.setColor(selectedColor);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setTitle(null); // remove dialog title to get more space for color picker
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult && shouldPersist()) {
            if (callChangeListener(colorPickerView.getColor())) {
                selectedColor = colorPickerView.getColor();
                persistInt(selectedColor);
            }
        }
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        selectedColor = restoreValue ? getPersistedInt(DEFAULT_COLOR) : (Integer) defaultValue;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, DEFAULT_COLOR);
    }

}
