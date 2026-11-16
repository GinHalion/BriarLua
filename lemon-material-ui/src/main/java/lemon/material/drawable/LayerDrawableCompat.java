
package lemon.material.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;

public class LayerDrawableCompat extends Drawable implements Drawable.Callback {
    private static final String LOG_TAG = "LayerDrawable";

    public static final int PADDING_MODE_NEST = 0;

    public static final int PADDING_MODE_STACK = 1;

    public static final int INSET_UNDEFINED = Integer.MIN_VALUE;

    @NonNull
    LayerState mLayerState;

    private int[] mPaddingL;
    private int[] mPaddingT;
    private int[] mPaddingR;
    private int[] mPaddingB;

    private final Rect mTmpRect = new Rect();
    private final Rect mTmpOutRect = new Rect();
    private final Rect mTmpContainer = new Rect();
    private Rect mHotspotBounds;
    private boolean mMutated;

    private boolean mSuspendChildInvalidation;
    private boolean mChildRequestedInvalidation;

    public LayerDrawableCompat(@NonNull Drawable[] layers) {
        this(layers, null);
    }

    LayerDrawableCompat(@NonNull Drawable[] layers, @Nullable LayerState state) {
        this(state, null);

        if (layers == null) {
            throw new IllegalArgumentException("layers must be non-null");
        }

        final int length = layers.length;
        final ChildDrawable[] r = new ChildDrawable[length];
        for (int i = 0; i < length; i++) {
            r[i] = new ChildDrawable(mLayerState.mDensity);
            Drawable child = layers[i];
            r[i].mDrawable = child;
            if (child != null) {
                child.setCallback(this);
                mLayerState.mChildrenChangingConfigurations |= child.getChangingConfigurations();
            }
        }
        mLayerState.mNumChildren = length;
        mLayerState.mChildren = r;

        ensurePadding();
        refreshPadding();
    }

    LayerDrawableCompat() {
        this((LayerState) null, null);
    }

    LayerDrawableCompat(@Nullable LayerState state, @Nullable Resources res) {
        mLayerState = createConstantState(state, res);
        if (mLayerState.mNumChildren > 0) {
            ensurePadding();
            refreshPadding();
        }
    }

    LayerState createConstantState(@Nullable LayerState state, @Nullable Resources res) {
        return new LayerState(state, this, res);
    }

    @Override
    public void applyTheme(@NonNull Theme t) {
        super.applyTheme(t);

        final LayerState state = mLayerState;
        final int density = Drawable_resolveDensity(t.getResources(), 0);
        state.setDensity(density);

        final ChildDrawable[] array = state.mChildren;
        final int N = state.mNumChildren;
        for (int i = 0; i < N; i++) {
            final ChildDrawable layer = array[i];
            layer.setDensity(density);

            final Drawable d = layer.mDrawable;
            if (d != null && d.canApplyTheme()) {
                d.applyTheme(t);

                state.mChildrenChangingConfigurations |= d.getChangingConfigurations();
            }
        }
    }

    @Override
    public boolean canApplyTheme() {
        return mLayerState.canApplyTheme() || super.canApplyTheme();
    }

    @Override
    public boolean isProjected() {
        if (super.isProjected()) {
            return true;
        }

        final ChildDrawable[] layers = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            Drawable childDrawable = layers[i].mDrawable;
            if (childDrawable != null && DrawableCompat_isProjected(childDrawable)) {
                return true;
            }
        }

        return false;
    }

    int addLayer(@NonNull ChildDrawable layer) {
        final LayerState st = mLayerState;
        final int N = st.mChildren != null ? st.mChildren.length : 0;
        final int i = st.mNumChildren;
        if (i >= N) {
            final ChildDrawable[] nu = new ChildDrawable[N + 10];
            if (i > 0) {
                System.arraycopy(st.mChildren, 0, nu, 0, i);
            }

            st.mChildren = nu;
        }

        st.mChildren[i] = layer;
        st.mNumChildren++;
        st.invalidateCache();
        return i;
    }

    ChildDrawable addLayer(Drawable dr, int[] themeAttrs, int id,
                           int left, int top, int right, int bottom) {
        final ChildDrawable childDrawable = createLayer(dr);
        childDrawable.mId = id;
        childDrawable.mThemeAttrs = themeAttrs;
        childDrawable.mDrawable.setAutoMirrored(isAutoMirrored());
        childDrawable.mInsetL = left;
        childDrawable.mInsetT = top;
        childDrawable.mInsetR = right;
        childDrawable.mInsetB = bottom;

        addLayer(childDrawable);

        mLayerState.mChildrenChangingConfigurations |= dr.getChangingConfigurations();
        dr.setCallback(this);

        return childDrawable;
    }

    private ChildDrawable createLayer(Drawable dr) {
        final ChildDrawable layer = new ChildDrawable(mLayerState.mDensity);
        layer.mDrawable = dr;
        return layer;
    }

    public int addLayer(Drawable dr) {
        final ChildDrawable layer = createLayer(dr);
        final int index = addLayer(layer);
        ensurePadding();
        refreshChildPadding(index, layer);
        return index;
    }

    public Drawable findDrawableByLayerId(int id) {
        final ChildDrawable[] layers = mLayerState.mChildren;
        for (int i = mLayerState.mNumChildren - 1; i >= 0; i--) {
            if (layers[i].mId == id) {
                return layers[i].mDrawable;
            }
        }

        return null;
    }

    public void setId(int index, int id) {
        mLayerState.mChildren[index].mId = id;
    }

    public int getId(int index) {
        if (index >= mLayerState.mNumChildren) {
            throw new IndexOutOfBoundsException();
        }
        return mLayerState.mChildren[index].mId;
    }

    public int getNumberOfLayers() {
        return mLayerState.mNumChildren;
    }

    public boolean setDrawableByLayerId(int id, Drawable drawable) {
        final int index = findIndexByLayerId(id);
        if (index < 0) {
            return false;
        }

        setDrawable(index, drawable);
        return true;
    }

    public int findIndexByLayerId(int id) {
        final ChildDrawable[] layers = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final ChildDrawable childDrawable = layers[i];
            if (childDrawable.mId == id) {
                return i;
            }
        }

        return -1;
    }

    public void setDrawable(int index, Drawable drawable) {
        if (index >= mLayerState.mNumChildren) {
            throw new IndexOutOfBoundsException();
        }

        final ChildDrawable[] layers = mLayerState.mChildren;
        final ChildDrawable childDrawable = layers[index];
        if (childDrawable.mDrawable != null) {
            if (drawable != null) {
                final Rect bounds = childDrawable.mDrawable.getBounds();
                drawable.setBounds(bounds);
            }

            childDrawable.mDrawable.setCallback(null);
        }

        if (drawable != null) {
            drawable.setCallback(this);
        }

        childDrawable.mDrawable = drawable;
        mLayerState.invalidateCache();

        refreshChildPadding(index, childDrawable);
    }

    public Drawable getDrawable(int index) {
        if (index >= mLayerState.mNumChildren) {
            throw new IndexOutOfBoundsException();
        }
        return mLayerState.mChildren[index].mDrawable;
    }

    public void setLayerSize(int index, int w, int h) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mWidth = w;
        childDrawable.mHeight = h;
    }

    public void setLayerWidth(int index, int w) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mWidth = w;
    }

    public int getLayerWidth(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mWidth;
    }

    public void setLayerHeight(int index, int h) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mHeight = h;
    }

    public int getLayerHeight(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mHeight;
    }

    public void setLayerGravity(int index, int gravity) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mGravity = gravity;
    }

    public int getLayerGravity(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mGravity;
    }

    public void setLayerInset(int index, int l, int t, int r, int b) {
        setLayerInsetInternal(index, l, t, r, b, INSET_UNDEFINED, INSET_UNDEFINED);
    }

    public void setLayerInsetRelative(int index, int s, int t, int e, int b) {
        setLayerInsetInternal(index, 0, t, 0, b, s, e);
    }

    public void setLayerInsetLeft(int index, int l) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetL = l;
    }

    public int getLayerInsetLeft(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mInsetL;
    }

    public void setLayerInsetRight(int index, int r) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetR = r;
    }

    public int getLayerInsetRight(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mInsetR;
    }

    public void setLayerInsetTop(int index, int t) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetT = t;
    }

    public int getLayerInsetTop(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mInsetT;
    }

    public void setLayerInsetBottom(int index, int b) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetB = b;
    }

    public int getLayerInsetBottom(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mInsetB;
    }

    public void setLayerInsetStart(int index, int s) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetS = s;
    }

    public int getLayerInsetStart(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mInsetS;
    }

    public void setLayerInsetEnd(int index, int e) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetE = e;
    }

    public int getLayerInsetEnd(int index) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        return childDrawable.mInsetE;
    }

    private void setLayerInsetInternal(int index, int l, int t, int r, int b, int s, int e) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetL = l;
        childDrawable.mInsetT = t;
        childDrawable.mInsetR = r;
        childDrawable.mInsetB = b;
        childDrawable.mInsetS = s;
        childDrawable.mInsetE = e;
    }

    public void setPaddingMode(int mode) {
        if (mLayerState.mPaddingMode != mode) {
            mLayerState.mPaddingMode = mode;
        }
    }

    public int getPaddingMode() {
        return mLayerState.mPaddingMode;
    }

    private void suspendChildInvalidation() {
        mSuspendChildInvalidation = true;
    }

    private void resumeChildInvalidation() {
        mSuspendChildInvalidation = false;

        if (mChildRequestedInvalidation) {
            mChildRequestedInvalidation = false;
            invalidateSelf();
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        if (mSuspendChildInvalidation) {
            mChildRequestedInvalidation = true;
        } else {
            mLayerState.invalidateCache();

            invalidateSelf();
        }
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }

    @Override
    public void draw(Canvas canvas) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.draw(canvas);
            }
        }
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mLayerState.getChangingConfigurations();
    }

    @Override
    public boolean getPadding(Rect padding) {
        final LayerState layerState = mLayerState;
        if (layerState.mPaddingMode == PADDING_MODE_NEST) {
            computeNestedPadding(padding);
        } else {
            computeStackedPadding(padding);
        }

        final int paddingT = layerState.mPaddingTop;
        final int paddingB = layerState.mPaddingBottom;

        final boolean isLayoutRtl = DrawableCompat.getLayoutDirection(this)
                == ViewCompat.LAYOUT_DIRECTION_RTL;
        final int paddingRtlL = isLayoutRtl ? layerState.mPaddingEnd : layerState.mPaddingStart;
        final int paddingRtlR = isLayoutRtl ? layerState.mPaddingStart : layerState.mPaddingEnd;
        final int paddingL = paddingRtlL >= 0 ? paddingRtlL : layerState.mPaddingLeft;
        final int paddingR = paddingRtlR >= 0 ? paddingRtlR : layerState.mPaddingRight;

        if (paddingL >= 0) {
            padding.left = paddingL;
        }

        if (paddingT >= 0) {
            padding.top = paddingT;
        }

        if (paddingR >= 0) {
            padding.right = paddingR;
        }

        if (paddingB >= 0) {
            padding.bottom = paddingB;
        }

        return padding.left != 0 || padding.top != 0 || padding.right != 0 || padding.bottom != 0;
    }

    public void setPadding(int left, int top, int right, int bottom) {
        final LayerState layerState = mLayerState;
        layerState.mPaddingLeft = left;
        layerState.mPaddingTop = top;
        layerState.mPaddingRight = right;
        layerState.mPaddingBottom = bottom;

        layerState.mPaddingStart = -1;
        layerState.mPaddingEnd = -1;
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) {
        final LayerState layerState = mLayerState;
        layerState.mPaddingStart = start;
        layerState.mPaddingTop = top;
        layerState.mPaddingEnd = end;
        layerState.mPaddingBottom = bottom;

        layerState.mPaddingLeft = -1;
        layerState.mPaddingRight = -1;
    }

    public int getLeftPadding() {
        return mLayerState.mPaddingLeft;
    }

    public int getRightPadding() {
        return mLayerState.mPaddingRight;
    }

    public int getStartPadding() {
        return mLayerState.mPaddingStart;
    }

    public int getEndPadding() {
        return mLayerState.mPaddingEnd;
    }

    public int getTopPadding() {
        return mLayerState.mPaddingTop;
    }

    public int getBottomPadding() {
        return mLayerState.mPaddingBottom;
    }

    private void computeNestedPadding(Rect padding) {
        padding.left = 0;
        padding.top = 0;
        padding.right = 0;
        padding.bottom = 0;

        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            refreshChildPadding(i, array[i]);

            padding.left += mPaddingL[i];
            padding.top += mPaddingT[i];
            padding.right += mPaddingR[i];
            padding.bottom += mPaddingB[i];
        }
    }

    private void computeStackedPadding(Rect padding) {
        padding.left = 0;
        padding.top = 0;
        padding.right = 0;
        padding.bottom = 0;

        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            refreshChildPadding(i, array[i]);

            padding.left = Math.max(padding.left, mPaddingL[i]);
            padding.top = Math.max(padding.top, mPaddingT[i]);
            padding.right = Math.max(padding.right, mPaddingR[i]);
            padding.bottom = Math.max(padding.bottom, mPaddingB[i]);
        }
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.getOutline(outline);
                if (!outline.isEmpty()) {
                    return;
                }
            }
        }
    }

    @Override
    public void setHotspot(float x, float y) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setHotspot(x, y);
            }
        }
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setHotspotBounds(left, top, right, bottom);
            }
        }

        if (mHotspotBounds == null) {
            mHotspotBounds = new Rect(left, top, right, bottom);
        } else {
            mHotspotBounds.set(left, top, right, bottom);
        }
    }

    @Override
    public void getHotspotBounds(Rect outRect) {
        if (mHotspotBounds != null) {
            outRect.set(mHotspotBounds);
        } else {
            super.getHotspotBounds(outRect);
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setVisible(visible, restart);
            }
        }

        return changed;
    }

    @Override
    public void setDither(boolean dither) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setDither(dither);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setAlpha(alpha);
            }
        }
    }

    @Override
    public int getAlpha() {
        final Drawable dr = getFirstNonNullDrawable();
        if (dr != null) {
            return dr.getAlpha();
        } else {
            return super.getAlpha();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setColorFilter(colorFilter);
            }
        }
    }

    @Override
    public void setTintList(ColorStateList tint) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setTintList(tint);
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Override
    public void setTintBlendMode(@NonNull BlendMode blendMode) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setTintBlendMode(blendMode);
            }
        }
    }

    private Drawable getFirstNonNullDrawable() {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                return dr;
            }
        }
        return null;
    }

    public void setOpacity(int opacity) {
        mLayerState.mOpacityOverride = opacity;
    }

    @Override
    public int getOpacity() {
        if (mLayerState.mOpacityOverride != PixelFormat.UNKNOWN) {
            return mLayerState.mOpacityOverride;
        }
        return mLayerState.getOpacity();
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        mLayerState.mAutoMirrored = mirrored;

        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setAutoMirrored(mirrored);
            }
        }
    }

    @Override
    public boolean isAutoMirrored() {
        return mLayerState.mAutoMirrored;
    }

    @Override
    public void jumpToCurrentState() {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.jumpToCurrentState();
            }
        }
    }

    @Override
    public boolean isStateful() {
        return mLayerState.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean changed = false;

        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null && dr.isStateful() && dr.setState(state)) {
                refreshChildPadding(i, array[i]);
                changed = true;
            }
        }

        if (changed) {
            updateLayerBounds(getBounds());
        }

        return changed;
    }

    @Override
    protected boolean onLevelChange(int level) {
        boolean changed = false;

        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null && dr.setLevel(level)) {
                refreshChildPadding(i, array[i]);
                changed = true;
            }
        }

        if (changed) {
            updateLayerBounds(getBounds());
        }

        return changed;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        updateLayerBounds(bounds);
    }

    private void updateLayerBounds(Rect bounds) {
        try {
            suspendChildInvalidation();
            updateLayerBoundsInternal(bounds);
        } finally {
            resumeChildInvalidation();
        }
    }

    private void updateLayerBoundsInternal(Rect bounds) {
        int paddingL = 0;
        int paddingT = 0;
        int paddingR = 0;
        int paddingB = 0;

        final Rect outRect = mTmpOutRect;
        final int layoutDirection = DrawableCompat.getLayoutDirection(this);
        final boolean isLayoutRtl = layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;
        final boolean isPaddingNested = mLayerState.mPaddingMode == PADDING_MODE_NEST;
        final ChildDrawable[] array = mLayerState.mChildren;

        for (int i = 0, count = mLayerState.mNumChildren; i < count; i++) {
            final ChildDrawable r = array[i];
            final Drawable d = r.mDrawable;
            if (d == null) {
                continue;
            }

            final int insetT = r.mInsetT;
            final int insetB = r.mInsetB;

            final int insetRtlL = isLayoutRtl ? r.mInsetE : r.mInsetS;
            final int insetRtlR = isLayoutRtl ? r.mInsetS : r.mInsetE;
            final int insetL = insetRtlL == INSET_UNDEFINED ? r.mInsetL : insetRtlL;
            final int insetR = insetRtlR == INSET_UNDEFINED ? r.mInsetR : insetRtlR;

            final Rect container = mTmpContainer;
            container.set(bounds.left + insetL + paddingL, bounds.top + insetT + paddingT,
                    bounds.right - insetR - paddingR, bounds.bottom - insetB - paddingB);

            final int intrinsicW = d.getIntrinsicWidth();
            final int intrinsicH = d.getIntrinsicHeight();
            final int layerW = r.mWidth;
            final int layerH = r.mHeight;
            final int gravity = resolveGravity(r.mGravity, layerW, layerH, intrinsicW, intrinsicH);

            final int resolvedW = layerW < 0 ? intrinsicW : layerW;
            final int resolvedH = layerH < 0 ? intrinsicH : layerH;
            Gravity.apply(gravity, resolvedW, resolvedH, container, outRect, layoutDirection);
            d.setBounds(outRect);

            if (isPaddingNested) {
                paddingL += mPaddingL[i];
                paddingR += mPaddingR[i];
                paddingT += mPaddingT[i];
                paddingB += mPaddingB[i];
            }
        }
    }

    private static int resolveGravity(int gravity, int width, int height,
                                      int intrinsicWidth, int intrinsicHeight) {
        if (!Gravity.isHorizontal(gravity)) {
            if (width < 0) {
                gravity |= Gravity.FILL_HORIZONTAL;
            } else {
                gravity |= Gravity.START;
            }
        }

        if (!Gravity.isVertical(gravity)) {
            if (height < 0) {
                gravity |= Gravity.FILL_VERTICAL;
            } else {
                gravity |= Gravity.TOP;
            }
        }

        if (width < 0 && intrinsicWidth < 0) {
            gravity |= Gravity.FILL_HORIZONTAL;
        }

        if (height < 0 && intrinsicHeight < 0) {
            gravity |= Gravity.FILL_VERTICAL;
        }

        return gravity;
    }

    @Override
    public int getIntrinsicWidth() {
        int width = -1;
        int padL = 0;
        int padR = 0;

        final boolean nest = mLayerState.mPaddingMode == PADDING_MODE_NEST;
        final boolean isLayoutRtl = DrawableCompat.getLayoutDirection(this)
                == ViewCompat.LAYOUT_DIRECTION_RTL;
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final ChildDrawable r = array[i];
            if (r.mDrawable == null) {
                continue;
            }

            final int insetRtlL = isLayoutRtl ? r.mInsetE : r.mInsetS;
            final int insetRtlR = isLayoutRtl ? r.mInsetS : r.mInsetE;
            final int insetL = insetRtlL == INSET_UNDEFINED ? r.mInsetL : insetRtlL;
            final int insetR = insetRtlR == INSET_UNDEFINED ? r.mInsetR : insetRtlR;

            final int minWidth = r.mWidth < 0 ? r.mDrawable.getIntrinsicWidth() : r.mWidth;
            final int w = minWidth < 0 ? -1 : minWidth + insetL + insetR + padL + padR;
            if (w > width) {
                width = w;
            }

            if (nest) {
                padL += mPaddingL[i];
                padR += mPaddingR[i];
            }
        }

        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        int height = -1;
        int padT = 0;
        int padB = 0;

        final boolean nest = mLayerState.mPaddingMode == PADDING_MODE_NEST;
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final ChildDrawable r = array[i];
            if (r.mDrawable == null) {
                continue;
            }

            final int minHeight = r.mHeight < 0 ? r.mDrawable.getIntrinsicHeight() : r.mHeight;
            final int h = minHeight < 0 ? -1 : minHeight + r.mInsetT + r.mInsetB + padT + padB;
            if (h > height) {
                height = h;
            }

            if (nest) {
                padT += mPaddingT[i];
                padB += mPaddingB[i];
            }
        }

        return height;
    }

    private boolean refreshChildPadding(int i, ChildDrawable r) {
        if (r.mDrawable != null) {
            final Rect rect = mTmpRect;
            r.mDrawable.getPadding(rect);
            if (rect.left != mPaddingL[i] || rect.top != mPaddingT[i]
                    || rect.right != mPaddingR[i] || rect.bottom != mPaddingB[i]) {
                mPaddingL[i] = rect.left;
                mPaddingT[i] = rect.top;
                mPaddingR[i] = rect.right;
                mPaddingB[i] = rect.bottom;
                return true;
            }
        }
        return false;
    }

    void ensurePadding() {
        final int N = mLayerState.mNumChildren;
        if (mPaddingL != null && mPaddingL.length >= N) {
            return;
        }

        mPaddingL = new int[N];
        mPaddingT = new int[N];
        mPaddingR = new int[N];
        mPaddingB = new int[N];
    }

    void refreshPadding() {
        final int N = mLayerState.mNumChildren;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < N; i++) {
            refreshChildPadding(i, array[i]);
        }
    }

    @Override
    public ConstantState getConstantState() {
        if (mLayerState.canConstantState()) {
            mLayerState.mChangingConfigurations = getChangingConfigurations();
            return mLayerState;
        }
        return null;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mLayerState = createConstantState(mLayerState, null);
            final ChildDrawable[] array = mLayerState.mChildren;
            final int N = mLayerState.mNumChildren;
            for (int i = 0; i < N; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null) {
                    dr.mutate();
                }
            }
            mMutated = true;
        }
        return this;
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        boolean changed = false;

        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                changed |= DrawableCompat.setLayoutDirection(dr, layoutDirection);
            }
        }

        updateLayerBounds(getBounds());
        return changed;
    }

    static class ChildDrawable {
        public Drawable mDrawable;
        public int[] mThemeAttrs;
        public int mDensity = DisplayMetrics.DENSITY_DEFAULT;
        public int mInsetL, mInsetT, mInsetR, mInsetB;
        public int mInsetS = INSET_UNDEFINED;
        public int mInsetE = INSET_UNDEFINED;
        public int mWidth = -1;
        public int mHeight = -1;
        public int mGravity = Gravity.NO_GRAVITY;
        public int mId = View.NO_ID;

        ChildDrawable(int density) {
            mDensity = density;
        }

        ChildDrawable(@NonNull ChildDrawable orig, @NonNull LayerDrawableCompat owner,
                      @Nullable Resources res) {
            final Drawable dr = orig.mDrawable;
            final Drawable clone;
            if (dr != null) {
                final ConstantState cs = dr.getConstantState();
                if (cs == null) {
                    clone = dr;
                    if (dr.getCallback() != null) {
                        Log.w(LOG_TAG, "Invalid drawable added to LayerDrawable! Drawable already "
                                        + "belongs to another owner but does not expose a constant state.",
                                new RuntimeException());
                    }
                } else if (res != null) {
                    clone = cs.newDrawable(res);
                } else {
                    clone = cs.newDrawable();
                }
                DrawableCompat.setLayoutDirection(clone, DrawableCompat.getLayoutDirection(dr));
                clone.setBounds(dr.getBounds());
                clone.setLevel(dr.getLevel());

                clone.setCallback(owner);
            } else {
                clone = null;
            }

            mDrawable = clone;
            mThemeAttrs = orig.mThemeAttrs;
            mInsetL = orig.mInsetL;
            mInsetT = orig.mInsetT;
            mInsetR = orig.mInsetR;
            mInsetB = orig.mInsetB;
            mInsetS = orig.mInsetS;
            mInsetE = orig.mInsetE;
            mWidth = orig.mWidth;
            mHeight = orig.mHeight;
            mGravity = orig.mGravity;
            mId = orig.mId;

            mDensity = Drawable_resolveDensity(res, orig.mDensity);
            if (orig.mDensity != mDensity) {
                applyDensityScaling(orig.mDensity, mDensity);
            }
        }

        public boolean canApplyTheme() {
            return mThemeAttrs != null
                    || (mDrawable != null && mDrawable.canApplyTheme());
        }

        public final void setDensity(int targetDensity) {
            if (mDensity != targetDensity) {
                final int sourceDensity = mDensity;
                mDensity = targetDensity;

                applyDensityScaling(sourceDensity, targetDensity);
            }
        }

        private void applyDensityScaling(int sourceDensity, int targetDensity) {
            mInsetL = Drawable_scaleFromDensity(mInsetL, sourceDensity, targetDensity, false);
            mInsetT = Drawable_scaleFromDensity(mInsetT, sourceDensity, targetDensity, false);
            mInsetR = Drawable_scaleFromDensity(mInsetR, sourceDensity, targetDensity, false);
            mInsetB = Drawable_scaleFromDensity(mInsetB, sourceDensity, targetDensity, false);
            if (mInsetS != INSET_UNDEFINED) {
                mInsetS = Drawable_scaleFromDensity(mInsetS, sourceDensity, targetDensity, false);
            }
            if (mInsetE != INSET_UNDEFINED) {
                mInsetE = Drawable_scaleFromDensity(mInsetE, sourceDensity, targetDensity, false);
            }
            if (mWidth > 0) {
                mWidth = Drawable_scaleFromDensity(mWidth, sourceDensity, targetDensity, true);
            }
            if (mHeight > 0) {
                mHeight = Drawable_scaleFromDensity(mHeight, sourceDensity, targetDensity, true);
            }
        }
    }

    static class LayerState extends ConstantState {
        private int[] mThemeAttrs;

        int mNumChildren;
        ChildDrawable[] mChildren;

        int mDensity;

        int mPaddingTop = -1;
        int mPaddingBottom = -1;
        int mPaddingLeft = -1;
        int mPaddingRight = -1;
        int mPaddingStart = -1;
        int mPaddingEnd = -1;
        int mOpacityOverride = PixelFormat.UNKNOWN;

        int mChangingConfigurations;
        int mChildrenChangingConfigurations;

        private boolean mCheckedOpacity;
        private int mOpacity;

        private boolean mCheckedStateful;
        private boolean mIsStateful;

        private boolean mAutoMirrored = false;

        private int mPaddingMode = PADDING_MODE_NEST;

        LayerState(@Nullable LayerState orig, @NonNull LayerDrawableCompat owner,
                   @Nullable Resources res) {
            mDensity = Drawable_resolveDensity(res, orig != null ? orig.mDensity : 0);

            if (orig != null) {
                final ChildDrawable[] origChildDrawable = orig.mChildren;
                final int N = orig.mNumChildren;

                mNumChildren = N;
                mChildren = new ChildDrawable[N];

                mChangingConfigurations = orig.mChangingConfigurations;
                mChildrenChangingConfigurations = orig.mChildrenChangingConfigurations;

                for (int i = 0; i < N; i++) {
                    final ChildDrawable or = origChildDrawable[i];
                    mChildren[i] = new ChildDrawable(or, owner, res);
                }

                mCheckedOpacity = orig.mCheckedOpacity;
                mOpacity = orig.mOpacity;
                mCheckedStateful = orig.mCheckedStateful;
                mIsStateful = orig.mIsStateful;
                mAutoMirrored = orig.mAutoMirrored;
                mPaddingMode = orig.mPaddingMode;
                mThemeAttrs = orig.mThemeAttrs;
                mPaddingTop = orig.mPaddingTop;
                mPaddingBottom = orig.mPaddingBottom;
                mPaddingLeft = orig.mPaddingLeft;
                mPaddingRight = orig.mPaddingRight;
                mPaddingStart = orig.mPaddingStart;
                mPaddingEnd = orig.mPaddingEnd;
                mOpacityOverride = orig.mOpacityOverride;

                if (orig.mDensity != mDensity) {
                    applyDensityScaling(orig.mDensity, mDensity);
                }
            } else {
                mNumChildren = 0;
                mChildren = null;
            }
        }

        public final void setDensity(int targetDensity) {
            if (mDensity != targetDensity) {
                final int sourceDensity = mDensity;
                mDensity = targetDensity;

                onDensityChanged(sourceDensity, targetDensity);
            }
        }

        protected void onDensityChanged(int sourceDensity, int targetDensity) {
            applyDensityScaling(sourceDensity, targetDensity);
        }

        private void applyDensityScaling(int sourceDensity, int targetDensity) {
            if (mPaddingLeft > 0) {
                mPaddingLeft = Drawable_scaleFromDensity(
                        mPaddingLeft, sourceDensity, targetDensity, false);
            }
            if (mPaddingTop > 0) {
                mPaddingTop = Drawable_scaleFromDensity(
                        mPaddingTop, sourceDensity, targetDensity, false);
            }
            if (mPaddingRight > 0) {
                mPaddingRight = Drawable_scaleFromDensity(
                        mPaddingRight, sourceDensity, targetDensity, false);
            }
            if (mPaddingBottom > 0) {
                mPaddingBottom = Drawable_scaleFromDensity(
                        mPaddingBottom, sourceDensity, targetDensity, false);
            }
            if (mPaddingStart > 0) {
                mPaddingStart = Drawable_scaleFromDensity(
                        mPaddingStart, sourceDensity, targetDensity, false);
            }
            if (mPaddingEnd > 0) {
                mPaddingEnd = Drawable_scaleFromDensity(
                        mPaddingEnd, sourceDensity, targetDensity, false);
            }
        }

        @Override
        public boolean canApplyTheme() {
            if (mThemeAttrs != null || super.canApplyTheme()) {
                return true;
            }

            final ChildDrawable[] array = mChildren;
            final int N = mNumChildren;
            for (int i = 0; i < N; i++) {
                final ChildDrawable layer = array[i];
                if (layer.canApplyTheme()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public Drawable newDrawable() {
            return new LayerDrawableCompat(this, null);
        }

        @Override
        public Drawable newDrawable(@Nullable Resources res) {
            return new LayerDrawableCompat(this, res);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations
                    | mChildrenChangingConfigurations;
        }

        public final int getOpacity() {
            if (mCheckedOpacity) {
                return mOpacity;
            }

            final int N = mNumChildren;
            final ChildDrawable[] array = mChildren;

            int firstIndex = -1;
            for (int i = 0; i < N; i++) {
                if (array[i].mDrawable != null) {
                    firstIndex = i;
                    break;
                }
            }

            int op;
            if (firstIndex >= 0) {
                op = array[firstIndex].mDrawable.getOpacity();
            } else {
                op = PixelFormat.TRANSPARENT;
            }

            for (int i = firstIndex + 1; i < N; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null) {
                    op = Drawable.resolveOpacity(op, dr.getOpacity());
                }
            }

            mOpacity = op;
            mCheckedOpacity = true;
            return op;
        }

        public final boolean isStateful() {
            if (mCheckedStateful) {
                return mIsStateful;
            }

            final int N = mNumChildren;
            final ChildDrawable[] array = mChildren;
            boolean isStateful = false;
            for (int i = 0; i < N; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null && dr.isStateful()) {
                    isStateful = true;
                    break;
                }
            }

            mIsStateful = isStateful;
            mCheckedStateful = true;
            return isStateful;
        }

        public final boolean canConstantState() {
            final ChildDrawable[] array = mChildren;
            final int N = mNumChildren;
            for (int i = 0; i < N; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null && dr.getConstantState() == null) {
                    return false;
                }
            }

            return true;
        }

        void invalidateCache() {
            mCheckedOpacity = false;
            mCheckedStateful = false;
        }

    }

    private static int Drawable_resolveDensity(@Nullable Resources r, int parentDensity) {
        final int densityDpi = r == null ? parentDensity : r.getDisplayMetrics().densityDpi;
        return densityDpi == 0 ? DisplayMetrics.DENSITY_DEFAULT : densityDpi;
    }

    private static int Drawable_scaleFromDensity(int pixels, int sourceDensity, int targetDensity,
                                                 boolean isSize) {
        if (pixels == 0 || sourceDensity == targetDensity) {
            return pixels;
        }

        final float result = pixels * targetDensity / (float) sourceDensity;
        if (!isSize) {
            return (int) result;
        }

        final int rounded = Math.round(result);
        if (rounded != 0) {
            return rounded;
        } else if (pixels > 0) {
            return 1;
        } else {
            return -1;
        }
    }

    private static boolean DrawableCompat_isProjected(@NonNull Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return drawable.isProjected();
        } else {
            return false;
        }
    }
}