
package lemon.material.swiperefreshlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.ListView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.view.NestedScrollingChild;
import androidx.core.view.NestedScrollingChild2;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParent2;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

public class SwipeRefreshLayout extends ViewGroup implements NestedScrollingParent3,
        NestedScrollingParent2, NestedScrollingChild3, NestedScrollingChild2, NestedScrollingParent,
        NestedScrollingChild {
    public static final int LARGE = CircularProgressDrawable.LARGE;
    public static final int DEFAULT = CircularProgressDrawable.DEFAULT;

    public static final int DEFAULT_SLINGSHOT_DISTANCE = -1;

    @VisibleForTesting
    static final int CIRCLE_DIAMETER = 40;
    @VisibleForTesting
    static final int CIRCLE_DIAMETER_LARGE = 56;

    private static final String LOG_TAG = SwipeRefreshLayout.class.getSimpleName();

    private static final int MAX_ALPHA = 255;
    private static final int STARTING_PROGRESS_ALPHA = (int) (.3f * MAX_ALPHA);

    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int INVALID_POINTER = -1;
    private static final float DRAG_RATE = .5f;

    private static final float MAX_PROGRESS_ANGLE = .8f;

    private static final int SCALE_DOWN_DURATION = 150;

    private static final int ALPHA_ANIMATION_DURATION = 300;

    private static final int ANIMATE_TO_TRIGGER_DURATION = 200;

    private static final int ANIMATE_TO_START_DURATION = 200;

    private static final int DEFAULT_CIRCLE_TARGET = 64;

    private View mTarget;
    OnRefreshListener mListener;
    boolean mRefreshing = false;
    private int mTouchSlop;
    private float mTotalDragDistance = -1;

    private float mTotalUnconsumed;
    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];

    private final int[] mNestedScrollingV2ConsumedCompat = new int[2];
    private boolean mNestedScrollInProgress;

    private int mMediumAnimationDuration;
    int mCurrentTargetOffsetTop;

    private float mInitialMotionY;
    private float mInitialDownY;
    private boolean mIsBeingDragged;
    private int mActivePointerId = INVALID_POINTER;
    boolean mScale;

    private boolean mReturningToStart;
    private final DecelerateInterpolator mDecelerateInterpolator;
    private static final int[] LAYOUT_ATTRS = new int[] {
            android.R.attr.enabled
    };

    CircleImageView mCircleView;
    private int mCircleViewIndex = -1;

    protected int mFrom;

    float mStartingScale;

    protected int mOriginalOffsetTop;

    int mSpinnerOffsetEnd;

    int mCustomSlingshotDistance;

    CircularProgressDrawable mProgress;

    private Animation mScaleAnimation;

    private Animation mScaleDownAnimation;

    private Animation mAlphaStartAnimation;

    private Animation mAlphaMaxAnimation;

    private Animation mScaleDownToStartAnimation;

    boolean mNotify;

    private int mCircleDiameter;

    boolean mUsingCustomStart;

    private OnChildScrollUpCallback mChildScrollUpCallback;

    private boolean mEnableLegacyRequestDisallowInterceptTouch;

    private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            if (mRefreshing) {
                mProgress.setAlpha(MAX_ALPHA);
                mProgress.start();
                if (mNotify) {
                    if (mListener != null) {
                        mListener.onRefresh();
                    }
                }
                mCurrentTargetOffsetTop = mCircleView.getTop();
            } else {
                reset();
            }
        }
    };

    void reset() {
        mCircleView.clearAnimation();
        mProgress.stop();
        mCircleView.setVisibility(View.GONE);
        setColorViewAlpha(MAX_ALPHA);
        if (mScale) {
            setAnimationProgress(0 );
        } else {
            setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCurrentTargetOffsetTop);
        }
        mCurrentTargetOffsetTop = mCircleView.getTop();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            reset();
        }
    }

    @Override
    public boolean dispatchKeyEvent(@Nullable KeyEvent event) {
        if (event != null && event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == KeyEvent.KEYCODE_REFRESH) {
            setRefreshingWithoutSwipeGesture(true , true );
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    static class SavedState extends View.BaseSavedState {
        final boolean mRefreshing;

        SavedState(Parcelable superState, boolean refreshing) {
            super(superState);
            this.mRefreshing = refreshing;
        }

        SavedState(Parcel in) {
            super(in);
            mRefreshing = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte(mRefreshing ? (byte) 1 : (byte) 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    @Override
    protected @NonNull Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, mRefreshing);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setRefreshing(savedState.mRefreshing);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reset();
    }

    private void setColorViewAlpha(int targetAlpha) {
        mCircleView.getBackground().setAlpha(targetAlpha);
        mProgress.setAlpha(targetAlpha);
    }

    public void setProgressViewOffset(boolean scale, int start, int end) {
        mScale = scale;
        mOriginalOffsetTop = start;
        mSpinnerOffsetEnd = end;
        mUsingCustomStart = true;
        reset();
        mRefreshing = false;
    }

    public int getProgressViewStartOffset() {
        return mOriginalOffsetTop;
    }

    public int getProgressViewEndOffset() {
        return mSpinnerOffsetEnd;
    }

    public void setProgressViewEndTarget(boolean scale, int end) {
        mSpinnerOffsetEnd = end;
        mScale = scale;
        mCircleView.invalidate();
    }

    public void setSlingshotDistance(@Px int slingshotDistance) {
        mCustomSlingshotDistance = slingshotDistance;
    }

    public void setSize(int size) {
        if (size != CircularProgressDrawable.LARGE && size != CircularProgressDrawable.DEFAULT) {
            return;
        }
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (size == CircularProgressDrawable.LARGE) {
            mCircleDiameter = (int) (CIRCLE_DIAMETER_LARGE * metrics.density);
        } else {
            mCircleDiameter = (int) (CIRCLE_DIAMETER * metrics.density);
        }
        mCircleView.setImageDrawable(null);
        mProgress.setStyle(size);
        mCircleView.setImageDrawable(mProgress);
    }

    public SwipeRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public SwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mMediumAnimationDuration = getResources().getInteger(
                android.R.integer.config_mediumAnimTime);

        setWillNotDraw(false);
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        mCircleDiameter = (int) (CIRCLE_DIAMETER * metrics.density);

        createProgressView();
        setChildrenDrawingOrderEnabled(true);
        mSpinnerOffsetEnd = (int) (DEFAULT_CIRCLE_TARGET * metrics.density);
        mTotalDragDistance = mSpinnerOffsetEnd;
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);

        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);

        mOriginalOffsetTop = mCurrentTargetOffsetTop = -mCircleDiameter;
        moveToStart(1.0f);

        final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
        setEnabled(a.getBoolean(0, true));
        a.recycle();
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mCircleViewIndex < 0) {
            return i;
        } else if (i == childCount - 1) {
            return mCircleViewIndex;
        } else if (i >= mCircleViewIndex) {
            return i + 1;
        } else {
            return i;
        }
    }

    private void createProgressView() {
        mCircleView = new CircleImageView(getContext());
        mProgress = new CircularProgressDrawable(getContext());
        mProgress.setStyle(CircularProgressDrawable.DEFAULT);
        mCircleView.setImageDrawable(mProgress);
        mCircleView.setVisibility(View.GONE);
        addView(mCircleView);
    }

    public void setOnRefreshListener(@Nullable OnRefreshListener listener) {
        mListener = listener;
    }

    public void setRefreshing(boolean refreshing) {
        setRefreshingWithoutSwipeGesture(refreshing, false );
    }

    private void startScaleUpAnimation(AnimationListener listener) {
        mCircleView.setVisibility(View.VISIBLE);
        mProgress.setAlpha(MAX_ALPHA);
        mScaleAnimation = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                setAnimationProgress(interpolatedTime);
            }
        };
        mScaleAnimation.setDuration(mMediumAnimationDuration);
        if (listener != null) {
            mCircleView.setAnimationListener(listener);
        }
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mScaleAnimation);
    }

    void setAnimationProgress(float progress) {
        mCircleView.setScaleX(progress);
        mCircleView.setScaleY(progress);
    }

    private void setRefreshingWithoutSwipeGesture(boolean refreshing, final boolean notify) {
        if (refreshing && mRefreshing != refreshing) {
            mRefreshing = refreshing;
            int endTarget = 0;
            if (!mUsingCustomStart) {
                endTarget = mSpinnerOffsetEnd + mOriginalOffsetTop;
            } else {
                endTarget = mSpinnerOffsetEnd;
            }
            setTargetOffsetTopAndBottom(endTarget - mCurrentTargetOffsetTop);
            mNotify = notify;
            startScaleUpAnimation(mRefreshListener);
        } else {
            setRefreshing(refreshing, false );
        }
    }

    private void setRefreshing(boolean refreshing, final boolean notify) {
        if (mRefreshing != refreshing) {
            mNotify = notify;
            ensureTarget();
            mRefreshing = refreshing;
            if (mRefreshing) {
                animateOffsetToCorrectPosition(mCurrentTargetOffsetTop, mRefreshListener);
            } else {
                startScaleDownAnimation(mRefreshListener);
            }
        }
    }

    void startScaleDownAnimation(Animation.AnimationListener listener) {
        mScaleDownAnimation = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                setAnimationProgress(1 - interpolatedTime);
            }
        };
        mScaleDownAnimation.setDuration(SCALE_DOWN_DURATION);
        mCircleView.setAnimationListener(listener);
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mScaleDownAnimation);
    }

    private void startProgressAlphaStartAnimation() {
        mAlphaStartAnimation = startAlphaAnimation(mProgress.getAlpha(), STARTING_PROGRESS_ALPHA);
    }

    private void startProgressAlphaMaxAnimation() {
        mAlphaMaxAnimation = startAlphaAnimation(mProgress.getAlpha(), MAX_ALPHA);
    }

    private Animation startAlphaAnimation(final int startingAlpha, final int endingAlpha) {
        Animation alpha = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                mProgress.setAlpha(
                        (int) (startingAlpha + ((endingAlpha - startingAlpha) * interpolatedTime)));
            }
        };
        alpha.setDuration(ALPHA_ANIMATION_DURATION);
        mCircleView.setAnimationListener(null);
        mCircleView.clearAnimation();
        mCircleView.startAnimation(alpha);
        return alpha;
    }

    @Deprecated
    public void setProgressBackgroundColor(int colorRes) {
        setProgressBackgroundColorSchemeResource(colorRes);
    }

    public void setProgressBackgroundColorSchemeResource(@ColorRes int colorRes) {
        setProgressBackgroundColorSchemeColor(ContextCompat.getColor(getContext(), colorRes));
    }

    public void setProgressBackgroundColorSchemeColor(@ColorInt int color) {
        mCircleView.setBackgroundColor(color);
    }

    @Deprecated
    public void setColorScheme(@ColorRes int... colors) {
        setColorSchemeResources(colors);
    }

    public void setColorSchemeResources(@NonNull @ColorRes int... colorResIds) {
        final Context context = getContext();
        int[] colorRes = new int[colorResIds.length];
        for (int i = 0; i < colorResIds.length; i++) {
            colorRes[i] = ContextCompat.getColor(context, colorResIds[i]);
        }
        setColorSchemeColors(colorRes);
    }

    public void setColorSchemeColors(@NonNull @ColorInt int... colors) {

        ensureTarget();
        mProgress.setColorSchemeColors(colors);
    }

    public boolean isRefreshing() {
        return mRefreshing;
    }

    private void ensureTarget() {
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mCircleView)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    public void setDistanceToTriggerSync(int distance) {
        mTotalDragDistance = distance;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        int circleWidth = mCircleView.getMeasuredWidth();
        int circleHeight = mCircleView.getMeasuredHeight();
        mCircleView.layout((width / 2 - circleWidth / 2), mCurrentTargetOffsetTop,
                (width / 2 + circleWidth / 2), mCurrentTargetOffsetTop + circleHeight);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        mTarget.measure(MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        mCircleView.measure(MeasureSpec.makeMeasureSpec(mCircleDiameter, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mCircleDiameter, MeasureSpec.EXACTLY));
        mCircleViewIndex = -1;
        for (int index = 0; index < getChildCount(); index++) {
            if (getChildAt(index) == mCircleView) {
                mCircleViewIndex = index;
                break;
            }
        }
    }

    public int getProgressCircleDiameter() {
        return mCircleDiameter;
    }

    public boolean canChildScrollUp() {
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback.canChildScrollUp(this, mTarget);
        }
        if (mTarget instanceof ListView) {
            return ((ListView) mTarget).canScrollList(-1);
        }
        return mTarget.canScrollVertically(-1);
    }

    public void setOnChildScrollUpCallback(@Nullable OnChildScrollUpCallback callback) {
        mChildScrollUpCallback = callback;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();

        final int action = ev.getActionMasked();
        int pointerIndex;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp()
                || mRefreshing || mNestedScrollInProgress) {
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCircleView.getTop());
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                mInitialDownY = ev.getY(pointerIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                startDragging(y);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    @Deprecated
    public void setLegacyRequestDisallowInterceptTouchEventEnabled(boolean enabled) {
        mEnableLegacyRequestDisallowInterceptTouch = enabled;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        if (mEnableLegacyRequestDisallowInterceptTouch
                && ((android.os.Build.VERSION.SDK_INT < 21 && mTarget instanceof AbsListView)
                || (mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget)))) {
            return;
        }
        super.requestDisallowInterceptTouchEvent(b);
    }


    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, @ViewCompat.NestedScrollType int type,
            @NonNull int[] consumed) {
        if (type != ViewCompat.TYPE_TOUCH) {
            return;
        }

        int consumedBeforeParents = consumed[1];
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                mParentOffsetInWindow, type, consumed);
        int consumedByParents = consumed[1] - consumedBeforeParents;
        int unconsumedAfterParents = dyUnconsumed - consumedByParents;

        int remainingDistanceToScroll;
        if (unconsumedAfterParents == 0) {
            remainingDistanceToScroll = dyUnconsumed + mParentOffsetInWindow[1];
        } else {
            remainingDistanceToScroll = unconsumedAfterParents;
        }

        if (remainingDistanceToScroll < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(remainingDistanceToScroll);
            moveSpinner(mTotalUnconsumed);

            consumed[1] += unconsumedAfterParents;
        }
    }


    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes,
            int type) {
        if (type == ViewCompat.TYPE_TOUCH) {
            return onStartNestedScroll(child, target, axes);
        } else {
            return false;
        }
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes,
            int type) {
        if (type == ViewCompat.TYPE_TOUCH) {
            onNestedScrollAccepted(child, target, axes);
        }
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        if (type == ViewCompat.TYPE_TOUCH) {
            onStopNestedScroll(target);
        }
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type,
                mNestedScrollingV2ConsumedCompat);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed,
            int type) {
        if (type == ViewCompat.TYPE_TOUCH) {
            onNestedPreScroll(target, dx, dy, consumed);
        }
    }


    @Override
    public boolean onStartNestedScroll(View child, View target, int axes) {
        return isEnabled() && !mReturningToStart && !mRefreshing
                && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mTotalUnconsumed = 0;
        mNestedScrollInProgress = true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = (int) mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }
            moveSpinner(mTotalUnconsumed);
        }

        if (mUsingCustomStart && dy > 0 && mTotalUnconsumed == 0
                && Math.abs(dy - consumed[1]) > 0) {
            mCircleView.setVisibility(View.GONE);
        }

        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        mNestedScrollInProgress = false;
        if (mTotalUnconsumed > 0) {
            finishSpinner(mTotalUnconsumed);
            mTotalUnconsumed = 0;
        } else {
            post(() -> reset());
        }
        stopNestedScroll();
    }

    @Override
    public void onNestedScroll(final View target, final int dxConsumed, final int dyConsumed,
            final int dxUnconsumed, final int dyUnconsumed) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                ViewCompat.TYPE_TOUCH, mNestedScrollingV2ConsumedCompat);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX,
            float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY,
            boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }


    @Override
    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, @Nullable int[] offsetInWindow, @ViewCompat.NestedScrollType int type,
            @NonNull int[] consumed) {
        if (type == ViewCompat.TYPE_TOUCH) {
            mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed,
                    dyUnconsumed, offsetInWindow, type, consumed);
        }
    }


    @Override
    public boolean startNestedScroll(int axes, int type) {
        return type == ViewCompat.TYPE_TOUCH && startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll(int type) {
        if (type == ViewCompat.TYPE_TOUCH) {
            stopNestedScroll();
        }
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return type == ViewCompat.TYPE_TOUCH && hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return type == ViewCompat.TYPE_TOUCH && mNestedScrollingChildHelper.dispatchNestedScroll(
                dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed,
            @Nullable int[] offsetInWindow, int type) {
        return type == ViewCompat.TYPE_TOUCH && dispatchNestedPreScroll(dx, dy, consumed,
                offsetInWindow);
    }


    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, @Nullable int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed,
            @Nullable int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    private boolean isAnimationRunning(Animation animation) {
        return animation != null && animation.hasStarted() && !animation.hasEnded();
    }

    private void moveSpinner(float overscrollTop) {
        mProgress.setArrowEnabled(true);
        float originalDragPercent = overscrollTop / mTotalDragDistance;

        float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
        float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
        float extraOS = Math.abs(overscrollTop) - mTotalDragDistance;
        float slingshotDist = mCustomSlingshotDistance > 0
                ? mCustomSlingshotDistance
                : (mUsingCustomStart
                        ? mSpinnerOffsetEnd - mOriginalOffsetTop
                        : mSpinnerOffsetEnd);
        float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2)
                / slingshotDist);
        float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
                (tensionSlingshotPercent / 4), 2)) * 2f;
        float extraMove = slingshotDist * tensionPercent * 2;

        int targetY = mOriginalOffsetTop + (int) ((slingshotDist * dragPercent) + extraMove);
        if (mCircleView.getVisibility() != View.VISIBLE) {
            mCircleView.setVisibility(View.VISIBLE);
        }
        if (!mScale) {
            mCircleView.setScaleX(1f);
            mCircleView.setScaleY(1f);
        }

        if (mScale) {
            setAnimationProgress(Math.min(1f, overscrollTop / mTotalDragDistance));
        }
        if (overscrollTop < mTotalDragDistance) {
            if (mProgress.getAlpha() > STARTING_PROGRESS_ALPHA
                    && !isAnimationRunning(mAlphaStartAnimation)) {
                startProgressAlphaStartAnimation();
            }
        } else {
            if (mProgress.getAlpha() < MAX_ALPHA && !isAnimationRunning(mAlphaMaxAnimation)) {
                startProgressAlphaMaxAnimation();
            }
        }
        float strokeStart = adjustedPercent * .8f;
        mProgress.setStartEndTrim(0f, Math.min(MAX_PROGRESS_ANGLE, strokeStart));
        mProgress.setArrowScale(Math.min(1f, adjustedPercent));

        float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
        mProgress.setProgressRotation(rotation);
        setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop);
    }

    private void finishSpinner(float overscrollTop) {
        if (overscrollTop > mTotalDragDistance) {
            setRefreshing(true, true );
        } else {
            mRefreshing = false;
            mProgress.setStartEndTrim(0f, 0f);
            Animation.AnimationListener listener = null;
            if (!mScale) {
                listener = new Animation.AnimationListener() {

                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (!mScale) {
                            startScaleDownAnimation(null);
                        }
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                };
            }
            animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener);
            mProgress.setArrowEnabled(false);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        int pointerIndex = -1;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp()
                || mRefreshing || mNestedScrollInProgress) {
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                startDragging(y);

                if (mIsBeingDragged) {
                    final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    if (overscrollTop > 0) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        moveSpinner(overscrollTop);
                    } else {
                        return false;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                pointerIndex = ev.getActionIndex();
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG,
                            "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    return false;
                }

                if (mIsBeingDragged) {
                    final float y = ev.getY(pointerIndex);
                    final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    mIsBeingDragged = false;
                    finishSpinner(overscrollTop);
                }
                mActivePointerId = INVALID_POINTER;
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                return false;
        }

        return true;
    }

    private void startDragging(float y) {
        final float yDiff = y - mInitialDownY;
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
            mInitialMotionY = mInitialDownY + mTouchSlop;
            mIsBeingDragged = true;
            mProgress.setAlpha(STARTING_PROGRESS_ALPHA);
        }
    }

    private void animateOffsetToCorrectPosition(int from, AnimationListener listener) {
        mFrom = from;
        mAnimateToCorrectPosition.reset();
        mAnimateToCorrectPosition.setDuration(ANIMATE_TO_TRIGGER_DURATION);
        mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
        if (listener != null) {
            mCircleView.setAnimationListener(listener);
        }
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mAnimateToCorrectPosition);
    }

    private void animateOffsetToStartPosition(int from, AnimationListener listener) {
        if (mScale) {
            startScaleDownReturnToStartAnimation(from, listener);
        } else {
            mFrom = from;
            mAnimateToStartPosition.reset();
            mAnimateToStartPosition.setDuration(ANIMATE_TO_START_DURATION);
            mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
            if (listener != null) {
                mCircleView.setAnimationListener(listener);
            }
            mCircleView.clearAnimation();
            mCircleView.startAnimation(mAnimateToStartPosition);
        }
    }

    private final Animation mAnimateToCorrectPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int endTarget;
            if (!mUsingCustomStart) {
                endTarget = mSpinnerOffsetEnd - Math.abs(mOriginalOffsetTop);
            } else {
                endTarget = mSpinnerOffsetEnd;
            }
            int targetTop = (mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
            int offset = targetTop - mCircleView.getTop();
            setTargetOffsetTopAndBottom(offset);
            mProgress.setArrowScale(1 - interpolatedTime);
        }
    };

    void moveToStart(float interpolatedTime) {
        int targetTop = (mFrom + (int) ((mOriginalOffsetTop - mFrom) * interpolatedTime));
        int offset = targetTop - mCircleView.getTop();
        setTargetOffsetTopAndBottom(offset);
    }

    private final Animation mAnimateToStartPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };

    private void startScaleDownReturnToStartAnimation(int from,
            Animation.AnimationListener listener) {
        mFrom = from;
        mStartingScale = mCircleView.getScaleX();
        mScaleDownToStartAnimation = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                float targetScale = (mStartingScale + (-mStartingScale  * interpolatedTime));
                setAnimationProgress(targetScale);
                moveToStart(interpolatedTime);
            }
        };
        mScaleDownToStartAnimation.setDuration(SCALE_DOWN_DURATION);
        if (listener != null) {
            mCircleView.setAnimationListener(listener);
        }
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mScaleDownToStartAnimation);
    }

    void setTargetOffsetTopAndBottom(int offset) {
        mCircleView.bringToFront();
        ViewCompat.offsetTopAndBottom(mCircleView, offset);
        mCurrentTargetOffsetTop = mCircleView.getTop();
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    public interface OnRefreshListener {
        void onRefresh();
    }

    public interface OnChildScrollUpCallback {
        boolean canChildScrollUp(@NonNull SwipeRefreshLayout parent, @Nullable View child);
    }
}
