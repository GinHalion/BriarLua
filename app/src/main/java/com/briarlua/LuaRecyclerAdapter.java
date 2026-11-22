package com.briarlua;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.androlua.LuaContext;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.luajava.LuaException;
import com.luajava.LuaFunction;
import com.luajava.LuaJavaAPI;
import com.luajava.LuaObject;
import com.luajava.LuaState;
import com.luajava.LuaTable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

@Keep
public class LuaRecyclerAdapter extends RecyclerView.Adapter<LuaRecyclerAdapter.ViewHolder>
        implements Filterable {

    private static final String TAG = "LuaRecyclerAdapter";

    private static final String DEFAULT_IMAGE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15"
                    + " (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1";

    @Keep
    public interface OnItemClickListener {
        void onItemClick(LuaRecyclerAdapter adapter, View itemView, View view, int position);
    }

    @Keep
    public interface OnItemLongClickListener {
        boolean onItemLongClick(LuaRecyclerAdapter adapter, View itemView, View view, int position);
    }

    @Keep
    public interface AdapterInterface {
        void onBindViewHolder(ViewHolder holder, int position);
    }

    @Keep
    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {

        public final LuaTable<String, Object> views;
        public final LuaTable<String, Object> tag;
        private final WeakReference<LuaRecyclerAdapter> owner;
        private View attachedClickTarget;

        ViewHolder(View itemView, LuaTable<String, Object> views, LuaRecyclerAdapter adapter) {
            super(itemView);
            this.views = views;
            this.tag = views;
            this.owner = new WeakReference<>(adapter);
        }

        @Override
        public void onClick(View v) {
            LuaRecyclerAdapter a = owner.get();
            if (a != null) a.dispatchItemClick(this, v);
        }

        @Override
        public boolean onLongClick(View v) {
            LuaRecyclerAdapter a = owner.get();
            return a != null && a.dispatchItemLongClick(this, v);
        }

        View findView(String id) {
            if (id == null || views == null) return null;
            try {
                Object o = views.get(id);
                return o instanceof View ? (View) o : null;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private final LuaContext mContext;
    private final LuaState L;
    private LuaFunction<View> loadlayout;
    private LuaTable<Integer, LuaTable<String, Object>> mBaseData;
    private LuaTable<Integer, LuaTable<String, Object>> mData;

    private Object mLayout;
    private LuaTable<String, Object> mDefaultData;
    private LuaFunction<Animation> mAnimationUtil;

    private final WeakHashMap<View, Animation> mAnimCache = new WeakHashMap<>();

    private String mClickViewId;
    private boolean mNotifyOnChange = true;

    private OnItemClickListener mOnItemClickListener;
    private OnItemLongClickListener mOnItemLongClickListener;
    private AdapterInterface mAdapterInterface;

    private LuaFunction mLuaFilter;
    private CharSequence mPrefix;
    private ArrayFilter mFilter;
    private boolean mRefilterPending;
    private Map<String, String> mImageHeaders;

    private String mChildContainerId;
    private Object mChildLayout;
    private final WeakHashMap<View, LuaTable<String, Object>> mChildViewsCache = new WeakHashMap<>();
    private boolean mChildErrorReported;

    private View mPreCreatedView;
    private LuaTable<String, Object> mPreCreatedViews;

    private boolean mLayoutErrorReported;
    private volatile boolean mReleased;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());


    public LuaRecyclerAdapter(LuaContext context, Object layout) throws LuaException {
        this(context, null, layout);
    }

    public LuaRecyclerAdapter(LuaContext context,
                              LuaTable<Integer, LuaTable<String, Object>> data,
                              Object layout) throws LuaException {
        if (context == null) throw new LuaException("LuaRecyclerAdapter: context 不能为空");
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new LuaException("LuaRecyclerAdapter: 必须在主线程构造");
        }
        mContext = context;
        L = context.getLuaState();
        if (L == null) throw new LuaException("LuaRecyclerAdapter: LuaState 不可用");

        if (data == null) data = new LuaTable<>(L);
        mBaseData = data;
        mData = mBaseData;
        mLayout = layout;
        if (!(layout instanceof LuaObject || layout instanceof String)) {
            throw new LuaException("LuaRecyclerAdapter: 布局参数必须是布局表或布局模块名，"
                    + "用法 LuaRecyclerAdapter(activity, data, layout)");
        }

        LuaObject ll = L.getLuaObject("loadlayout");
        if (ll == null || ll.isNil()) {
            throw new LuaException("LuaRecyclerAdapter: 全局 loadlayout 不可用");
        }
        loadlayout = (LuaFunction<View>) ll.getFunction();

        LuaTable<String, Object> views = new LuaTable<>(L);
        try {
            View v = loadlayout.call(mLayout, views, ViewGroup.class);
            if (v != null) {
                mPreCreatedView = v;
                mPreCreatedViews = views;
            }
        } catch (LuaException e) {
            throw new LuaException("LuaRecyclerAdapter: 布局表无法创建视图\n" + e.getMessage());
        }
    }


    @Override
    public int getItemCount() {
        LuaTable<Integer, LuaTable<String, Object>> d = mData;
        if (mReleased || d == null) return 0;
        try {
            return d.length();
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return position + 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = null;
        LuaTable<String, Object> views = null;

        if (mPreCreatedView != null) {
            view = mPreCreatedView;
            views = mPreCreatedViews;
            mPreCreatedView = null;
            mPreCreatedViews = null;
        } else {
            views = new LuaTable<>(L);
            try {
                view = loadlayout.call(mLayout, views, ViewGroup.class);
            } catch (Throwable t) {
                if (!mLayoutErrorReported) {
                    mLayoutErrorReported = true;
                    sendError("onCreateViewHolder", t instanceof Exception
                            ? (Exception) t : new RuntimeException(t));
                }
            }
        }

        if (view == null) {
            view = new View(mContext.getContext());
        }

        return new ViewHolder(view, views, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (mReleased || mData == null || holder.views == null) return;

        LuaTable<String, Object> row = null;
        try {
            Object o = mData.get(position + 1);
            if (o instanceof LuaTable) row = (LuaTable<String, Object>) o;
        } catch (Throwable ignored) {
        }
        if (row == null) {
            Log.i(TAG, "row " + position + " is nil, skipped");
            return;
        }

        bindRow(row, holder.views, mChildContainerId);

        if (mChildContainerId != null) {
            bindChildren(holder, row);
        }

        resolveClickView(holder);

        applyAnimation(holder.itemView);

        AdapterInterface ai = mAdapterInterface;
        if (ai != null) {
            try {
                ai.onBindViewHolder(holder, position);
            } catch (Throwable t) {
                sendError("AdapterInterface.onBindViewHolder", asException(t));
            }
        }
    }

    private void bindRow(LuaTable<String, Object> row, LuaTable<String, Object> views, String skipKey) {
        Map<String, String> rowHeaders = extractRowHeaders(row, views);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.equals(skipKey)) continue;
            Object value = entry.getValue();
            Object o;
            try {
                o = views.get(key);
            } catch (Throwable t) {
                continue;
            }
            if (!(o instanceof View)) continue;
            View target = (View) o;
            try {
                if (mDefaultData != null) {
                    Object def = mDefaultData.get(key);
                    if (def != null) setHelper(target, def, rowHeaders);
                }
                setHelper(target, value, rowHeaders);
            } catch (Throwable t) {
                Log.w(TAG, "bind " + key + ": " + t);
            }
        }
    }

    private void bindChildren(ViewHolder holder, LuaTable<String, Object> row) {
        View cv = holder.findView(mChildContainerId);
        if (!(cv instanceof ViewGroup)) {
            if (!mChildErrorReported) {
                mChildErrorReported = true;
                sendError("setChildLayout", new IllegalStateException(
                        "条目布局中不存在 id 为 " + mChildContainerId + " 的 ViewGroup 容器"));
            }
            return;
        }
        ViewGroup container = (ViewGroup) cv;

        Object v = null;
        try {
            v = row.get(mChildContainerId);
        } catch (Throwable ignored) {
        }
        @SuppressWarnings("unchecked")
        LuaTable<Integer, LuaTable<String, Object>> children =
                v instanceof LuaTable ? (LuaTable<Integer, LuaTable<String, Object>>) v : null;

        int target = 0;
        if (children != null) {
            try {
                target = children.length();
            } catch (Throwable t) {
                target = 0;
            }
        }

        if (target <= 0) {
            container.removeAllViews();
            if (container.getVisibility() != View.GONE) container.setVisibility(View.GONE);
            return;
        }
        if (container.getVisibility() != View.VISIBLE) container.setVisibility(View.VISIBLE);

        int current = container.getChildCount();
        while (current > target) {
            View extra = container.getChildAt(current - 1);
            mChildViewsCache.remove(extra);
            container.removeViewAt(current - 1);
            current--;
        }
        while (current < target) {
            LuaTable<String, Object> views = new LuaTable<>(L);
            View child = null;
            try {
                child = loadlayout.call(mChildLayout, views, container.getClass());
            } catch (Throwable t) {
                if (!mChildErrorReported) {
                    mChildErrorReported = true;
                    sendError("setChildLayout.onCreate", asException(t));
                }
            }
            if (child == null) return;
            container.addView(child);
            mChildViewsCache.put(child, views);
            current++;
        }

        for (int i = 0; i < target; i++) {
            Object o = null;
            try {
                o = children.get(i + 1);
            } catch (Throwable ignored) {
            }
            if (!(o instanceof LuaTable)) continue;
            View child = container.getChildAt(i);
            LuaTable<String, Object> views = mChildViewsCache.get(child);
            if (views == null) continue;
            @SuppressWarnings("unchecked")
            LuaTable<String, Object> childRow = (LuaTable<String, Object>) o;
            bindRow(childRow, views, null);
        }
    }

    private View resolveClickView(ViewHolder holder) {
        View target = holder.findView(mClickViewId);
        if (target == null) target = holder.itemView;
        if (target != holder.attachedClickTarget) {
            holder.attachedClickTarget = target;
            target.setOnClickListener(holder);
            target.setOnLongClickListener(holder);
        }
        return target;
    }

    private void applyAnimation(View view) {
        LuaFunction<Animation> util = mAnimationUtil;
        if (util == null || view == null) return;
        try {
            Animation anim = mAnimCache.get(view);
            if (anim == null) {
                anim = util.call();
                if (anim != null) mAnimCache.put(view, anim);
            }
            if (anim != null) {
                view.clearAnimation();
                view.startAnimation(anim);
            }
        } catch (Throwable t) {
            sendError("setAnimation", asException(t));
        }
    }

    private void dispatchItemClick(ViewHolder holder, View view) {
        OnItemClickListener l = mOnItemClickListener;
        if (l != null) {
            try {
                l.onItemClick(this, holder.itemView, view, holder.getBindingAdapterPosition());
            } catch (Throwable t) {
                sendError("onItemClick", asException(t));
            }
        }
    }

    private boolean dispatchItemLongClick(ViewHolder holder, View view) {
        OnItemLongClickListener l = mOnItemLongClickListener;
        if (l == null) return false;
        try {
            return l.onItemLongClick(this, holder.itemView, view, holder.getBindingAdapterPosition());
        } catch (Throwable t) {
            sendError("onItemLongClick", asException(t));
            return false;
        }
    }


    public void add(LuaTable<String, Object> item) {
        runOnMain(() -> {
            if (checkReleased() || mBaseData == null || item == null) return;
            int oldLen = mBaseData.length();
            mBaseData.put(oldLen + 1, item);
            if (mNotifyOnChange) {
                if (mData == mBaseData) notifyItemInserted(oldLen);
                else scheduleRefilter();
            }
        });
    }

    public void addAll(LuaTable<Integer, LuaTable<String, Object>> items) {
        runOnMain(() -> {
            if (checkReleased() || mBaseData == null || items == null) return;
            int oldLen = mBaseData.length();
            int n = items.length();
            for (int i = 1; i <= n; i++) {
                Object v = items.get(i);
                if (v != null) mBaseData.put(oldLen + i, (LuaTable<String, Object>) v);
            }
            int added = mBaseData.length() - oldLen;
            if (mNotifyOnChange && added > 0) {
                if (mData == mBaseData) notifyItemRangeInserted(oldLen, added);
                else scheduleRefilter();
            }
        });
    }

    public void insert(int position, LuaTable<String, Object> item) {
        runOnMain(() -> {
            if (checkReleased() || mBaseData == null || item == null) return;
            int len = mBaseData.length();
            int luaIdx = clamp(position + 1, 1, len + 1);
            for (int i = len + 1; i > luaIdx; i--) mBaseData.put(i, mBaseData.get(i - 1));
            mBaseData.put(luaIdx, item);
            if (mNotifyOnChange) {
                if (mData == mBaseData) notifyItemInserted(luaIdx - 1);
                else scheduleRefilter();
            }
        });
    }

    public void remove(int position) {
        runOnMain(() -> {
            if (checkReleased() || mBaseData == null) return;
            int len = mBaseData.length();
            if (len == 0) return;
            int luaIdx = clamp(position + 1, 1, len);
            for (int i = luaIdx; i < len; i++) mBaseData.put(i, mBaseData.get(i + 1));
            mBaseData.put(len, null);
            if (mNotifyOnChange) {
                if (mData == mBaseData) notifyItemRemoved(luaIdx - 1);
                else scheduleRefilter();
            }
        });
    }

    public void update(int position, LuaTable<String, Object> item) {
        runOnMain(() -> {
            if (checkReleased() || mBaseData == null || item == null) return;
            int len = mBaseData.length();
            if (len == 0) return;
            int luaIdx = clamp(position + 1, 1, len);
            mBaseData.put(luaIdx, item);
            if (mNotifyOnChange) {
                if (mData == mBaseData) notifyItemChanged(luaIdx - 1);
                else scheduleRefilter();
            }
        });
    }

    public LuaTable<String, Object> getItem(int position) {
        LuaTable<Integer, LuaTable<String, Object>> d = mData;
        if (d == null || mReleased) return null;
        try {
            Object o = d.get(position + 1);
            return o instanceof LuaTable ? (LuaTable<String, Object>) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public void clear() {
        runOnMain(() -> {
            if (checkReleased() || mBaseData == null) return;
            boolean fullMode = (mData == mBaseData);
            int oldLen = fullMode ? mBaseData.length() : mData.length();
            mBaseData.clear();
            mData = mBaseData;
            if (mNotifyOnChange) {
                if (fullMode && oldLen > 0) notifyItemRangeRemoved(0, oldLen);
                else notifyDataSetChanged();
            }
        });
    }

    public void changeData() {
        runOnMain(() -> {
            if (!checkReleased()) notifyDataSetChanged();
        });
    }

    public void chageData() {
        changeData();
    }

    public void refresh() {
        changeData();
    }

    public LuaTable<Integer, LuaTable<String, Object>> getData() {
        return mData;
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
        if (notifyOnChange) changeData();
    }


    public void setFilter(LuaFunction filter) {
        mLuaFilter = filter;
    }

    @Override
    public Filter getFilter() {
        if (mFilter == null) mFilter = new ArrayFilter();
        return mFilter;
    }

    public void filter(CharSequence prefix) {
        getFilter().filter(prefix);
    }

    public void clearFilter() {
        runOnMain(() -> {
            if (checkReleased()) return;
            mPrefix = null;
            if (mData != mBaseData) {
                mData = mBaseData;
                notifyDataSetChanged();
            }
        });
    }

    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            mPrefix = prefix == null ? null : prefix.toString();
            if (mLuaFilter != null && mPrefix != null && mPrefix.length() > 0) {
                mMainHandler.post(LuaRecyclerAdapter.this::runLuaFilter);
            } else {
                mMainHandler.post(LuaRecyclerAdapter.this::resetToBaseData);
            }
            return new FilterResults();
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
        }
    }

    private void runLuaFilter() {
        if (mReleased || mLuaFilter == null || mBaseData == null) return;
        CharSequence p = mPrefix;
        if (p == null || p.length() == 0) {
            resetToBaseData();
            return;
        }
        try {
            LuaTable<Integer, LuaTable<String, Object>> out = new LuaTable<>(L);
            mLuaFilter.call(mBaseData, out, p);
            mData = out;
            notifyDataSetChanged();
        } catch (Throwable t) {
            sendError("filter", asException(t));
        }
    }

    private void resetToBaseData() {
        if (mReleased) return;
        mPrefix = null;
        if (mData != mBaseData) {
            mData = mBaseData;
            notifyDataSetChanged();
        }
    }

    private void scheduleRefilter() {
        if (mData == mBaseData || mLuaFilter == null || mRefilterPending) return;
        mRefilterPending = true;
        mMainHandler.post(() -> {
            mRefilterPending = false;
            runLuaFilter();
        });
    }


    public void setClickViewId(String id) {
        mClickViewId = id;
        runOnMain(this::notifyDataSetChanged);
    }

    public void setDefaultData(LuaTable<String, Object> defaults) {
        mDefaultData = defaults;
    }

    public void setAnimation(LuaFunction<Animation> util) {
        mAnimCache.clear();
        mAnimationUtil = util;
    }

    public void setAdapterInterface(AdapterInterface iface) {
        mAdapterInterface = iface;
    }

    public void setChildLayout(String containerId, Object childLayout) throws LuaException {
        if (childLayout == null) {
            mChildContainerId = null;
            mChildLayout = null;
            mChildViewsCache.clear();
            runOnMain(() -> {
                if (!checkReleased()) notifyDataSetChanged();
            });
            return;
        }
        if (containerId == null || containerId.length() == 0) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: containerId 不能为空");
        }
        if (!(childLayout instanceof LuaObject || childLayout instanceof String)) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: childLayout 必须是布局表或布局模块名");
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: 必须在主线程调用");
        }
        if (checkReleased()) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: 适配器已 release");
        }

        LuaTable<String, Object> itemViews = new LuaTable<>(L);
        View item;
        try {
            item = loadlayout.call(mLayout, itemViews, ViewGroup.class);
        } catch (Throwable t) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: 条目布局无法创建视图\n" + t);
        }
        Object container = null;
        if (item != null) {
            try {
                container = itemViews.get(containerId);
            } catch (Throwable ignored) {
            }
        }
        if (!(container instanceof ViewGroup)) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: 条目布局中不存在 id 为 "
                    + containerId + " 的 ViewGroup 容器");
        }

        LuaTable<String, Object> childViews = new LuaTable<>(L);
        View child;
        try {
            child = loadlayout.call(childLayout, childViews, container.getClass());
        } catch (Throwable t) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: 子布局无法创建视图\n" + t);
        }
        if (child == null) {
            throw new LuaException("LuaRecyclerAdapter.setChildLayout: 子布局无法创建视图（loadlayout 返回 nil）");
        }

        mChildContainerId = containerId;
        mChildLayout = childLayout;
        mChildViewsCache.clear();
        notifyDataSetChanged();
    }

    public void setImageHeaders(LuaTable<String, Object> headers) {
        mImageHeaders = toStringMap(headers);
    }

    private static Map<String, String> extractRowHeaders(LuaTable<String, Object> row,
                                                         LuaTable<String, Object> views) {
        Object h = null;
        try {
            h = row.get("header");
            if (h == null) h = row.get("headers");
        } catch (Throwable ignored) {
        }
        if (!(h instanceof LuaTable)) return null;
        if (views != null) {
            try {
                if (views.get("header") instanceof View || views.get("headers") instanceof View) {
                    return null;
                }
            } catch (Throwable ignored) {
            }
        }
        return toStringMap((LuaTable<String, Object>) h);
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        mOnItemClickListener = l;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        mOnItemLongClickListener = l;
    }


    private void setHelper(View view, Object value, Map<String, String> headers) {
        if (view == null || value == null) return;
        try {
            if (value instanceof LuaTable) {
                setFields(view, (LuaTable<String, Object>) value);
            } else if (view instanceof TextView) {
                ((TextView) view).setText(value instanceof CharSequence
                        ? (CharSequence) value : String.valueOf(value));
            } else if (view instanceof ImageView) {
                setImageView((ImageView) view, value, headers);
            }
        } catch (Throwable t) {
            sendError("setHelper", asException(t));
        }
    }

    private void setImageView(ImageView view, Object value, Map<String, String> headers) {
        if (value instanceof Number) {
            glideClear(view);
            view.setImageResource(((Number) value).intValue());
        } else if (value instanceof Bitmap) {
            glideClear(view);
            view.setImageBitmap((Bitmap) value);
        } else if (value instanceof Drawable) {
            glideClear(view);
            view.setImageDrawable((Drawable) value);
        } else {
            String p = String.valueOf(value);
            if (p.length() == 0) return;
            Object model = p;
            if (isNetworkUrl(p)) {
                Map<String, String> merged = mergeHeaders(mImageHeaders, headers);
                if (merged != null && !merged.isEmpty()) model = buildGlideUrl(p, merged);
            } else if (p.indexOf("://") < 0) {
                File f = new File(p);
                if (!f.isFile()) {
                    File alt = null;
                    try {
                        alt = new File(mContext.getLuaPath(p));
                    } catch (Throwable ignored) {
                    }
                    if (alt != null && alt.isFile()) f = alt;
                }
                if (f.isFile()) model = f;
            }
            try {
                Glide.with(view).load(model).into(view);
            } catch (Throwable t) {
                Log.w(TAG, "load image: " + t);
            }
        }
    }

    private static boolean isNetworkUrl(String p) {
        return p.regionMatches(true, 0, "http://", 0, 7)
                || p.regionMatches(true, 0, "https://", 0, 8);
    }

    private static Object buildGlideUrl(String url, Map<String, String> headers) {
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        boolean hasUa = false;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || v == null) continue;
            builder.addHeader(k, v);
            if ("User-Agent".equalsIgnoreCase(k)) hasUa = true;
        }
        if (!hasUa) builder.addHeader("User-Agent", DEFAULT_IMAGE_UA);
        sorted.putAll(headers);
        StringBuilder key = new StringBuilder(url);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            key.append('\n').append(e.getKey()).append(':').append(e.getValue());
        }
        final String cacheKey = key.toString();
        return new GlideUrl(url, builder.build()) {
            @Override
            public String getCacheKey() {
                return cacheKey;
            }
        };
    }

    private static Map<String, String> toStringMap(LuaTable<String, Object> table) {
        if (table == null) return null;
        Map<String, String> out = new HashMap<>();
        try {
            for (Map.Entry<String, Object> e : table.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (k == null || v == null) continue;
                String vs = String.valueOf(v);
                if (!vs.isEmpty()) out.put(String.valueOf(k), vs);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Map<String, String> mergeHeaders(Map<String, String> base, Map<String, String> override) {
        if (base == null || base.isEmpty()) return override;
        if (override == null || override.isEmpty()) return base;
        Map<String, String> out = new HashMap<>(base);
        out.putAll(override);
        return out;
    }

    private static void glideClear(ImageView view) {
        try {
            Glide.with(view).clear(view);
        } catch (Throwable ignored) {
        }
    }

    private void setFields(View view, LuaTable<String, Object> fields) {
        if (fields == null) return;
        Object src = null;
        Map<String, String> headers = null;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null) continue;
            try {
                if ("src".equalsIgnoreCase(key)) {
                    src = value;
                } else if (("header".equalsIgnoreCase(key) || "headers".equalsIgnoreCase(key))
                        && value instanceof LuaTable) {
                    headers = toStringMap((LuaTable<String, Object>) value);
                } else {
                    javaSetter(view, key, value);
                }
            } catch (Throwable t) {
                Log.w(TAG, "setFields " + key + ": " + t);
            }
        }
        if (src != null) setHelper(view, src, headers);
    }

    private int javaSetter(Object obj, String methodName, Object value) throws LuaException {
        if (methodName.length() > 2 && methodName.startsWith("on") && value instanceof LuaFunction) {
            return javaSetListener(obj, methodName, value);
        }
        return javaSetMethod(obj, methodName, value);
    }

    private int javaSetListener(Object obj, String methodName, Object value) throws LuaException {
        String name = "setOn" + methodName.substring(2) + "Listener";
        ArrayList<Method> methods = LuaJavaAPI.getMethod(obj.getClass(), name, false);
        for (Method m : methods) {
            Class<?>[] tp = m.getParameterTypes();
            if (tp.length == 1 && tp[0].isInterface()) {
                L.newTable();
                try {
                    L.pushObjectValue(value);
                    L.setField(-2, methodName);
                    Object listener = L.getLuaObject(-1).createProxy(tp[0]);
                    m.invoke(obj, listener);
                    return 1;
                } catch (Exception e) {
                    throw new LuaException(e);
                } finally {
                    L.pop(1);
                }
            }
        }
        return 0;
    }

    private int javaSetMethod(Object obj, String methodName, Object value) throws LuaException {
        if (Character.isLowerCase(methodName.charAt(0))) {
            methodName = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        }
        String name = "set" + methodName;
        Class<?> type = value.getClass();
        StringBuilder buf = new StringBuilder();
        ArrayList<Method> methods = LuaJavaAPI.getMethod(obj.getClass(), name, false);
        for (Method m : methods) {
            Class<?>[] tp = m.getParameterTypes();
            if (tp.length != 1) continue;
            if (tp[0].isPrimitive()) {
                try {
                    if (value instanceof Double || value instanceof Float) {
                        m.invoke(obj, LuaState.convertLuaNumber(
                                ((Number) value).doubleValue(), tp[0]));
                    } else if (value instanceof Long || value instanceof Integer) {
                        m.invoke(obj, LuaState.convertLuaNumber(
                                ((Number) value).longValue(), tp[0]));
                    } else if (value instanceof Boolean) {
                        m.invoke(obj, value);
                    } else {
                        continue;
                    }
                    return 1;
                } catch (Exception e) {
                    buf.append(e.getMessage()).append('\n');
                    continue;
                }
            }
            if (!tp[0].isAssignableFrom(type)) continue;
            try {
                m.invoke(obj, value);
                return 1;
            } catch (Exception e) {
                buf.append(e.getMessage()).append('\n');
            }
        }
        if (buf.length() > 0) {
            throw new LuaException("Invalid setter " + methodName
                    + ". Invalid Parameters.\n" + buf + type);
        }
        throw new LuaException("Invalid setter " + methodName + " is not a method.\n");
    }


    public void release() {
        mReleased = true;
        runOnMain(() -> {
            loadlayout = null;
            mLayout = null;
            mDefaultData = null;
            mLuaFilter = null;
            mAnimationUtil = null;
            mOnItemClickListener = null;
            mOnItemLongClickListener = null;
            mAdapterInterface = null;
            mChildContainerId = null;
            mChildLayout = null;
            mChildViewsCache.clear();
            mPreCreatedView = null;
            mPreCreatedViews = null;
            mBaseData = null;
            mData = null;
            mPrefix = null;
            mImageHeaders = null;
            mAnimCache.clear();
        });
    }


    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mMainHandler.post(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    sendError("post", asException(t));
                }
            });
        }
    }

    private boolean checkReleased() {
        return mReleased || mData == null;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static Exception asException(Throwable t) {
        return t instanceof Exception ? (Exception) t : new RuntimeException(t);
    }

    private void sendError(String where, Exception e) {
        LuaContext c = mContext;
        if (c == null) return;
        try {
            c.sendError(TAG + "." + where, e);
        } catch (Throwable ignored) {
        }
    }
}
