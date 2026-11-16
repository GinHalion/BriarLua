package lemon.material.drawable

import android.graphics.Rect
import android.graphics.drawable.Drawable

class BoundedDrawable(wrappedDrawable: Drawable) : DrawableWrapper(wrappedDrawable) {

    private fun setDrawableBounds(left: Int, top: Int, right: Int, bottom: Int) {
        setBounds(left, top, right, bottom)
        wrappedDrawable.setBounds(left, top, right, bottom)
    }

    var drawableBounds: Rect = wrappedDrawable.bounds
        set(value) {
            field = value
            setDrawableBounds(value.left, value.top, value.right, value.bottom)
        }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {}
    override fun setBounds(bounds: Rect) {}
}