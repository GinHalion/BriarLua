package com.briarlua.behavior;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class FabAboveBottomNavBehavior extends CoordinatorLayout.Behavior<FloatingActionButton> {

    private final float fabMargin;
    private final float marginEnd;

    public FabAboveBottomNavBehavior(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        this.fabMargin = 16f * density;
        this.marginEnd = 16f * density;
    }

    @Override
    public boolean layoutDependsOn(@NonNull CoordinatorLayout parent,
            @NonNull FloatingActionButton child,
            @NonNull View dependency) {
        return dependency instanceof BottomNavigationView;
    }

    @Override
    public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent,
            @NonNull FloatingActionButton child,
            @NonNull View dependency) {
        float targetY = dependency.getY() - child.getHeight() - fabMargin;
        float translationY = Math.max(targetY, 0f);

        float targetX = parent.getWidth() - child.getWidth() - marginEnd;

        boolean changed = false;
        if (child.getTranslationY() != translationY) {
            child.setTranslationY(translationY);
            changed = true;
        }
        if (child.getTranslationX() != targetX) {
            child.setTranslationX(targetX);
            changed = true;
        }
        return changed;
    }

    @Override
    public void onDependentViewRemoved(@NonNull CoordinatorLayout parent,
            @NonNull FloatingActionButton child,
            @NonNull View dependency) {
        child.setTranslationY(0f);
        child.setTranslationX(0f);
    }
}