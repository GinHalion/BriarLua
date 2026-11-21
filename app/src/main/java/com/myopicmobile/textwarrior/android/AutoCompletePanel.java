package com.myopicmobile.textwarrior.android;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import androidx.appcompat.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.myopicmobile.textwarrior.common.Flag;
import com.myopicmobile.textwarrior.common.Language;
import com.myopicmobile.textwarrior.common.LanguageNonProg;
import com.myopicmobile.textwarrior.common.Lexer;
import com.myopicmobile.textwarrior.common.LuaParser;
import com.myopicmobile.textwarrior.common.PackageUtil;

import java.util.ArrayList;

public class AutoCompletePanel {

    private final DisplayMetrics dm;
    private FreeScrollingTextField _textField;
    private Context _context;
    private static Language _globalLanguage = LanguageNonProg.getInstance();
    private ListPopupWindow _autoCompletePanel;
    private AutoCompletePanel.MyAdapter _adapter;
    private Filter _filter;

    private int _verticalOffset;

    private int _height;

    private int _horizontal;

    private CharSequence _constraint;

    private int _backgroundColor;

    private GradientDrawable gd;

    private int _textColor;
    private Drawable _background;
    private int _width;

    private boolean caseSensitive = false;

    public AutoCompletePanel(FreeScrollingTextField textField) {
        _textField = textField;
        _context = textField.getContext();
        dm = _context.getResources().getDisplayMetrics();
        initAutoCompletePanel();
    }

    public void setTextColor(int color) {
        _textColor = color;
        gd.setStroke(dp(1), color);
        _autoCompletePanel.setBackgroundDrawable(gd);
    }

    public void setBackgroundColor(int color) {
        _backgroundColor = color;
        gd.setColor(color);
        _autoCompletePanel.setBackgroundDrawable(gd);
    }

    public void setBackground(Drawable color) {
        _background = color;
    }

    private void initAutoCompletePanel() {
        _autoCompletePanel = new ListPopupWindow(_context);
        _autoCompletePanel.setAnchorView(_textField);
        _adapter = new MyAdapter(_context, android.R.layout.simple_list_item_1);
        _autoCompletePanel.setAdapter(_adapter);
        _filter = _adapter.getFilter();
        setHeight(300);

        TypedArray array =
                _context.getTheme()
                        .obtainStyledAttributes(
                                new int[] {
                                    android.R.attr.colorBackground, android.R.attr.textColorPrimary,
                                });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        int textColor = array.getColor(1, 0xFF00FF);
        array.recycle();
        gd = new GradientDrawable();
        gd.setColor(backgroundColor);
        gd.setCornerRadius(dp(5));
        gd.setStroke(dp(1), textColor);
        setTextColor(textColor);
        _autoCompletePanel.setBackgroundDrawable(gd);
        _autoCompletePanel.setOnItemClickListener(
                new OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
                        String text = ((TextView) p2).getText().toString();
                        int i = text.indexOf(":");
                        if (i > 0) {
                            text = text.substring(0, i - 1);
                        }
                        _textField.replaceText(
                                _textField.getCaretPosition() - _constraint.length(),
                                _constraint.length(),
                                text);
                        _adapter.abort();
                        dismiss();
                    }
                });
    }

    private int dp(float n) {
        int ret = (int) TypedValue.applyDimension(1, n, dm);
        if (ret <= 0) return (int) n;
        return ret;
    }

    public void setWidth(int width) {
        _autoCompletePanel.setWidth(width);
        ListView listView = _autoCompletePanel.getListView();
        if (listView != null) {
            ViewGroup.LayoutParams lp = listView.getLayoutParams();
            lp.width = width;
            listView.setLayoutParams(lp);
        }
        _width = width;
    }

    public void setHeight(int height) {

        if (_height != height) {
            _height = height;
            _autoCompletePanel.setHeight(height);
        }
    }

    private void setHorizontalOffset(int horizontal) {
        horizontal = Math.min(horizontal, _textField.getWidth() / 2);
        if (_horizontal != horizontal) {
            _horizontal = horizontal;
            _autoCompletePanel.setHorizontalOffset(horizontal);
        }
    }

    private void setVerticalOffset(int verticalOffset) {
        int max = 0 - _autoCompletePanel.getHeight();
        if (verticalOffset > max) {
            int target = _textField.getScrollY() + (verticalOffset - max);
            int maxScroll = _textField.getMaxScrollY();
            if (maxScroll < 0) {
                maxScroll = 0;
            }
            if (target > maxScroll) {
                target = maxScroll;
            }
            _textField.scrollBy(0, target - _textField.getScrollY());
            verticalOffset = max;
        }
        if (_verticalOffset != verticalOffset) {
            _verticalOffset = verticalOffset;
            _autoCompletePanel.setVerticalOffset(verticalOffset);
        }
    }

    public void update(CharSequence constraint) {
        _adapter.restart();
        _filter.filter(constraint);
    }

    public void show() {
        if (!_autoCompletePanel.isShowing()) {
            _autoCompletePanel.show();
            ListView list = _autoCompletePanel.getListView();
            if (list != null) {
                list.setFadingEdgeLength(0);
                if (_background != null) list.setBackground(_background);
            }
        }
    }

    public void dismiss() {
        if (_autoCompletePanel.isShowing()) {
            _autoCompletePanel.dismiss();
        }
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public synchronized void setLanguage(Language lang) {
        _globalLanguage = lang;
    }

    public static synchronized Language getLanguage() {
        return _globalLanguage;
    }

    class MyAdapter extends ArrayAdapter<CharSequence> implements Filterable {

        private int _h;
        private Flag _abort;

        private DisplayMetrics dm;

        public MyAdapter(android.content.Context context, int resource) {
            super(context, resource);
            _abort = new Flag();
            setNotifyOnChange(false);
            dm = context.getResources().getDisplayMetrics();
        }

        public void abort() {
            _abort.set();
        }

        private int dp(float n) {
            return (int) TypedValue.applyDimension(1, n, dm);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);

            view.setWidth(_width);
            view.setSingleLine(true);
            view.setTextColor(_textColor);
            return view;
        }

        public void restart() {
            _abort.clear();
        }

        public int getItemHeight() {
            if (_h != 0) return _h;

            LayoutInflater inflater =
                    (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            TextView item = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, null);
            item.measure(0, 0);
            _h = item.getMeasuredHeight();
            return _h;
        }

        @Override
        public Filter getFilter() {
            Filter filter =
                    new Filter() {

                        @Override
                        protected FilterResults performFiltering(CharSequence constraint) {
                            ArrayList<CharSequence> buf = new ArrayList<>();
                            String keyword = String.valueOf(constraint);
                            if (!caseSensitive) {
                                keyword = keyword.toLowerCase();
                            }
                            String[] ss = keyword.split("\\.");
                            keyword = ss[ss.length - 1];
                            if (keyword.endsWith(".")) {
                                _constraint = "";
                            } else {
                                _constraint = keyword;
                            }
                            int position =
                                    _textField.getCaretPosition()
                                            - keyword.length()
                                            - 1;
                            if (ss.length == 2) {
                                String pkg = ss[0];
                                if (_globalLanguage.isBasePackage(pkg)) {
                                    String[] keywords = _globalLanguage.getBasePackage(pkg);
                                    for (String k : keywords) {
                                        String kCompare = caseSensitive ? k : k.toLowerCase();
                                        if (kCompare.startsWith(keyword)) {
                                            buf.add(
                                                    getColorText(
                                                            k,
                                                            _textField
                                                                    .getColorScheme()
                                                                    .getTokenColor(Lexer.NAME)));
                                        }
                                    }
                                } else {
                                    buf.addAll(LuaParser.filterJava(pkg, keyword, position));
                                }
                            } else if (ss.length == 1) {
                                if (keyword.charAt(keyword.length() - 1) == '.') {
                                    if (_globalLanguage.isBasePackage(keyword)) {
                                        String[] keywords = _globalLanguage.getBasePackage(keyword);
                                        for (String k : keywords) {
                                            buf.add(
                                                    getColorText(
                                                            k,
                                                            _textField
                                                                    .getColorScheme()
                                                                    .getTokenColor(Lexer.NAME)));
                                        }
                                    } else {
                                        buf.addAll(LuaParser.filterJava(keyword, position));
                                    }
                                } else {
                                    buf.addAll(
                                            LuaParser.filterLocal(
                                                    keyword,
                                                    position,
                                                    _textField.getColorScheme()));
                                    ArrayList<String> usewords = LuaParser.getUserWord();
                                    for (String k : usewords) {
                                        String kCompare = caseSensitive ? k : k.toLowerCase();
                                        if (kCompare.startsWith(keyword)) {
                                            buf.add(
                                                    getColorText(
                                                            k,
                                                            _textField
                                                                    .getColorScheme()
                                                                    .getTokenColor(Lexer.LITERAL)));
                                        }
                                    }
                                    String[] keywords = _globalLanguage.getKeywords();
                                    for (String k : keywords) {
                                        String kCompare = caseSensitive ? k : k.toLowerCase();
                                        if (kCompare.startsWith(keyword)) {
                                            buf.add(
                                                    getColorText(
                                                            k,
                                                            _textField
                                                                    .getColorScheme()
                                                                    .getTokenColor(Lexer.KEYWORD)));
                                        }
                                    }
                                    keywords = _globalLanguage.getNames();
                                    for (String k : keywords) {
                                        String kCompare = caseSensitive ? k : k.toLowerCase();
                                        if (kCompare.startsWith(keyword)) {
                                            buf.add(
                                                    getColorText(
                                                            k,
                                                            _textField
                                                                    .getColorScheme()
                                                                    .getTokenColor(Lexer.NAME)));
                                        }
                                    }
                                }
                            }

                            buf.addAll(PackageUtil.filter(keyword));
                            FilterResults filterResults = new FilterResults();
                            filterResults.values = buf;
                            filterResults.count = buf.size();
                            return filterResults;
                        }

                        private CharSequence getColorText(String text, int color) {
                            SpannableString ss = new SpannableString(text);
                            ss.setSpan(new ForegroundColorSpan(color), 0, text.length(), 0);
                            return ss;
                        }

                        @Override
                        protected void publishResults(
                                CharSequence constraint, FilterResults results) {
                            if (results != null && results.count > 0 && !_abort.isSet()) {
                                MyAdapter.this.clear();
                                MyAdapter.this.addAll((ArrayList<CharSequence>) results.values);
                                int y =
                                        _textField.getPaintBaseline(_textField.getCaretRow())
                                                + _textField.rowHeight() / 2
                                                - _textField.getScrollY();
                                setHeight(getItemHeight() * Math.min(3, results.count));

                                setHorizontalOffset(
                                        _textField
                                                .getCharExtent(_textField.getCaretPosition())
                                                .getFirst()
                                                - _textField.getScrollX());
                                setVerticalOffset(
                                        y
                                                - _textField
                                                        .getHeight());
                                notifyDataSetChanged();
                                show();
                            } else {
                                notifyDataSetInvalidated();
                            }
                        }
                    };
            return filter;
        }
    }
}
