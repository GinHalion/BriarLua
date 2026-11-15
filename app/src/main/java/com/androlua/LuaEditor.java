package com.androlua;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.myopicmobile.textwarrior.android.FreeScrollingTextField;
import com.myopicmobile.textwarrior.android.OnSelectionChangedListener;
import com.myopicmobile.textwarrior.android.TrackpadNavigationMethod;
import com.myopicmobile.textwarrior.android.YoyoNavigationMethod;
import com.myopicmobile.textwarrior.common.ColorScheme;
import com.myopicmobile.textwarrior.common.Document;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.LanguageLua;
import com.myopicmobile.textwarrior.common.Lexer;
import com.myopicmobile.textwarrior.common.LinearSearchStrategy;
import com.myopicmobile.textwarrior.common.PackageUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_COPY;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CUT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT;

public class LuaEditor extends FreeScrollingTextField {

    private Document _inputtingDoc;
    private boolean _isWordWrap;
    private Context mContext;
    private String _lastSelectedFile;
    public boolean enableErrMsg = true;
    private String fontDir;

    public interface SelectionListener {
        void onSelectionChanged(boolean active, int selStart, int selEnd);
    }

    private SelectionListener _luaSelectionListener;

    public interface SearchListener {
        void onSearchResult(String keyword, boolean found,
                            int selStart, int selEnd, int matchIndex, int matchCount);
    }

    private SearchListener _luaSearchListener;

    public interface SearchLauncher {
        void onOpenSearchBar();
    }

    private SearchLauncher _searchLauncher;

    public void setCodeSearchLauncher(SearchLauncher l) {
        _searchLauncher = l;
    }

    public void openCodeSearch() {
        SearchLauncher l = _searchLauncher;
        if (l != null) {
            try {
                l.onOpenSearchBar();
                return;
            } catch (Throwable ignored) {
            }
        }
        startCodeSearch();
    }

    public void setSelectionMenuSuppressed(boolean suppressed) {
        try {
            if (_clipboardPanel != null) {
                _clipboardPanel.setSuppressed(suppressed);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onSelectionMenuSearch() {
        openCodeSearch();
    }

    private boolean _briarDark = false;

    public void setLuaSelectionListener(SelectionListener l) {
        _luaSelectionListener = l;
    }

    public void setLuaSearchListener(SearchListener l) {
        _luaSearchListener = l;
    }

    private int _index;
    private LinearSearchStrategy finder;
    private int idx;
    private String mKeyword;

    private ActionMode _codeSearchMode;
    private EditText _codeSearchInput;

    @SuppressLint("StaticFieldLeak")
    public LuaEditor(final Context context) {
        super(context);
        mContext = context;
        fontDir = new File(context.getFilesDir(), "fonts").getAbsolutePath();

        setTypeface(Typeface.MONOSPACE);
        File df = new File(fontDir, "default.ttf");
        if (df.exists()) setTypeface(Typeface.createFromFile(df));
        File bf = new File(fontDir, "bold.ttf");
        if (bf.exists()) setBoldTypeface(Typeface.createFromFile(bf));
        File tf = new File(fontDir, "italic.ttf");
        if (tf.exists()) setItalicTypeface(Typeface.createFromFile(tf));

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float size = TypedValue.applyDimension(2, BASE_TEXT_SIZE_PIXELS, dm);
        setTextSize((int) size);
        setShowLineNumbers(true);
        setHighlightCurrentRow(true);
        setWordWrap(false);
        setAutoIndentWidth(2);
        Lexer.setLanguage(LanguageLua.getInstance());

        if (isAccessibilityEnabled())
            setNavigationMethod(new TrackpadNavigationMethod(this));
        else
            setNavigationMethod(new YoyoNavigationMethod(this));

        TypedArray array = mContext.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorHighlight,
        });
        int textColor = array.getColor(1, 0xFF00FF);
        int textColorHighlight = array.getColor(2, 0xFF00FF);
        array.recycle();
        _briarDark = isNightMode();
        applyBriarLuaColors(getColorScheme(), _briarDark);
        setTextColor(textColor);
        setTextHighlightColor(textColorHighlight);

        PackageUtil.load(context);
        installCompositeSelectionListener();
    }

    private void applyBriarLuaColors(ColorScheme scheme, boolean dark) {
        if (dark) {
            scheme.setColor(ColorScheme.Colorable.FOREGROUND, 0xFFE1E2E9);
            scheme.setColor(ColorScheme.Colorable.KEYWORD, 0xFFC792EA);
            scheme.setColor(ColorScheme.Colorable.STRING, 0xFFC3E88D);
            scheme.setColor(ColorScheme.Colorable.LITERAL, 0xFFF78C6C);
            scheme.setColor(ColorScheme.Colorable.NAME, 0xFF82AAFF);
            scheme.setColor(ColorScheme.Colorable.COMMENT, 0xFF676E95);
            scheme.setColor(ColorScheme.Colorable.SECONDARY, 0xFF89DDFF);
            scheme.setColor(ColorScheme.Colorable.GLOBAL, 0xFFFFCB6B);
            scheme.setColor(ColorScheme.Colorable.UPVAL, 0xFFB2CCD6);
            scheme.setColor(ColorScheme.Colorable.LOCAL, 0xFFA5C8FF);
            scheme.setColor(ColorScheme.Colorable.LINE_HIGHLIGHT, 0x14FFFFFF);
            scheme.setColor(ColorScheme.Colorable.CARET_BACKGROUND, 0xFFA5C8FF);
            scheme.setColor(ColorScheme.Colorable.CARET_FOREGROUND, 0xFF111318);
            scheme.setColor(ColorScheme.Colorable.CARET_DISABLED, 0xFF666A75);
            scheme.setColor(ColorScheme.Colorable.NON_PRINTING_GLYPH, 0xFF565E6E);
            scheme.setColor(ColorScheme.Colorable.SELECTION_FOREGROUND, 0xFFFFFFFF);
        } else {
            scheme.setColor(ColorScheme.Colorable.FOREGROUND, 0xFF191C20);
            scheme.setColor(ColorScheme.Colorable.KEYWORD, 0xFF7C4DFF);
            scheme.setColor(ColorScheme.Colorable.STRING, 0xFF2E7D32);
            scheme.setColor(ColorScheme.Colorable.LITERAL, 0xFFC2185B);
            scheme.setColor(ColorScheme.Colorable.NAME, 0xFF1565C0);
            scheme.setColor(ColorScheme.Colorable.COMMENT, 0xFF8A8F98);
            scheme.setColor(ColorScheme.Colorable.SECONDARY, 0xFF00838F);
            scheme.setColor(ColorScheme.Colorable.GLOBAL, 0xFFB26A00);
            scheme.setColor(ColorScheme.Colorable.UPVAL, 0xFF5D4037);
            scheme.setColor(ColorScheme.Colorable.LOCAL, 0xFF3C608F);
            scheme.setColor(ColorScheme.Colorable.LINE_HIGHLIGHT, 0x14000000);
            scheme.setColor(ColorScheme.Colorable.CARET_BACKGROUND, 0xFF3C608F);
            scheme.setColor(ColorScheme.Colorable.CARET_FOREGROUND, 0xFFFFFFFF);
            scheme.setColor(ColorScheme.Colorable.CARET_DISABLED, 0xFF9AA0A6);
            scheme.setColor(ColorScheme.Colorable.NON_PRINTING_GLYPH, 0xFFBDC1C6);
            scheme.setColor(ColorScheme.Colorable.SELECTION_FOREGROUND, 0xFF000000);
        }
    }

    private boolean isNightMode() {
        try {
            return (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable e) {
            return false;
        }
    }

    public void setDark(boolean isDark) {
        _briarDark = isDark;
        applyBriarLuaColors(getColorScheme(), isDark);
        TypedArray array = mContext.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.textColorPrimary,
                android.R.attr.textColorHighlight,
        });
        int textColor = array.getColor(0, 0xFF00FF);
        int textColorHighlight = array.getColor(1, 0xFF00FF);
        array.recycle();
        setTextColor(textColor);
        setTextHighlightColor(textColorHighlight);
        respan();
        invalidate();
    }

    private void installCompositeSelectionListener() {
        final OnSelectionChangedListener panelLis = _selModeLis;
        setOnSelectionChangedListener(new OnSelectionChangedListener() {
            @Override
            public void onSelectionChanged(boolean active, int selStart, int selEnd) {
                if (panelLis != null) {
                    panelLis.onSelectionChanged(active, selStart, selEnd);
                }
                if (_luaSelectionListener != null) {
                    _luaSelectionListener.onSelectionChanged(active, selStart, selEnd);
                }
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (_index != 0 && right > 0) {
            moveCaret(_index);
            _index = 0;
        }
    }

    public void addNames(String[] names) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        String[] old = lang.getNames();
        String[] news = new String[old.length + names.length];
        System.arraycopy(old, 0, news, 0, old.length);
        System.arraycopy(names, 0, news, old.length, names.length);
        lang.setNames(news);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void addPackage(String pkg, String[] names) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        lang.addBasePackage(pkg, names);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void removePackage(String pkg) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        lang.removeBasePackage(pkg);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void setPanelBackgroundColor(int color) {
        _autoCompletePanel.setBackgroundColor(color);
    }

    public void setPanelTextColor(int color) {
        _autoCompletePanel.setTextColor(color);
    }

    public void setKeywordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.KEYWORD, color);
    }

    public void setUserwordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.LITERAL, color);
    }

    public void setBasewordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.NAME, color);
    }

    public void setStringColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.STRING, color);
    }

    public void setCommentColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.COMMENT, color);
    }

    public void setBackgroundColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.BACKGROUND, color);
    }

    public void setTextColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.FOREGROUND, color);
    }

    public void setTextHighlightColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.SELECTION_BACKGROUND, color);
    }

    public void setEnableDrawingErrMsg(boolean enable) {
        enableErrMsg = enable;
    }

    public void setNonPrintingCharVisibility(boolean enable) {
        super.setNonPrintingCharVisibility(enable);
    }

    public String getSelectedText() {
        return _hDoc.subSequence(getSelectionStart(), getSelectionEnd() - getSelectionStart()).toString();
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        final int filteredMetaState = event.getMetaState() & ~KeyEvent.META_CTRL_MASK;
        if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    selectAll();
                    return true;
                case KeyEvent.KEYCODE_X:
                    cut();
                    return true;
                case KeyEvent.KEYCODE_C:
                    copy();
                    return true;
                case KeyEvent.KEYCODE_V:
                    paste();
                    return true;
                case KeyEvent.KEYCODE_L:
                    format();
                    return true;
                case KeyEvent.KEYCODE_F:
                    openCodeSearch();
                    return true;
                case KeyEvent.KEYCODE_S:
                    openCodeSearch();
                    return true;
                case KeyEvent.KEYCODE_G:
                    gotoLine();
                    return true;
                case KeyEvent.KEYCODE_Z:
                    undo();
                    return true;
                case KeyEvent.KEYCODE_Y:
                    redo();
                    return true;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    public void gotoLine() {
        startGotoMode();
    }

    public void search() {
        openCodeSearch();
    }

    public void startGotoMode() {
        startActionMode(new ActionMode.Callback() {

            private int idx;
            private EditText edit;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.setTitle("转到");
                mode.setSubtitle(null);

                edit = new EditText(mContext) {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s.length() > 0) {
                            idx = 0;
                            _gotoLine();
                        }
                    }
                };

                edit.setSingleLine(true);
                edit.setInputType(2);
                edit.setImeOptions(2);
                edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView p1, int p2, KeyEvent p3) {
                        _gotoLine();
                        return true;
                    }
                });
                edit.setLayoutParams(new LinearLayout.LayoutParams(getWidth() / 3, -1));
                menu.add(0, 1, 0, "").setActionView(edit);
                menu.add(0, 2, 0, mContext.getString(android.R.string.ok));
                edit.requestFocus();
                return true;
            }

            private void _gotoLine() {
                String s = edit.getText().toString();
                if (s.isEmpty())
                    return;

                int l;
                try {
                    l = Integer.parseInt(s.trim());
                } catch (NumberFormatException e) {
                    return;
                }
                if (l < 1) {
                    l = 1;
                }
                if (l > _hDoc.getRowCount()) {
                    l = _hDoc.getRowCount();
                }
                gotoLine(l);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        break;
                    case 2:
                        _gotoLine();
                        break;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
            }
        });
    }

    public void startFindMode() {
        startActionMode(new ActionMode.Callback() {

            private LinearSearchStrategy finder;
            private int idx;
            private EditText edit;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.setTitle("搜索");
                mode.setSubtitle(null);

                edit = new EditText(mContext) {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s.length() > 0) {
                            idx = 0;
                            findNext();
                        }
                    }
                };
                edit.setSingleLine(true);
                edit.setImeOptions(3);
                edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView p1, int p2, KeyEvent p3) {
                        findNext();
                        return true;
                    }
                });
                edit.setLayoutParams(new LinearLayout.LayoutParams(getWidth() / 3, -1));
                menu.add(0, 1, 0, "").setActionView(edit);
                menu.add(0, 2, 0, mContext.getString(android.R.string.search_go));
                edit.requestFocus();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        break;
                    case 2:
                        findNext();
                        break;
                }
                return false;
            }

            private void findNext() {
                finder = new LinearSearchStrategy();
                String kw = edit.getText().toString();
                if (kw.isEmpty()) {
                    selectText(false);
                    return;
                }
                idx = finder.find(getText(), kw, idx, getText().length(), false, false);
                if (idx == -1) {
                    selectText(false);
                    Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
                    idx = 0;
                    return;
                }
                setSelection(idx, edit.getText().length());
                idx += edit.getText().length();
                moveCaret(idx);
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
            }
        });
    }

    @Override
    public void setWordWrap(boolean enable) {
        _isWordWrap = enable;
        super.setWordWrap(enable);
    }

    public DocumentProvider getText() {
        return createDocumentProvider();
    }

    public void setText(CharSequence c) {
        Document doc = new Document(this);
        doc.setWordWrap(_isWordWrap);
        doc.setText(c);
        setDocumentProvider(new DocumentProvider(doc));
    }

    public void insert(int idx, String text) {
        selectText(false);
        moveCaret(idx);
        paste(text);
    }

    public void setText(CharSequence c, boolean isRep) {
        replaceText(0, getLength() - 1, c.toString());
    }

    public void setSelection(int index) {
        selectText(false);
        if (!hasLayout())
            moveCaret(index);
        else
            _index = index;
    }

    public void gotoLine(int line) {
        if (line < 1) {
            line = 1;
        }
        if (line > _hDoc.getRowCount()) {
            line = _hDoc.getRowCount();
        }
        int i = getText().getLineOffset(line - 1);
        if (i < 0) {
            i = 0;
        }
        setSelection(i);
    }

    public int getCaretLine() {
        try {
            int pos = getCaretPosition();
            if (pos < 0) {
                pos = 0;
            }
            int line = _hDoc.findLineNumber(pos);
            if (line < 0) {
                line = 0;
            }
            return line + 1;
        } catch (Throwable e) {
            return 1;
        }
    }

    public void undo() {
        DocumentProvider doc = createDocumentProvider();
        int newPosition = doc.undo();

        if (newPosition >= 0) {
            setEdited(true);
            respan();
            selectText(false);
            moveCaret(newPosition);
            invalidate();
        }
    }

    public void redo() {
        DocumentProvider doc = createDocumentProvider();
        int newPosition = doc.redo();

        if (newPosition >= 0) {
            setEdited(true);
            respan();
            selectText(false);
            moveCaret(newPosition);
            invalidate();
        }
    }

    public String getFilePath() {
        return _lastSelectedFile;
    }

    public void open(String filename) throws IOException {
        _lastSelectedFile = filename;
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8));
        StringBuilder buf = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null)
                buf.append(line).append("\n");
        } finally {
            reader.close();
        }
        if (buf.length() > 1)
            buf.setLength(buf.length() - 1);
        setText(buf);
    }

    public boolean save() throws IOException {
        return save(_lastSelectedFile);
    }

    public boolean save(String filename) throws IOException {
        if (filename == null)
            return true;
        File outputFile = new File(filename);

        if (outputFile.exists()) {
            if (!outputFile.canWrite()) {
                return false;
            }
        }

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8));
        try {
            writer.write(getText().toString());
        } finally {
            writer.close();
        }
        return true;
    }

    public boolean findNext(String keyword) {
        if (!keyword.equals(mKeyword)) {
            mKeyword = keyword;
            idx = 0;
        }
        finder = new LinearSearchStrategy();
        String kw = mKeyword;
        if (kw.isEmpty()) {
            selectText(false);
            return false;
        }
        idx = finder.find(getText(), kw, idx, getText().length(), false, false);
        if (idx == -1) {
            selectText(false);
            Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            idx = 0;
            return false;
        }
        setSelection(idx, mKeyword.length());
        idx += mKeyword.length();
        moveCaret(idx);
        return true;
    }


    public void startCodeSearch() {
        try {
            selectText(false);
        } catch (Throwable ignored) {
        }
        if (_codeSearchMode != null) {
            if (_codeSearchInput != null) {
                _codeSearchInput.requestFocus();
                _codeSearchInput.selectAll();
            }
            return;
        }
        startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                _codeSearchMode = mode;
                mode.setTitle("搜索");
                mode.setSubtitle(null);

                _codeSearchInput = new EditText(mContext);
                _codeSearchInput.setSingleLine(true);
                _codeSearchInput.setHint("搜索内容");
                _codeSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                _codeSearchInput.setLayoutParams(
                        new LinearLayout.LayoutParams(Math.max(getWidth() / 3, dp(120)), -1));
                _codeSearchInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        String kw = s.toString();
                        if (!kw.isEmpty()) {
                            doCodeSearchEx(kw, true, true, true, false, false, false, true);
                        }
                    }
                });
                _codeSearchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        searchCodeNext(v.getText().toString());
                        return true;
                    }
                });

                menu.add(0, 1, 0, "").setActionView(_codeSearchInput)
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(0, 2, 0, "上一个").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(0, 3, 0, "下一个").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
                _codeSearchInput.requestFocus();
                _codeSearchInput.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputMethodManager imm = (InputMethodManager) mContext
                                    .getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null && _codeSearchInput != null) {
                                imm.showSoftInput(_codeSearchInput, InputMethodManager.SHOW_IMPLICIT);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                String kw = _codeSearchInput == null ? "" : _codeSearchInput.getText().toString();
                switch (item.getItemId()) {
                    case 2:
                        searchCodePrev(kw);
                        return true;
                    case 3:
                        searchCodeNext(kw);
                        return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                _codeSearchMode = null;
                _codeSearchInput = null;
                try {
                    setSelection(getCaretPosition(), 0);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public void stopCodeSearch() {
        if (_codeSearchMode != null) {
            _codeSearchMode.finish();
        }
    }

    public boolean searchCodeNext(String keyword) {
        return doCodeSearchEx(keyword, true, false, true, false, false, false, true);
    }

    public boolean searchCodePrev(String keyword) {
        return doCodeSearchEx(keyword, false, false, true, false, false, false, true);
    }

    public int searchCodeCount(String keyword) {
        return searchCodeCount(keyword, true, false, false);
    }

    public boolean searchCodeNext(String keyword, boolean caseSensitive, boolean wholeWord, boolean regex) {
        return doCodeSearchEx(keyword, true, false, caseSensitive, wholeWord, regex, true, true);
    }

    public boolean searchCodePrev(String keyword, boolean caseSensitive, boolean wholeWord, boolean regex) {
        return doCodeSearchEx(keyword, false, false, caseSensitive, wholeWord, regex, true, true);
    }

    public boolean searchCodeInput(String keyword, boolean caseSensitive, boolean wholeWord, boolean regex) {
        return doCodeSearchEx(keyword, true, true, caseSensitive, wholeWord, regex, true, false);
    }

    public int searchCodeCount(String keyword, boolean caseSensitive, boolean wholeWord, boolean regex) {
        Pattern p = compileCodeSearchPattern(keyword, caseSensitive, wholeWord, regex);
        if (p == null) {
            return 0;
        }
        return findCodeSearchMatches(p, null).size();
    }

    public boolean replaceCode(String keyword, String replacement,
                               boolean caseSensitive, boolean wholeWord, boolean regex) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        String repl = (replacement == null) ? "" : replacement;
        Pattern p = compileCodeSearchPattern(keyword, caseSensitive, wholeWord, regex);
        if (p == null) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            return false;
        }
        String text = codeSearchText();
        int searchable = codeSearchableLength(text);
        if (searchable <= 0) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            return false;
        }
        List<int[]> matches = findCodeSearchMatches(p, text);
        if (matches.isEmpty()) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            return false;
        }
        int[] sel = codeSearchSelection();
        int[] current = null;
        for (int[] m : matches) {
            if (m[0] == sel[0] && m[1] == sel[1]) {
                current = m;
                break;
            }
        }
        if (current == null) {
            return doCodeSearchEx(keyword, true, false, caseSensitive, wholeWord, regex, true, true);
        }
        int at = current[0];
        try {
            if (_clipboardPanel != null) {
                _clipboardPanel.markSilentSelection();
            }
            setSelection(at, current[1] - current[0]);
            paste(repl);
        } catch (Throwable e) {
            return false;
        }
        List<int[]> after = findCodeSearchMatches(p, null);
        if (after.isEmpty()) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            return true;
        }
        int from = at + repl.length();
        int[] target = null;
        for (int[] m : after) {
            if (m[0] >= from) {
                target = m;
                break;
            }
        }
        if (target == null) {
            target = after.get(0);
        }
        selectCodeSearchMatch(target);
        int idx = indexOfMatch(after, target);
        notifySearchResult(keyword, true, target[0], target[1], idx, after.size());
        return true;
    }

    public int replaceCodeAll(String keyword, String replacement,
                              boolean caseSensitive, boolean wholeWord, boolean regex) {
        if (keyword == null || keyword.isEmpty()) {
            return 0;
        }
        String repl = (replacement == null) ? "" : replacement;
        Pattern p = compileCodeSearchPattern(keyword, caseSensitive, wholeWord, regex);
        if (p == null) {
            return 0;
        }
        String text = codeSearchText();
        int searchable = codeSearchableLength(text);
        if (searchable <= 0) {
            return 0;
        }
        List<int[]> matches = findCodeSearchMatches(p, text);
        if (matches.isEmpty()) {
            return 0;
        }
        StringBuilder sb = new StringBuilder(text.length() + repl.length() * matches.size());
        int pos = 0;
        for (int[] m : matches) {
            sb.append(text, pos, m[0]);
            sb.append(repl);
            pos = m[1];
        }
        sb.append(text, pos, searchable);
        int oldCaret = getCaretPosition();
        try {
            selectText(false);
            setText(sb, true);
        } catch (Throwable e) {
            return 0;
        }
        int newLen = sb.length();
        int newCaret = oldCaret;
        if (newCaret > newLen) {
            newCaret = newLen;
        }
        if (newCaret < 0) {
            newCaret = 0;
        }
        try {
            moveCaret(newCaret);
        } catch (Throwable ignored) {
        }
        return matches.size();
    }

    public String searchCodeRegexError(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        try {
            Pattern.compile(keyword);
            return null;
        } catch (PatternSyntaxException e) {
            String msg = e.getMessage();
            return msg == null ? "正则表达式无效" : msg;
        }
    }

    public void clearCodeSearchSelection() {
        try {
            selectText(false);
        } catch (Throwable ignored) {
        }
    }


    private String codeSearchText() {
        try {
            String t = getText().toString();
            return t == null ? "" : t;
        } catch (Throwable e) {
            return "";
        }
    }

    private static int codeSearchableLength(String text) {
        return Math.max(0, text.length() - 1);
    }

    private Pattern compileCodeSearchPattern(String keyword, boolean caseSensitive,
                                             boolean wholeWord, boolean regex) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        int flags = Pattern.MULTILINE;
        if (!caseSensitive) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        String body;
        if (regex) {
            body = keyword;
        } else {
            body = Pattern.quote(keyword);
            if (wholeWord) {
                String pre = isWordChar(keyword.charAt(0)) ? "(?<![\\w])" : "";
                String post = isWordChar(keyword.charAt(keyword.length() - 1)) ? "(?![\\w])" : "";
                body = pre + body + post;
            }
        }
        try {
            return Pattern.compile(body, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private List<int[]> findCodeSearchMatches(Pattern pattern, String text) {
        List<int[]> list = new ArrayList<>();
        if (pattern == null) {
            return list;
        }
        if (text == null) {
            text = codeSearchText();
        }
        int searchable = codeSearchableLength(text);
        if (searchable <= 0) {
            return list;
        }
        try {
            Matcher m = pattern.matcher(text).region(0, searchable);
            while (m.find()) {
                if (m.end() > m.start()) {
                    list.add(new int[]{m.start(), m.end()});
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    private int[] codeSearchSelection() {
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        if (selStart > selEnd) {
            int t = selStart;
            selStart = selEnd;
            selEnd = t;
        }
        if (selStart < 0) {
            selStart = 0;
        }
        int searchable = codeSearchableLength(codeSearchText());
        if (selEnd > searchable) {
            selEnd = searchable;
        }
        if (selStart > searchable) {
            selStart = searchable;
        }
        return new int[]{selStart, selEnd};
    }

    private void selectCodeSearchMatch(int[] m) {
        try {
            if (_clipboardPanel != null) {
                _clipboardPanel.markSilentSelection();
            }
            setSelection(m[0], m[1] - m[0]);
        } catch (Throwable ignored) {
        }
    }

    private static int indexOfMatch(List<int[]> matches, int[] target) {
        for (int i = 0; i < matches.size(); i++) {
            int[] m = matches.get(i);
            if (m[0] == target[0] && m[1] == target[1]) {
                return i + 1;
            }
        }
        return 1;
    }

    private boolean doCodeSearchEx(String keyword, boolean forward, boolean fromSelectionStart,
                                   boolean caseSensitive, boolean wholeWord, boolean regex,
                                   boolean quiet, boolean wrap) {
        if (keyword == null || keyword.isEmpty()) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            if (!quiet) {
                Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        Pattern p = compileCodeSearchPattern(keyword, caseSensitive, wholeWord, regex);
        if (p == null) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            if (!quiet) {
                Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        String text = codeSearchText();
        int searchable = codeSearchableLength(text);
        if (searchable <= 0) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            if (!quiet) {
                Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        List<int[]> matches = findCodeSearchMatches(p, text);
        if (matches.isEmpty()) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            if (!quiet) {
                Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        int[] sel = codeSearchSelection();
        int selStart = sel[0];
        int selEnd = sel[1];
        int caret = getCaretPosition();

        int[] target = null;
        if (forward) {
            int from;
            if (fromSelectionStart) {
                from = selStart;
            } else if (selEnd > selStart) {
                from = selEnd;
            } else {
                from = caret;
            }
            for (int[] m : matches) {
                if (m[0] >= from) {
                    target = m;
                    break;
                }
            }
            if (target == null && wrap) {
                target = matches.get(0);
            }
        } else {
            int from;
            if (fromSelectionStart) {
                from = selStart;
            } else if (selEnd > selStart) {
                from = selStart;
            } else {
                from = caret;
            }
            for (int i = matches.size() - 1; i >= 0; i--) {
                if (matches.get(i)[0] < from) {
                    target = matches.get(i);
                    break;
                }
            }
            if (target == null && wrap) {
                target = matches.get(matches.size() - 1);
            }
        }

        if (target == null) {
            notifySearchResult(keyword, false, -1, -1, 0, 0);
            if (!quiet) {
                Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        selectCodeSearchMatch(target);
        int idx = indexOfMatch(matches, target);
        notifySearchResult(keyword, true, target[0], target[1], idx, matches.size());
        return true;
    }

    private void notifySearchResult(String keyword, boolean found,
                                    int selStart, int selEnd, int matchIndex, int matchCount) {
        SearchListener l = _luaSearchListener;
        if (l != null) {
            try {
                l.onSearchResult(keyword, found, selStart, selEnd, matchIndex, matchCount);
            } catch (Throwable ignored) {
            }
        }
    }

    private int dp(float v) {
        return (int) (v * mContext.getResources().getDisplayMetrics().density + 0.5f);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                switch (arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                        moveCaretDown();
                        break;
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
                        moveCaretRight();
                        break;
                }
                return true;
            case ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                switch (arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                        moveCaretUp();
                        break;
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
                        moveCaretLeft();
                        break;
                }
                return true;
            case ACTION_SET_SELECTION:
                if (arguments == null)
                    return true;
                int start = arguments.getInt(ACTION_ARGUMENT_SELECTION_START_INT, 0);
                int end = arguments.getInt(ACTION_ARGUMENT_SELECTION_END_INT, 0);
                boolean sel = arguments.getBoolean(ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false);
                if (sel)
                    setSelectionRange(start, end);
                else
                    setSelection(start, end);
                return true;
            case ACTION_SET_TEXT:
                selectText(false);
                if (arguments == null)
                    setText("", true);
                else
                    setText(arguments.getCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE), true);
                return true;
            case ACTION_PASTE:
                paste();
                return true;
            case ACTION_COPY:
                copy();
                return true;
            case ACTION_CUT:
                cut();
                return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
}