package lemon.utils

import android.util.TypedValue
import android.content.Context
import com.google.android.material.color.MaterialColors
import androidx.core.content.ContextCompat

object ColorUtil {

    fun getColor(context: Context, colorName: String): Int {
        try {
            val colorAttr = context.resources.getIdentifier(colorName, "attr", context.packageName)
            if (colorAttr == 0) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
                return typedValue.data
            } else {
                return MaterialColors.getColor(context, colorAttr, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    fun getColor2(context: Context, colorResId: Int): Int {
       return ContextCompat.getColor(context, colorResId)
    }

}
