package github.znzsofficial.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.androlua.LuaActivity

class LuaFragmentAdapter(context: LuaActivity, inter: Creator) :
    FragmentStateAdapter(context.supportFragmentManager, context.lifecycle) {
    var creator: Creator
    private var mContext: LuaActivity?

    init {
        mContext = context
        creator = inter
    }

    override fun createFragment(position: Int): Fragment {
        return try {
            creator.createFragment(position)
        } catch (e: Exception) {
            e.printStackTrace()
            mContext?.sendError("FragmentAdapter", e)
            Fragment()
        }
    }

    override fun getItemCount(): Int {
        return try {
            creator.itemCount.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
            mContext?.sendError("FragmentAdapter", e)
            0
        }
    }

    fun release() {
        mContext = null
        if (creator is Releasable) {
            (creator as Releasable).release()
        }
    }

    interface Creator {
        fun createFragment(i: Int): Fragment
        val itemCount: Long
    }
}