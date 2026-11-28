
package lemon.material.foreground;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

interface ForegroundCompatView {

    @Nullable
    Drawable getSupportForeground();

    void setSupportForeground(@Nullable Drawable foreground);

    int getSupportForegroundGravity();

    void setSupportForegroundGravity(int gravity);

    void setSupportForegroundTintList(@Nullable ColorStateList tint);

    @Nullable
    ColorStateList getSupportForegroundTintList();

    void setSupportForegroundTintMode(@Nullable PorterDuff.Mode tintMode);

    @Nullable
    PorterDuff.Mode getSupportForegroundTintMode();
}
