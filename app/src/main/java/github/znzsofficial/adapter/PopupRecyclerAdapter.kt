package github.znzsofficial.adapter

import android.view.View
import com.androlua.LuaContext
import me.zhanghai.android.fastscroll.PopupTextProvider

class PopupRecyclerAdapter : LuaCustRecyclerAdapter, PopupTextProvider {

    private val popupCreator: PopupCreator

    constructor(adapterCreator: PopupCreator) : super(adapterCreator) {
        this.popupCreator = adapterCreator
        mContext = null
    }

    constructor(context: LuaContext, adapterCreator: PopupCreator) : super(context, adapterCreator) {
        this.popupCreator = adapterCreator
        mContext = context
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        return popupCreator.getPopupText(view, position)
    }

    override fun release() {
        super.release()
    }

    interface PopupCreator : Creator {
        fun getPopupText(view: View, i: Int): CharSequence
    }
}