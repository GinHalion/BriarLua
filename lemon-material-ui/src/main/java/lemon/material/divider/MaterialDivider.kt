package lemon.material.divider

import android.content.Context
import android.app.Activity
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.divider.MaterialDivider
import lemon.material.R
import lemon.utils.ColorUtil

class MaterialDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialDivider(context, attrs, defStyleAttr) {
    
    init {
        
        setDividerColor(ColorUtil.getColor2(context, R.color.colorBorder))
        
     }
    

}