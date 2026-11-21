package lemon.material.card

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Checkable
import android.content.res.ColorStateList
import com.google.android.material.card.MaterialCardView
import com.google.android.material.R as MDC_R
import lemon.material.R
import lemon.material.Material
import lemon.utils.ColorUtil
import lemon.utils.SizeUtil

class MaterialCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr), Checkable {
    
    init {

        setStrokeWidth(SizeUtil.dp2px(context, 1))
        setStrokeColor(ColorUtil.getColor2(context, R.color.colorBorder))
        setCardElevation(0f)
        setRadius(SizeUtil.dp2px(context, if (Material.isMaterial3Enabled(context)) {
             12
        } else {
             4
        }).toFloat())
        
    }
    
}