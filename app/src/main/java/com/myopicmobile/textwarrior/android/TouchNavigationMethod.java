package com.myopicmobile.textwarrior.android;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.myopicmobile.textwarrior.common.ColorScheme;
import com.myopicmobile.textwarrior.common.DocumentProvider;


public class TouchNavigationMethod extends GestureDetector.SimpleOnGestureListener {
    private final static Rect _caretBloat = new Rect(0, 0, 0, 0);
    protected static int SCROLL_EDGE_SLOP = 10;
    protected static int TOUCH_SLOP = 12;
    protected FreeScrollingTextField _textField;
    protected boolean _isCaretTouched = false;
    private GestureDetector _gestureDetector;
    private float lastDist;
    private float lastX;
    private float lastY;
    private float lastSize;
    private int fling;
    private boolean _fastScroll;

    public TouchNavigationMethod(FreeScrollingTextField textField) {
        _textField = textField;
        _gestureDetector = new GestureDetector(textField.getContext(), this);
        _gestureDetector.setIsLongpressEnabled(true);
    }

    @SuppressWarnings("unused")
    private TouchNavigationMethod() {
    }

    @Override
    public boolean onDown(MotionEvent e) {
        int x = screenToViewX((int) e.getX());
        int y = screenToViewY((int) e.getY());
        _isCaretTouched = isNearChar(x, y, _textField.getCaretPosition());
        _fastScroll = x < _textField.getLeftOffset();
        if (_textField.isFlingScrolling()) {
            _textField.stopFlingScrolling();
        } else if (_textField.isSelectText()) {
            if (isNearChar(x, y, _textField.getSelectionStart())) {
                _textField.focusSelectionStart();
                _textField.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                _isCaretTouched = true;
                _textField.onSelectionHandleGrab();
            } else if (isNearChar(x, y, _textField.getSelectionEnd())) {
                _textField.focusSelectionEnd();
                _textField.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                _isCaretTouched = true;
                _textField.onSelectionHandleGrab();
            }
        }

        if (_isCaretTouched) {
            _textField.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }

        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (_textField.isAccessibilityEnabled()) {
            _textField.showIME(true);
            return true;
        }
        int x = screenToViewX((int) e.getX());
        int y = screenToViewY((int) e.getY());
        int charOffset = _textField.coordToCharIndex(x, y);

        if (_textField.isSelectText()) {
            int strictCharOffset = _textField.coordToCharIndexStrict(x, y);
            if (_textField.inSelectionRange(strictCharOffset) ||
                    isNearChar(x, y, _textField.getSelectionStart()) ||
                    isNearChar(x, y, _textField.getSelectionEnd())) {
            } else {
                _textField.selectText(false);
                if (strictCharOffset >= 0) {
                    _textField.moveCaret(charOffset);
                }
            }
        } else {
            if (charOffset >= 0) {
                _textField.moveCaret(charOffset);
            }
        }
        boolean displayIME = true;
        if (displayIME) {
            _textField.showIME(true);
        }
        return true;
    }

    public boolean onUp(MotionEvent e) {
        _textField.stopAutoScrollCaret();
        _isCaretTouched = false;
        _fastScroll=false;
        lastDist = 0;
        fling = 0;
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {


        if (_isCaretTouched) {
            dragCaret(e2);
        } else if (e2.getPointerCount() == 1) {
            if (fling == 0)
                if (Math.abs(distanceX) > Math.abs(distanceY))
                    fling = 1;
                else
                    fling = -1;
            if (fling == 1)
                distanceY = 0;
            else if (fling == -1)
                distanceX = 0;
            if (_fastScroll)
                distanceY *= _textField.getMaxScrollY() / _textField.getHeight();
            scrollView(distanceX, distanceY);

        }

        if ((e2.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            onUp(e2);
        }
        return true;
    }

    private void dragCaret(MotionEvent e) {
        if (!_textField.isSelectText() && isDragSelect()) {
            _textField.selectText(true);
        }

        int x = (int) e.getX() - _textField.getPaddingLeft();
        int y = (int) e.getY() - _textField.getPaddingTop();
        boolean scrolled = false;

        if (x < SCROLL_EDGE_SLOP) {
            scrolled = _textField.autoScrollCaret(FreeScrollingTextField.SCROLL_LEFT);
        } else if (x >= (_textField.getContentWidth() - SCROLL_EDGE_SLOP)) {
            scrolled = _textField.autoScrollCaret(FreeScrollingTextField.SCROLL_RIGHT);
        } else if (y < SCROLL_EDGE_SLOP) {
            scrolled = _textField.autoScrollCaret(FreeScrollingTextField.SCROLL_UP);
        } else if (y >= (_textField.getContentHeight() - SCROLL_EDGE_SLOP)) {
            scrolled = _textField.autoScrollCaret(FreeScrollingTextField.SCROLL_DOWN);
        }

        if (!scrolled) {
            _textField.stopAutoScrollCaret();
            int newCaretIndex = _textField.coordToCharIndex(
                    screenToViewX((int) e.getX()),
                    screenToViewY((int) e.getY())
            );
            if (newCaretIndex >= 0) {
                _textField.moveCaret(newCaretIndex);
            }
        }
    }

    private void scrollView(float distanceX, float distanceY) {
        int newX = (int) distanceX + _textField.getScrollX();
        int newY = (int) distanceY + _textField.getScrollY();

        int maxWidth = Math.max(_textField.getMaxScrollX(),
                _textField.getScrollX());
        if (newX > maxWidth) {
            newX = maxWidth;
        } else if (newX < 0) {
            newX = 0;
        }

        int maxHeight = Math.max(_textField.getMaxScrollY(),
                _textField.getScrollY());
        if (newY > maxHeight) {
            newY = maxHeight;
        } else if (newY < 0) {
            newY = 0;
        }
        _textField.smoothScrollTo(newX, newY);

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {
        if (!_isCaretTouched) {

            if (fling == 1)
                velocityY = 0;
            else if (fling == -1)
                velocityX = 0;

            _textField.flingScroll((int) -velocityX * 2, (int) -velocityY * 2);
        }
        onUp(e2);
        return true;
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private boolean onTouchZoom(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (e.getPointerCount() == 2) {
                if (lastDist == 0) {
                    float x = e.getX(0) - e.getX(1);
                    float y = e.getY(0) - e.getY(1);
                    lastDist = (float) Math.sqrt(x * x + y * y);
                    lastX = (e.getX(0) + e.getX(1)) / 2;
                    lastY = (e.getY(0) + e.getY(1)) / 2;
                    lastSize = _textField.getTextSize();
                }

                float dist = spacing(e);
                if (lastDist != 0) {
                    _textField.setTextSize((int) (lastSize * (dist / lastDist)));
                }
                return true;
            }
        }
        lastDist = 0;
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        onTouchZoom(event);
        boolean handled = _gestureDetector.onTouchEvent(event);
        if (!handled
                && (event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            handled = onUp(event);
        }
        return handled;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        onDoubleTap(e);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        _isCaretTouched = true;
        int x = screenToViewX((int) e.getX());
        int y = screenToViewY((int) e.getY());
        int charOffset = _textField.coordToCharIndex(x, y);

        if (_textField.isSelectText() && _textField.inSelectionRange(charOffset)) {
            DocumentProvider doc = _textField.createDocumentProvider();
            int line = doc.findLineNumber(charOffset);
            int start = doc.getLineOffset(line);
            int end = doc.getLineOffset(line + 1) - 1;
            _textField.setSelectionRange(start, end - start);
        } else {
            if (charOffset >= 0) {
                _textField.moveCaret(charOffset);
                DocumentProvider doc = _textField.createDocumentProvider();
                int start;
                int end;
                for (start = charOffset; start >= 0; start--) {
                    char c = doc.charAt(start);
                    if (!Character.isJavaIdentifierPart(c))
                        break;
                }
                if (start != charOffset)
                    start++;
                for (end = charOffset; end >= 0; end++) {
                    char c = doc.charAt(end);
                    if (!Character.isJavaIdentifierPart(c))
                        break;
                }
                _textField.selectText(true);
                _textField.setSelectionRange(start, end - start);
            }
        }
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    void onPause() {
    }

    void onResume() {
    }

    public void onTextDrawComplete(Canvas canvas) {
    }

    public void onColorSchemeChanged(ColorScheme colorScheme) {
    }



    public void onChiralityChanged(boolean isRightHanded) {
    }

    public Rect getCaretBloat() {
        return _caretBloat;
    }

    @SuppressWarnings("deprecation")
    final protected int getPointerId(MotionEvent e) {
        return (e.getAction() & MotionEvent.ACTION_POINTER_ID_MASK)
                >> MotionEvent.ACTION_POINTER_ID_SHIFT;
    }

    final protected int screenToViewX(int x) {
        return x - _textField.getPaddingLeft() + _textField.getScrollX();
    }

    final protected int screenToViewY(int y) {
        return y - _textField.getPaddingTop() + _textField.getScrollY();
    }

    final public boolean isRightHanded() {
        return true;
    }

    private boolean isDragSelect() {
        return false;
    }

    public boolean isNearChar(int x, int y, int charOffset) {
        Rect bounds = _textField.getBoundingBox(charOffset);

        return (y >= (bounds.top - TOUCH_SLOP)
                && y < (bounds.bottom + TOUCH_SLOP)
                && x >= (bounds.left - TOUCH_SLOP)
                && x < (bounds.right + TOUCH_SLOP)
        );
    }
}
