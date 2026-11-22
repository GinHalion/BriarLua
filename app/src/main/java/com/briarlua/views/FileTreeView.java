package com.briarlua.views;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.briarlua.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileFilter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class FileTreeView extends FrameLayout {

    public static final int SORT_AUTO = 0;
    public static final int SORT_BY_NAME = 1;
    public static final int SORT_BY_TIME = 2;

    private static final String TAG = "FileTreeView";

    private RecyclerView mRecyclerView;
    private TreeAdapter mTreeAdapter;
    private final LinkedList<Node> mNodes = new LinkedList<>();
    private LinearLayoutManager mLayoutManager;
    private OnNodeClickListener onNodeClickListener;
    private float folderIconSize = 24f;
    private float nodeIndent = -1f;
    private float lineSpace = 4f;
    private Drawable nodeOpenIcon;
    private Drawable nodeCloseIcon;
    private int sortType = SORT_BY_NAME;
    private boolean isSortDescending = false;
    private boolean isShowHidden = true;

    private OnFileClickListener fileClickListener;
    private OnFileLongClickListener fileLongClickListener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    final Map<String, Integer> tintMap = new HashMap<>();
    private final Map<String, Drawable> drawableIconMap = new HashMap<>();

    private final Map<String, Integer> imgs = new HashMap<>();

    private static final String[] IMAGE_EXTS =
            {"jpg", "jpeg", "png", "gif", "bmp", "webp"};

    private final FileFilter mDirFilter = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() || (pathname.isFile() && (isShowHidden || !pathname.isHidden()));
        }
    };

    public FileTreeView(Context context) {
        this(context, null);
    }

    public FileTreeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FileTreeView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        imgs.put("lua", R.drawable.ic_language_lua);
        imgs.put("aly", R.drawable.ic_code_braces);
        imgs.put("json", R.drawable.ic_code_json);
        imgs.put("png", R.drawable.ic_file_image_outline);
        imgs.put("jpg", R.drawable.ic_file_image_outline);
        imgs.put("gif", R.drawable.ic_file_image_outline);
        imgs.put("mp3", R.drawable.ic_file_music_outline);
        imgs.put("java", R.drawable.ic_language_java);
        imgs.put("kt", R.drawable.ic_language_kotlin);
        imgs.put("gradle", R.drawable.ic_language_gradle);
        imgs.put("js", R.drawable.ic_language_javascript);
        imgs.put("py", R.drawable.ic_language_python);
        imgs.put("php", R.drawable.ic_language_php);
        imgs.put("apk", R.drawable.ic_android);
        imgs.put("zip", R.drawable.ic_zip_box_outline);
        initView();
    }

    public String getFileExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isImageExt(String ext) {
        for (String e : IMAGE_EXTS) {
            if (e.equals(ext)) return true;
        }
        return false;
    }

    private void executeFileClick(View view, String path) {
        if (fileClickListener != null) fileClickListener.onClick(view, path);
    }

    private void executeFileLongClick(View view, String path) {
        if (fileLongClickListener != null) fileLongClickListener.onLongClick(view, path);
    }

    private void submitBackground(final Runnable r) {
        if (executor.isShutdown()) return;
        try {
            executor.execute(r);
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        executor.shutdown();
    }

    private void initView() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        nodeOpenIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_chevron_down);
        nodeCloseIcon = ContextCompat.getDrawable(getContext(),
                com.google.android.material.R.drawable.material_ic_keyboard_arrow_right_black_24dp);

        MyScrollView scrollView = new MyScrollView(getContext());
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setFillViewport(true);
        addView(scrollView);

        mRecyclerView = new RecyclerView(getContext());
        mRecyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRecyclerView.setVerticalScrollBarEnabled(true);
        mRecyclerView.setHasFixedSize(true);
        scrollView.addView(mRecyclerView);

        mLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mTreeAdapter = new TreeAdapter();
        mRecyclerView.setAdapter(mTreeAdapter);
    }

    public boolean init(String path) {
        if (!canReadStorage()) return false;
        mNodes.clear();
        File rootFile = new File(path);
        final Node rootNode = new Node(
                rootFile.getName(), rootFile.getAbsolutePath(), 0L);
        rootNode.level = 0;
        rootNode.isDir = true;
        rootNode.isDirResolved = true;
        rootNode.isExpanded = true;
        rootNode.isShowNodeIcon = true;
        mNodes.add(rootNode);
        submitBackground(new Runnable() {
            @Override
            public void run() {
                rootNode.time = new File(rootNode.path).lastModified();
                addNode(rootNode);
            }
        });
        return true;
    }

    public Node makeNode(Node parent, String name, File dir) {
        Node node = new Node(name, dir.getAbsolutePath(), dir.lastModified());
        if (parent != null) node.level = parent.level + 1;
        node.isDir = dir.isDirectory();
        node.isDirResolved = true;
        if (!node.isDir) {
            node.isImage = isImageExt(getFileExtension(dir.getAbsolutePath()));
        }
        File[] children = dir.listFiles(mDirFilter);
        if (children != null && children.length > 0) node.isShowNodeIcon = true;
        return node;
    }

    public List<Node> getNodes() {
        return new ArrayList<>(mNodes);
    }

    @Nullable
    public Node getNode(String path) {
        for (Node n : mNodes) {
            if (n.path.equals(path)) return n;
        }
        return null;
    }

    public class TreeAdapter extends RecyclerView.Adapter<MyViewHolder> {

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tree_item_node, parent, false);
            MyViewHolder holder = new MyViewHolder(view);
            int iconPx = dip2px(folderIconSize);
            applyIconSize(holder.iv_icon, iconPx);
            applyIconSize(holder.iv_node, iconPx);
            return holder;
        }

        private void applyIconSize(AppCompatImageView iv, int px) {
            ViewGroup.LayoutParams p = iv.getLayoutParams();
            if (p == null) return;
            if (p.width == px && p.height == px) return;
            p.width = px;
            p.height = px;
            iv.requestLayout();
        }

        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            final Node node = mNodes.get(position);
            holder.tv_name.setText(node.name);

            File file = null;
            boolean isDir = node.isDir;
            if (!node.isDirResolved) {
                file = new File(node.path);
                isDir = file.isDirectory();
                node.isDir = isDir;
                node.isDirResolved = true;
                if (!isDir) node.isImage = isImageExt(getFileExtension(node.path));
            }

            holder.iv_icon.setImageTintList(primaryTint());

            if (!isDir && node.isImage) {
                int iconPx = dip2px(folderIconSize);
                Glide.with(holder.itemView.getContext())
                        .load(node.path)
                        .override(iconPx, iconPx)
                        .signature(new ObjectKey(node.time))
                        .error(R.drawable.ic_file_image_outline)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(holder.iv_icon);
                holder.iv_icon.setImageTintList(null);
                holder.iv_node.setVisibility(View.INVISIBLE);
                holder.iv_node.setPadding(14, 14, 14, 14);
            } else if (!isDir) {
                String ext = getFileExtension(node.path);
                Drawable icon = drawableIconMap.get(ext);
                if (icon != null) {
                    holder.iv_icon.setImageDrawable(icon);
                } else {
                    Integer resId = imgs.get(ext);
                    holder.iv_icon.setImageResource(resId != null ? resId : R.drawable.ic_file_outline);
                }
                holder.iv_node.setVisibility(View.INVISIBLE);
                holder.iv_node.setPadding(14, 14, 14, 14);
            } else {
                holder.iv_node.setVisibility(View.VISIBLE);
                holder.iv_icon.setImageResource(R.drawable.ic_folder);
                holder.iv_node.setImageDrawable(node.isExpanded ? nodeOpenIcon : nodeCloseIcon);
                holder.iv_node.setPadding(0, 0, 0, 0);
            }

            int padding = nodeIndent < 0 ? dip2px(folderIconSize) : dip2px(nodeIndent);
            holder.itemView.setPadding(
                    padding * node.level + dip2px(10f),
                    dip2px(lineSpace) / 2,
                    dip2px(10f),
                    dip2px(lineSpace) / 2);

            holder.vg_item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    Node n = mNodes.get(pos);
                    File f = new File(n.path);
                    if (f.isDirectory()) {
                        n.isExpanded = !n.isExpanded;
                        updateNodeState(holder, n);
                        if (onNodeClickListener != null) {
                            onNodeClickListener.onNodeClick(FileTreeView.this, pos, f, n.isExpanded);
                        }
                    } else {
                        executeFileClick(holder.vg_item, n.path);
                    }
                }
            });

            holder.vg_item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return true;
                    executeFileLongClick(holder.vg_item, mNodes.get(pos).path);
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return mNodes.size();
        }
    }

    private ColorStateList mPrimaryTint;

    private ColorStateList primaryTint() {
        if (mPrimaryTint == null) {
            try {
                mPrimaryTint = ColorStateList.valueOf(MaterialColors.getColor(
                        this, androidx.appcompat.R.attr.colorPrimary, 0xFF36618E));
            } catch (Throwable t) {
                mPrimaryTint = ColorStateList.valueOf(0xFF36618E);
            }
        }
        return mPrimaryTint;
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        public AppCompatImageView iv_node;
        public AppCompatImageView iv_icon;
        public AppCompatTextView tv_name;
        public ViewGroup vg_item;

        public MyViewHolder(View itemView) {
            super(itemView);
            iv_node = itemView.findViewById(R.id.iv_node);
            iv_icon = itemView.findViewById(R.id.iv_icon);
            tv_name = itemView.findViewById(R.id.tv_name);
            vg_item = itemView.findViewById(R.id.vg_item);
        }
    }

    public void setFileIcon(String extension, @DrawableRes int iconResId) {
        try {
            imgs.put(extension, iconResId);
            mTreeAdapter.notifyDataSetChanged();
        } catch (Throwable t) {
            Log.e(TAG, "setFileIcon(resId=" + iconResId + ") failed", t);
        }
    }

    public void setFileIcon(String extension, Drawable drawable) {
        Drawable icon;
        if (drawable.getConstantState() != null) {
            icon = drawable.getConstantState().newDrawable().mutate();
        } else {
            icon = drawable.mutate();
        }
        drawableIconMap.put(extension, icon);
        mTreeAdapter.notifyDataSetChanged();
    }

    public void setFileIcon(String ext, Object luaIcon) {
        Drawable drawable;
        if (luaIcon instanceof String) {
            drawable = LuaBitmapCompat.decode(getContext(), (String) luaIcon);
        } else if (luaIcon instanceof Drawable) {
            drawable = (Drawable) luaIcon;
        } else {
            throw new IllegalArgumentException("Unsupported icon type");
        }
        setFileIcon(ext, drawable);
    }

    public void sort(List<Node> nodes) {
        Comparator<Node> directoryComparator = new Comparator<Node>() {
            @Override
            public int compare(Node a, Node b) {
                boolean aDir, bDir;
                if (a.isDirResolved && b.isDirResolved) {
                    aDir = a.isDir;
                    bDir = b.isDir;
                } else {
                    aDir = new File(a.path).isDirectory();
                    bDir = new File(b.path).isDirectory();
                }
                if (aDir == bDir) return 0;
                return aDir ? -1 : 1;
            }
        };

        Comparator<Node> contentComparator;
        if (sortType == SORT_BY_NAME) {
            contentComparator = new Comparator<Node>() {
                @Override
                public int compare(Node a, Node b) {
                    return a.name.compareTo(b.name);
                }
            };
        } else if (sortType == SORT_BY_TIME) {
            contentComparator = new Comparator<Node>() {
                @Override
                public int compare(Node a, Node b) {
                    return Long.compare(a.time, b.time);
                }
            };
        } else {
            final Collator collator = Collator.getInstance();
            contentComparator = new Comparator<Node>() {
                @Override
                public int compare(Node a, Node b) {
                    return collator.compare(a.name, b.name);
                }
            };
        }

        if (isSortDescending) {
            final Comparator<Node> base = contentComparator;
            contentComparator = new Comparator<Node>() {
                @Override
                public int compare(Node a, Node b) {
                    return base.compare(b, a);
                }
            };
        }

        final Comparator<Node> finalContent = contentComparator;
        Collections.sort(nodes, new Comparator<Node>() {
            @Override
            public int compare(Node a, Node b) {
                int d = directoryComparator.compare(a, b);
                if (d != 0) return d;
                return finalContent.compare(a, b);
            }
        });
    }

    public void updateNodeState(MyViewHolder holder, final Node node) {
        if (node.isExpanded) {
            startNodeOpenAnimation(holder.iv_node);
            submitBackground(new Runnable() {
                @Override
                public void run() {
                    addNodeInternal(node);
                }
            });
        } else {
            startNodeCloseAnimation(holder.iv_node);
            removeNode(node);
        }
    }

    private void startNodeOpenAnimation(final AppCompatImageView view) {
        RotateAnimation anim = new RotateAnimation(0f, 90f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(100);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                view.setImageDrawable(nodeOpenIcon);
            }
        });
        view.startAnimation(anim);
    }

    private void startNodeCloseAnimation(final AppCompatImageView view) {
        RotateAnimation anim = new RotateAnimation(0f, -90f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(100);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                view.setImageDrawable(nodeCloseIcon);
            }
        });
        view.startAnimation(anim);
    }

    public static class Node {
        public final String name;
        public final String path;
        public long time;
        public int level = 0;
        public boolean isExpanded = false;
        public boolean isShowNodeIcon = false;
        public boolean isDir = false;
        public boolean isDirResolved = false;
        public boolean isImage = false;

        public Node(String name, String path, long time) {
            this.name = name;
            this.path = path;
            this.time = time;
        }
    }

    public void refresh() {
        refresh((String) null);
    }

    public void refresh(@Nullable String path) {
        if (path == null) {
            Set<String> expandedPaths = new HashSet<>();
            for (Node n : mNodes) if (n.isExpanded) expandedPaths.add(n.path);
            if (mNodes.isEmpty()) return;
            final String rootPath = mNodes.getFirst().path;
            mNodes.clear();
            init(rootPath);
            post(new Runnable() {
                @Override
                public void run() {
                    for (String p : expandedPaths) {
                        if (p.equals(rootPath)) continue;
                        Node n = getNode(p);
                        if (n != null) {
                            n.isExpanded = true;
                            addNode(n);
                        }
                    }
                }
            });
        } else {
            Node node = getNode(path);
            if (node == null) {
                refresh((String) null);
                return;
            }
            boolean wasExpanded = node.isExpanded;
            removeNode(node);
            if (wasExpanded) {
                addNode(node);
            } else {
                final Node finalNode = node;
                post(new Runnable() {
                    @Override
                    public void run() {
                        int index = mNodes.indexOf(finalNode);
                        if (index != -1) mTreeAdapter.notifyItemChanged(index);
                    }
                });
            }
        }
    }

    public void addNode(final Node parent) {
        submitBackground(new Runnable() {
            @Override
            public void run() {
                addNodeInternal(parent);
            }
        });
    }

    private void addNodeInternal(final Node parent) {
        if (!parent.isExpanded) return;
        File dir = new File(parent.path);
        File[] children = dir.listFiles(mDirFilter);
        final List<Node> nodes = new ArrayList<>();
        if (children != null && children.length > 0) {
            for (File file : children) nodes.add(makeNode(parent, file.getName(), file));
            if (sortType != SORT_AUTO) sort(nodes);
        }
        post(new Runnable() {
            @Override
            public void run() {
                if (!parent.isExpanded || mNodes.indexOf(parent) == -1) return;
                int parentPosition = mNodes.indexOf(parent);
                int childCount = 0;
                int i = parentPosition + 1;
                while (i < mNodes.size() && mNodes.get(i).level > parent.level) {
                    childCount++;
                    i++;
                }
                if (childCount > 0) {
                    mNodes.subList(parentPosition + 1, parentPosition + 1 + childCount).clear();
                    mTreeAdapter.notifyItemRangeRemoved(parentPosition + 1, childCount);
                }
                if (!nodes.isEmpty()) {
                    mNodes.addAll(parentPosition + 1, nodes);
                    parent.isShowNodeIcon = true;
                    mTreeAdapter.notifyItemRangeInserted(parentPosition + 1, nodes.size());
                } else {
                    parent.isShowNodeIcon = false;
                    mTreeAdapter.notifyItemChanged(parentPosition);
                }
            }
        });
    }

    public void removeNode(Node parent) {
        int parentPosition = mNodes.indexOf(parent);
        if (parentPosition == -1) return;
        int childCount = 0;
        int i = parentPosition + 1;
        while (i < mNodes.size() && mNodes.get(i).level > parent.level) {
            childCount++;
            i++;
        }
        if (childCount > 0) {
            mNodes.subList(parentPosition + 1, parentPosition + 1 + childCount).clear();
            mTreeAdapter.notifyItemRangeRemoved(parentPosition + 1, childCount);
        }
    }

    private boolean canReadStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void setOnNodeClickListener(OnNodeClickListener listener) {
        this.onNodeClickListener = listener;
    }

    public void setOnFileClickListener(OnFileClickListener listener) {
        this.fileClickListener = listener;
    }

    public void setOnLongFileClickListener(OnFileLongClickListener listener) {
        this.fileLongClickListener = listener;
    }

    public void setNodeIndent(float indent) { this.nodeIndent = indent; }
    public void setLineSpace(float space) { this.lineSpace = space; }
    public void setSortType(int type) { this.sortType = type; }
    public void setSortDescending(boolean descending) { this.isSortDescending = descending; }
    public void setShowHidden(boolean show) { this.isShowHidden = show; }
    public void setFolderIconSize(float size) { this.folderIconSize = size; }

    public boolean openNode(String path) {
        File targetFile = new File(path);
        if (!targetFile.exists()) return false;
        try {
            String targetPath = targetFile.getCanonicalPath();
            List<String> pathsToExpand = new ArrayList<>();
            File current = targetFile;
            while (current != null && !current.getPath().equals("/")) {
                pathsToExpand.add(0, current.getCanonicalPath());
                current = current.getParentFile();
            }
            for (String p : pathsToExpand) {
                Node node = null;
                for (Node n : mNodes) {
                    if (new File(n.path).getCanonicalPath().equals(p)) { node = n; break; }
                }
                if (node == null) {
                    File parentFile = new File(p).getParentFile();
                    if (parentFile == null) continue;
                    String parentPath = parentFile.getCanonicalPath();
                    Node parentNode = null;
                    for (Node n : mNodes) {
                        if (new File(n.path).getCanonicalPath().equals(parentPath)) {
                            parentNode = n; break;
                        }
                    }
                    if (parentNode == null) continue;
                    if (!parentNode.isExpanded) {
                        parentNode.isExpanded = true;
                        addNodeSync(parentNode);
                    }
                }
                Node foundNode = null;
                for (Node n : mNodes) {
                    if (new File(n.path).getCanonicalPath().equals(p)) { foundNode = n; break; }
                }
                if (foundNode == null) continue;
                if (!foundNode.isExpanded && new File(foundNode.path).isDirectory()) {
                    foundNode.isExpanded = true;
                    addNodeSync(foundNode);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void addNodeSync(Node parent) {
        if (!parent.isExpanded || !new File(parent.path).isDirectory()) return;
        File dir = new File(parent.path);
        File[] children = dir.listFiles(mDirFilter);
        if (children == null) return;
        List<Node> nodes = new ArrayList<>();
        for (File file : children) nodes.add(makeNode(parent, file.getName(), file));
        if (sortType != SORT_AUTO) sort(nodes);
        int parentIndex = mNodes.indexOf(parent);
        if (parentIndex == -1) return;
        mNodes.addAll(parentIndex + 1, nodes);
        mTreeAdapter.notifyItemRangeInserted(parentIndex + 1, nodes.size());
    }

    public boolean closeNode(String path) {
        Node node = getNode(path);
        if (node == null) return false;
        if (node.isExpanded && new File(node.path).isDirectory()) {
            node.isExpanded = false;
            removeNode(node);
            return true;
        }
        return false;
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private class MyScrollView extends HorizontalScrollView {
        public MyScrollView(Context context) { super(context); }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return super.onInterceptTouchEvent(ev);
        }
    }

    public interface OnNodeClickListener {
        void onNodeClick(FileTreeView view, int position, File dir, boolean isExpanded);
    }

    public interface OnFileClickListener {
        void onClick(View view, String path);
    }

    public interface OnFileLongClickListener {
        void onLongClick(View view, String path);
    }

    private static class LuaBitmapCompat {
        static android.graphics.drawable.Drawable decode(Context ctx, String path) {
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(path);
            if (bm == null) return null;
            return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);
        }
    }
}
