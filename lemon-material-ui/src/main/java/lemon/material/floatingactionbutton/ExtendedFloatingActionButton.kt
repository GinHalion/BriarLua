package lemon.material.floatingactionbutton

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.app.Activity
import android.widget.FrameLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import lemon.material.R
import lemon.material.Material
import lemon.utils.SizeUtil
import lemon.utils.ColorUtil

class ExtendedFloatingActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val card: MaterialCardView = MaterialCardView(context)
    private val fab: ExtendedFloatingActionButton = ExtendedFloatingActionButton(context)
    private val fabAnimator = FloatingActionButtonAnimator(card)

    var icon: Drawable? = null
        set(value) {
            fab.icon = value
        }

    var text: String? = null
        set(value) {
            fab.text = value
        }

    var backgroundTint: ColorStateList? = null
        set(value) {
            fab.backgroundTintList = value
        }

    init {

        addView(card)
        card.addView(fab)

        fabAnimator.init()

        card.setStrokeWidth(SizeUtil.dp2px(context, 1))
        card.setStrokeColor(ColorUtil.getColor2(context, R.color.colorFabBorder))
        card.setCardElevation(0f)

        var attributes = context.obtainStyledAttributes(
            attrs, R.styleable.ExtendedFloatingActionButton
        )
        icon = attributes.getDrawable(R.styleable.ExtendedFloatingActionButton_icon)
        text = attributes.getString(R.styleable.ExtendedFloatingActionButton_android_text)
        backgroundTint = attributes.getColorStateList(R.styleable.ExtendedFloatingActionButton_backgroundTint)
        attributes.recycle()

        if (Material.isMaterial3Enabled(context)) {
            card.setRadius(SizeUtil.dp2px(context, 16).toFloat())
            fab.setBackgroundTintList(ColorStateList.valueOf(ColorUtil.getColor(context, "colorPrimaryContainer")))
        } else {
            card.setRadius(SizeUtil.dp2px(context, 360).toFloat())
            fab.setBackgroundTintList(ColorStateList.valueOf(ColorUtil.getColor(context, "colorPrimary")))
        }

    }

    fun setIcon(drawable: Any) {
        if (drawable is Drawable) {
            fab.icon = drawable
        } else if (drawable is Int) {
            fab.setIconResource(drawable)
        }
    }

    fun setBackgroundColor(colorStateList: ColorStateList) {
        fab.backgroundTintList = colorStateList
    }

    fun show() {
        fabAnimator.show()
    }

    fun hide() {
        fabAnimator.hide()
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        fab.setOnClickListener(onClickListener)
    }

    override fun setOnLongClickListener(onLongClickListener: OnLongClickListener?) {
        fab.setOnLongClickListener(onLongClickListener)
    }

}