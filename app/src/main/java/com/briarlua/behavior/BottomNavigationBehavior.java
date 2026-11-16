package com.briarlua.behavior;


import com.google.android.material.R;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.core.content.ContextCompat.getSystemService;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior;
import androidx.core.view.ViewCompat;
import com.google.android.material.animation.AnimationUtils;
import com.google.android.material.motion.MotionUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashSet;

@Deprecated
public class BottomNavigationBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {

  public interface OnScrollStateChangedListener {

    void onStateChanged(@NonNull View bottomView, @ScrollState int newState);
  }

  @NonNull
  private final LinkedHashSet<OnScrollStateChangedListener> onScrollStateChangedListeners =
      new LinkedHashSet<>();

  private static final int DEFAULT_ENTER_ANIMATION_DURATION_MS = 225;
  private static final int DEFAULT_EXIT_ANIMATION_DURATION_MS = 175;
  private static final int ENTER_ANIM_DURATION_ATTR = R.attr.motionDurationLong2;
  private static final int EXIT_ANIM_DURATION_ATTR = R.attr.motionDurationMedium4;
  private static final int ENTER_EXIT_ANIM_EASING_ATTR = R.attr.motionEasingEmphasizedInterpolator;

  private int enterAnimDuration;
  private int exitAnimDuration;
  private TimeInterpolator enterAnimInterpolator;
  private TimeInterpolator exitAnimInterpolator;

  public static final int STATE_SCROLLED_DOWN = 1;

  public static final int STATE_SCROLLED_UP = 2;

  private int height = 0;

  private AccessibilityManager accessibilityManager;
  private TouchExplorationStateChangeListener touchExplorationListener;

  private boolean disableOnTouchExploration = true;

  @RestrictTo(LIBRARY_GROUP)
  @IntDef({STATE_SCROLLED_DOWN, STATE_SCROLLED_UP})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ScrollState {}

  @ScrollState private int currentState = STATE_SCROLLED_UP;
  private int additionalHiddenOffsetY = 0;
  @Nullable private ViewPropertyAnimator currentAnimator;

  private static final int SCROLL_THRESHOLD = 5;
  private int scrollThresholdReached = 0;

  public BottomNavigationBehavior() {}

  public BottomNavigationBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public boolean onLayoutChild(
      @NonNull CoordinatorLayout parent, @NonNull V child, int layoutDirection) {
    ViewGroup.MarginLayoutParams paramsCompat =
        (ViewGroup.MarginLayoutParams) child.getLayoutParams();
    height = child.getMeasuredHeight() + paramsCompat.bottomMargin;
    enterAnimDuration =
        MotionUtils.resolveThemeDuration(
            child.getContext(), ENTER_ANIM_DURATION_ATTR, DEFAULT_ENTER_ANIMATION_DURATION_MS);
    exitAnimDuration =
        MotionUtils.resolveThemeDuration(
            child.getContext(), EXIT_ANIM_DURATION_ATTR, DEFAULT_EXIT_ANIMATION_DURATION_MS);
    enterAnimInterpolator =
        MotionUtils.resolveThemeInterpolator(
            child.getContext(),
            ENTER_EXIT_ANIM_EASING_ATTR,
            AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR);
    exitAnimInterpolator =
        MotionUtils.resolveThemeInterpolator(
            child.getContext(),
            ENTER_EXIT_ANIM_EASING_ATTR,
            AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR);
    disableIfTouchExplorationEnabled(child);
    return super.onLayoutChild(parent, child, layoutDirection);
  }

  private void disableIfTouchExplorationEnabled(V child) {
    if (accessibilityManager == null) {
      accessibilityManager = getSystemService(child.getContext(), AccessibilityManager.class);
    }
    if (accessibilityManager != null && touchExplorationListener == null) {
      touchExplorationListener =
          enabled -> {
            if (enabled && isScrolledDown()) {
              slideUp(child);
            }
          };
      accessibilityManager.addTouchExplorationStateChangeListener(touchExplorationListener);
      child.addOnAttachStateChangeListener(
          new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {}

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
              if (touchExplorationListener != null && accessibilityManager != null) {
                accessibilityManager.removeTouchExplorationStateChangeListener(
                    touchExplorationListener);
                touchExplorationListener = null;
              }
            }
          });
    }
  }

  public void setAdditionalHiddenOffsetY(@NonNull V child, @Dimension int offset) {
    additionalHiddenOffsetY = offset;

    if (currentState == STATE_SCROLLED_DOWN) {
      child.setTranslationY(height + additionalHiddenOffsetY);
    }
  }

  @Override
  public boolean onStartNestedScroll(
      @NonNull CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View directTargetChild,
      @NonNull View target,
      int nestedScrollAxes,
      int type) {
    return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
  }

  @Override
  public void onNestedScroll(
      CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View target,
      int dxConsumed,
      int dyConsumed,
      int dxUnconsumed,
      int dyUnconsumed,
      int type,
      @NonNull int[] consumed) {
    
    if (height == 0) {
      ViewGroup.MarginLayoutParams paramsCompat =
          (ViewGroup.MarginLayoutParams) child.getLayoutParams();
      height = child.getMeasuredHeight() + paramsCompat.bottomMargin;
    }
    
    int totalScrollY = dyConsumed + dyUnconsumed;
    scrollThresholdReached += totalScrollY;
    
    if (scrollThresholdReached > SCROLL_THRESHOLD) {
      slideDown(child);
      scrollThresholdReached = 0;
    } else if (scrollThresholdReached < -SCROLL_THRESHOLD) {
      slideUp(child);
      scrollThresholdReached = 0;
    }
  }

  public boolean isScrolledUp() {
    return currentState == STATE_SCROLLED_UP;
  }

  public void slideUp(@NonNull V child) {
    slideUp(child,   true);
  }

  public void slideUp(@NonNull V child, boolean animate) {
    if (isScrolledUp()) {
      return;
    }

    if (currentAnimator != null) {
      currentAnimator.cancel();
      child.clearAnimation();
    }
    updateCurrentState(child, STATE_SCROLLED_UP);
    int targetTranslationY = 0;
    if (animate) {
      animateChildTo(child, targetTranslationY, enterAnimDuration, enterAnimInterpolator);
    } else {
      child.setTranslationY(targetTranslationY);
    }
  }

  public boolean isScrolledDown() {
    return currentState == STATE_SCROLLED_DOWN;
  }

  public void slideDown(@NonNull V child) {
    slideDown(child,   true);
  }

  public void slideDown(@NonNull V child, boolean animate) {
    if (isScrolledDown()) {
      return;
    }

    if (disableOnTouchExploration
        && accessibilityManager != null
        && accessibilityManager.isTouchExplorationEnabled()) {
      return;
    }

    if (currentAnimator != null) {
      currentAnimator.cancel();
      child.clearAnimation();
    }
    updateCurrentState(child, STATE_SCROLLED_DOWN);
    int targetTranslationY = height + additionalHiddenOffsetY;
    if (animate) {
      animateChildTo(child, targetTranslationY, exitAnimDuration, exitAnimInterpolator);
    } else {
      child.setTranslationY(targetTranslationY);
    }
  }

  private void updateCurrentState(@NonNull V child, @ScrollState int state) {
    currentState = state;
    for (OnScrollStateChangedListener listener : onScrollStateChangedListeners) {
      listener.onStateChanged(child, currentState);
    }
  }

  private void animateChildTo(
      @NonNull V child, int targetY, long duration, TimeInterpolator interpolator) {
    currentAnimator =
        child
            .animate()
            .translationY(targetY)
            .setInterpolator(interpolator)
            .setDuration(duration)
            .setListener(
                new AnimatorListenerAdapter() {
                  @Override
                  public void onAnimationEnd(Animator animation) {
                    currentAnimator = null;
                  }
                });
  }

  public void addOnScrollStateChangedListener(@NonNull OnScrollStateChangedListener listener) {
    onScrollStateChangedListeners.add(listener);
  }

  public void removeOnScrollStateChangedListener(@NonNull OnScrollStateChangedListener listener) {
    onScrollStateChangedListeners.remove(listener);
  }

  public void clearOnScrollStateChangedListeners() {
    onScrollStateChangedListeners.clear();
  }

  public void disableOnTouchExploration(boolean disableOnTouchExploration) {
    this.disableOnTouchExploration = disableOnTouchExploration;
  }

  public boolean isDisabledOnTouchExploration() {
    return disableOnTouchExploration;
  }
}