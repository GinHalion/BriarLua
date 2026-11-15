package com.myopicmobile.textwarrior.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.appcompat.widget.TooltipCompat;

import com.google.android.material.color.MaterialColors;

public class ClipboardPanel {

    private final FreeScrollingTextField _textField;
    private final Context _context;
    private final float _density;

    private PopupWindow _popup;
    private LinearLayout _row;
    private int _bgColor;
    private int _fgColor;
    private int _dividerColor;
    private int _rippleResId;

    private boolean _suppressed = false;

    private boolean _silentSelection = false;

    private boolean _syncPending = false;
    private int _lastX = Integer.MIN_VALUE;
    private int _lastY = Integer.MIN_VALUE;

    public ClipboardPanel(FreeScrollingTextField textField) {
        _textField = textField;
        _context = textField.getContext();
        _density = _context.getResources().getDisplayMetrics().density;

        resolveThemeColors();

        _textField.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                hide();
            }
        });
    }

    private void resolveThemeColors() {
        _bgColor = resolveMaterialColor(
                "colorSurfaceContainerHigh", android.R.attr.colorBackground, 0xFFECE9F1);
        _fgColor = resolveMaterialColor(
                "colorOnSurface", android.R.attr.textColorPrimary, 0xFF1D1B20);
        _dividerColor = resolveMaterialColor(
                "colorOutlineVariant", android.R.attr.textColorSecondary, 0x33808080);
        TypedArray array = _context.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.selectableItemBackgroundBorderless,
        });
        _rippleResId = array.getResourceId(0, 0);
        array.recycle();
    }

    private int resolveMaterialColor(String materialAttr, int frameworkAttr, int fallback) {
        try {
            Integer attrId = null;
            try {
                java.lang.reflect.Field f =
                        com.google.android.material.R.attr.class.getField(materialAttr);
                attrId = (Integer) f.get(null);
            } catch (Throwable ignored) {
            }
            if (attrId != null) {
                return MaterialColors.getColor(_context, attrId, fallback);
            }
        } catch (Throwable ignored) {
        }
        try {
            TypedArray array = _context.getTheme().obtainStyledAttributes(new int[]{frameworkAttr});
            int color = array.getColor(0, fallback);
            array.recycle();
            return color;
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public void setSuppressed(boolean suppressed) {
        _suppressed = suppressed;
        if (suppressed) {
            hide();
        }
    }

    public boolean isSuppressed() {
        return _suppressed;
    }

    public void markSilentSelection() {
        _silentSelection = true;
        hide();
    }

    public void notifyUserSelection() {
        _silentSelection = false;
    }

    public boolean isSilentSelection() {
        return _silentSelection;
    }

    public Context getContext() {
        return _context;
    }

    public void show() {
        if (_suppressed || _silentSelection) {
            return;
        }
        if (_textField == null || _textField.getWidth() <= 0 || _textField.getHeight() <= 0) {
            return;
        }
        int[] pos = null;
        try {
            pos = computePosition();
        } catch (Throwable ignored) {
        }
        if (pos == null) {
            hide();
            return;
        }
        ensurePopup();
        if (_popup.isShowing()) {
            if (pos[0] != _lastX || pos[1] != _lastY) {
                _popup.update(pos[0], pos[1], -1, -1);
                _lastX = pos[0];
                _lastY = pos[1];
            }
        } else {
            _popup.showAtLocation(_textField, Gravity.NO_GRAVITY, pos[0], pos[1]);
            _lastX = pos[0];
            _lastY = pos[1];
        }
    }

    public void hide() {
        _lastX = Integer.MIN_VALUE;
        _lastY = Integer.MIN_VALUE;
        if (_popup != null && _popup.isShowing()) {
            _popup.dismiss();
        }
    }

    public boolean isShowing() {
        return _popup != null && _popup.isShowing();
    }

    public void requestSync() {
        if (_suppressed || _silentSelection || _syncPending || _textField == null) {
            return;
        }
        _syncPending = true;
        _textField.post(new Runnable() {
            @Override
            public void run() {
                _syncPending = false;
                try {
                    if (!_suppressed && !_silentSelection && _textField.isAttachedToWindow()
                            && _textField.isSelectText()) {
                        show();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public void onEditorScroll() {
        if (_suppressed || _silentSelection) {
            return;
        }
        if (isShowing() || _textField.isSelectText()) {
            requestSync();
        }
    }


    private int[] computePosition() {
        if (_textField._hDoc == null || _textField._hDoc.docLength() <= 0) {
            return null;
        }

        int selStart = _textField.getSelectionStart();
        int selEnd = _textField.getSelectionEnd();
        if (selStart < 0) selStart = 0;
        if (selEnd < 0) selEnd = 0;
        if (selStart > selEnd) {
            int t = selStart;
            selStart = selEnd;
            selEnd = t;
        }
        int maxChar = _textField._hDoc.docLength() - 1;
        if (selEnd > maxChar) selEnd = maxChar;
        if (selStart > maxChar) selStart = maxChar;
        if (selStart < 0 || selEnd < 0) {
            return null;
        }

        int rowH = _textField.rowHeight();
        int startRow = _textField._hDoc.findRowNumber(selStart);
        int endRow = _textField._hDoc.findRowNumber(selEnd);
        if (startRow < 0) startRow = 0;
        if (endRow < startRow) endRow = startRow;

        int selTopDoc = startRow * rowH;
        int selBottomDoc = (endRow + 1) * rowH;

        int selTopView = selTopDoc + _textField.getPaddingTop() - _textField.getScrollY();
        int selBottomView = selBottomDoc + _textField.getPaddingTop() - _textField.getScrollY();

        int viewH = _textField.getHeight();
        if (selTopView >= viewH || selBottomView <= 0) {
            return null;
        }

        int anchorChar = selStart;
        if (selTopView < 0) {
            anchorChar = selEnd;
        }

        int[] size = measurePopup();
        int popupW = size[0];
        int popupH = size[1];
        int gap = dp(10);

        int[] loc = new int[2];
        _textField.getLocationInWindow(loc);

        int anchorXView = _textField.getPaddingLeft() - _textField.getScrollX();
        try {
            Rect box = _textField.getBoundingBox(Math.min(anchorChar, maxChar));
            if (box != null && box.left >= 0) {
                anchorXView = box.left + _textField.getPaddingLeft() - _textField.getScrollX();
            }
        } catch (Throwable ignored) {
        }
        if (anchorXView < 0) anchorXView = 0;

        int y;
        if (selTopView - gap - popupH >= 0) {
            y = loc[1] + selTopView - gap - popupH;
        } else {
            int below = Math.min(selBottomView + gap, viewH);
            y = loc[1] + below;
        }

        int x = loc[0] + anchorXView - popupW / 2;
        int minLeft = loc[0] + dp(4);
        int maxRight = loc[0] + _textField.getWidth() - dp(4);
        if (x < minLeft) x = minLeft;
        if (x + popupW > maxRight) x = maxRight - popupW;
        if (x < minLeft) x = minLeft;
        if (x < 0) x = 0;

        return new int[]{x, y};
    }

    private int[] measurePopup() {
        ensurePopup();
        View content = _popup.getContentView();
        int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        content.measure(wSpec, hSpec);
        int w = content.getMeasuredWidth();
        int h = content.getMeasuredHeight();
        int maxW = _textField.getWidth() - dp(12);
        if (w > maxW) {
            w = maxW;
            _popup.setWidth(w);
        } else {
            _popup.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return new int[]{w, h};
    }


    private void ensurePopup() {
        if (_popup != null) {
            return;
        }

        HorizontalScrollView scroll = new HorizontalScrollView(_context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxW = _textField.getWidth() - dp(12);
                if (maxW > 0) {
                    widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                            maxW, View.MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setVerticalScrollBarEnabled(false);

        _row = new LinearLayout(_context);
        _row.setOrientation(LinearLayout.HORIZONTAL);
        int padV = dp(2);
        int padH = dp(3);
        _row.setPadding(padH, padV, padH, padV);
        scroll.addView(_row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(_bgColor);
        bg.setCornerRadius(dp(22));

        addButton("全选", "ic_select_all", new Runnable() {
            @Override
            public void run() {
                _textField.selectAll();
            }
        });
        addButton("剪切", "ic_content_cut", new Runnable() {
            @Override
            public void run() {
                _textField.cut();
                _textField.selectText(false);
                hide();
            }
        });
        addButton("复制", "ic_content_copy", new Runnable() {
            @Override
            public void run() {
                _textField.copy();
                _textField.selectText(false);
                hide();
            }
        });
        addButton("粘贴", "ic_content_paste", new Runnable() {
            @Override
            public void run() {
                _textField.paste();
                _textField.selectText(false);
                hide();
            }
        });
        addButton("搜索", "ic_search", new Runnable() {
            @Override
            public void run() {
                try {
                    _textField.onSelectionMenuSearch();
                } catch (Throwable ignored) {
                }
                hide();
            }
        });

        addDivider();

        addButton("关闭", "ic_close", new Runnable() {
            @Override
            public void run() {
                _textField.selectText(false);
                hide();
            }
        });

        _popup = new PopupWindow(scroll, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        _popup.setBackgroundDrawable(bg);
        _popup.setOutsideTouchable(false);
        _popup.setFocusable(false);
        _popup.setTouchable(true);
        _popup.setClippingEnabled(true);
        _popup.setElevation(dp(6));
    }

    private void addButton(String label, String iconName, final Runnable action) {
        int iconRes = _context.getResources().getIdentifier(iconName, "drawable",
                _context.getPackageName());
        View btn;
        if (iconRes != 0) {
            ImageView icon = new ImageView(_context);
            icon.setImageResource(iconRes);
            icon.setImageTintList(ColorStateList.valueOf(_fgColor));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn = icon;
        } else {
            android.widget.TextView text = new android.widget.TextView(_context);
            text.setText(label);
            text.setTextSize(12);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextColor(_fgColor);
            text.setGravity(Gravity.CENTER);
            btn = text;
        }
        int size = dp(40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMargins(dp(1), 0, dp(1), 0);
        btn.setLayoutParams(lp);
        if (_rippleResId != 0) {
            btn.setBackgroundResource(_rippleResId);
        }
        btn.setContentDescription(label);
        TooltipCompat.setTooltipText(btn, label);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    action.run();
                } catch (Throwable ignored) {
                    hide();
                }
            }
        });
        _row.addView(btn, lp);
    }

    private void addDivider() {
        View divider = new View(_context);
        GradientDrawable d = new GradientDrawable();
        d.setColor(_dividerColor);
        d.setSize(dp(1), dp(16));
        divider.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(16));
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMargins(dp(3), 0, dp(3), 0);
        _row.addView(divider, lp);
    }

    private int dp(float v) {
        return (int) (v * _density + 0.5f);
    }
}
