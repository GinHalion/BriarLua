package com.briarlua.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Keep;

import java.lang.ref.WeakReference;

@Keep
public final class UiUtil {

    private UiUtil() {}

    public static boolean isNightMode(Activity activity) {
        int currentNightMode = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getStatusBarHeight(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int resourceId = context.getResources()
                    .getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return context.getResources().getDimensionPixelSize(resourceId);
            }
        }
        return 0;
    }

    public static int getActionBarSize(Context context) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(
                    typedValue.data,
                    context.getResources().getDisplayMetrics());
        }
        return 0;
    }

    public static void keepAboveKeyboard(final View target, final View other) {
        if (target == null) {
            return;
        }
        final WeakReference<View> targetRef = new WeakReference<>(target);
        final WeakReference<View> otherRef = (other == null || other == target)
                ? null : new WeakReference<>(other);
        final Rect frame = new Rect();
        final int[] lastBottom = {Integer.MIN_VALUE};

        target.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                View t = targetRef.get();
                if (t == null || !t.isAttachedToWindow()) {
                    return;
                }
                View root = t.getRootView();
                if (root == null) {
                    return;
                }
                try {
                    root.getWindowVisibleDisplayFrame(frame);
                } catch (Throwable e) {
                    return;
                }
                int rootH = root.getHeight();
                if (rootH <= 0) {
                    return;
                }
                int bottom = rootH - frame.bottom;
                if (bottom < 0) {
                    bottom = 0;
                }
                if (bottom == lastBottom[0]) {
                    return;
                }
                lastBottom[0] = bottom;
                t.setPadding(t.getPaddingLeft(), t.getPaddingTop(), t.getPaddingRight(), bottom);
                View v = otherRef == null ? null : otherRef.get();
                if (v != null && v.isAttachedToWindow()) {
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
                }
            }
        });
    }
}