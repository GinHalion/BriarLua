package com.briarlua.utils;

import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.view.MotionEvent;
import android.widget.TextView;

public class MovementMethodUtil extends ArrowKeyMovementMethod {

    private static MovementMethodUtil sInstance;
    private static final Rect sLineBounds = new Rect();

    public static MovementMethod getInstance() {
        if (sInstance == null) {
            synchronized (MovementMethodUtil.class) {
                if (sInstance == null) {
                    sInstance = new MovementMethodUtil();
                }
            }
        }
        return sInstance;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
            int index = getCharIndexAt(widget, event);
            if (index != -1) {
                ClickableSpan[] links = buffer.getSpans(index, index, ClickableSpan.class);
                if (links != null && links.length > 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        links[0].onClick(widget);
                    } else {
                        Selection.setSelection(buffer,
                                buffer.getSpanStart(links[0]),
                                buffer.getSpanEnd(links[0]));
                    }
                    return true;
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    private int getCharIndexAt(TextView textView, MotionEvent event) {
        Layout layout = textView.getLayout();
        if (layout == null) return -1;

        CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) return -1;
        Spanned spanned = (Spanned) text;

        int x = (int) event.getX();
        int y = (int) event.getY();
        int x2 = x - textView.getTotalPaddingLeft() + textView.getScrollX();
        int y2 = y - textView.getTotalPaddingTop() + textView.getScrollY();

        if (y2 < 0 || y2 > layout.getHeight()) return -1;

        int line;
        try {
            line = layout.getLineForVertical(y2);
        } catch (Exception e) {
            return -1;
        }
        if (line < 0 || line >= layout.getLineCount()) return -1;

        synchronized (sLineBounds) {
            try {
                layout.getLineBounds(line, sLineBounds);
                if (!sLineBounds.contains(x2, y2)) return -1;
            } catch (Exception e) {
                return -1;
            }
        }

        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);
        if (lineEnd <= lineStart) return -1;

        CharSequence lineText = spanned.subSequence(lineStart, lineEnd);
        int lineLength = lineText.length();
        if (lineLength == 0) return -1;

        int margin = 0;
        LeadingMarginSpan[] marginSpans = spanned.getSpans(lineStart, lineEnd, LeadingMarginSpan.class);
        if (marginSpans != null) {
            for (LeadingMarginSpan span : marginSpans) {
                margin += span.getLeadingMargin(true);
            }
        }

        float x3 = x2 - margin;
        if (x3 < 0) return -1;

        TextPaint paint = textView.getPaint();
        float[] widths = new float[lineLength];
        paint.getTextWidths(lineText, 0, lineLength, widths);

        float defaultSize = textView.getTextSize();
        if (defaultSize > 0) {
            AbsoluteSizeSpan[] absSpans = spanned.getSpans(lineStart, lineEnd, AbsoluteSizeSpan.class);
            if (absSpans != null) {
                for (AbsoluteSizeSpan span : absSpans) {
                    int spanStart = spanned.getSpanStart(span);
                    int spanEnd = spanned.getSpanEnd(span);
                    float scaleFactor = span.getSize() / defaultSize;
                    int start = Math.max(lineStart, spanStart) - lineStart;
                    int end = Math.min(lineEnd, spanEnd) - lineStart;
                    if (start < 0) start = 0;
                    if (end > lineLength) end = lineLength;
                    for (int i = start; i < end; i++) {
                        widths[i] *= scaleFactor;
                    }
                }
            }
        }

        float endChar = 0.0f;
        for (int i = 0; i < lineLength; i++) {
            float startChar = endChar;
            endChar += widths[i];
            if (endChar >= x3) {
                int offset = (x3 - startChar < endChar - x3) ? i : i + 1;
                if (offset > lineLength) offset = lineLength;
                return lineStart + offset;
            }
        }
        return -1;
    }
}