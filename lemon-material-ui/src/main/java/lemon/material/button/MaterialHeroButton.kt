package lemon.material.button

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.R as MDC_R
import lemon.material.R
import lemon.material.Material
import lemon.utils.ColorUtil
import lemon.utils.SizeUtil

class MaterialHeroButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val customLayout = LayoutInflater.from(context).inflate(R.layout.hero_button, null)
    private val imgView = customLayout.findViewById<AppCompatImageView>(R.id.hero_icon)
    private val textView = customLayout.findViewById<AppCompatTextView>(R.id.hero_text)

    var icon: Drawable? = null
        set(value) {
            imgView.setImageDrawable(value)
        }

    var text: String? = null
        set(value) {
            textView.text = value.toString().toUpperCase()
        }

    var color: Int = ColorUtil.getColor(context, "colorPrimary")
        set(value) {
            imgView.setColorFilter(value, PorterDuff.Mode.SRC_IN)
            textView.setTextColor(value)
        }

    var rippleColor: Int? = null
        set(value) {
            val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            val typedArray = context.obtainStyledAttributes(attrs)
            val backgroundResource = typedArray.getResourceId(0, 0)
            setBackgroundResource(backgroundResource)
            val rippleDrawable = background as RippleDrawable
            rippleDrawable.setColor(ColorStateList.valueOf(value ?: ColorUtil.getColor2(context, R.color.colorRipple)))
        }

    init {
        addView(customLayout)
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.MaterialHeroButton)
        icon = attributes.getDrawable(R.styleable.MaterialHeroButton_icon)
        text = attributes.getString(R.styleable.MaterialHeroButton_android_text)
        color =
            attributes.getInt(R.styleable.MaterialHeroButton_color, ColorUtil.getColor(context, "colorPrimary"))
        rippleColor = attributes.getInt(R.styleable.MaterialHeroButton_rippleColor, ColorUtil.getColor2(context, R.color.colorRipple))
        attributes.recycle()
    }

}