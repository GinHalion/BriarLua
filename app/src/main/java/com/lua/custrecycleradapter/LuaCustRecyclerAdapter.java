package com.lua.custrecycleradapter;

import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;

public class LuaCustRecyclerAdapter extends RecyclerView.Adapter {
    com.lua.custrecycleradapter.AdapterCreator adapterCreator;

    public LuaCustRecyclerAdapter(com.lua.custrecycleradapter.AdapterCreator adapterCreator) {
        this.adapterCreator = adapterCreator;
    }

    public int getItemCount() {
        com.lua.custrecycleradapter.AdapterCreator c = this.adapterCreator;
        if (c == null) return 0;
        return (int) c.getItemCount();
    }

    public int getItemViewType(int i) {
        com.lua.custrecycleradapter.AdapterCreator c = this.adapterCreator;
        if (c == null) return 0;
        return (int) c.getItemViewType(i);
    }

    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        this.adapterCreator.onBindViewHolder(viewHolder, i);
    }

    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        RecyclerView.ViewHolder holder = this.adapterCreator.onCreateViewHolder(viewGroup, i);
        if (holder == null) {
            throw new IllegalStateException(
                    "onCreateViewHolder 未返回有效视图（Lua 端 onCreateViewHolder 返回了 nil）");
        }
        return holder;
    }

    public void release() {
        if (adapterCreator != null) {
            adapterCreator = null;
        }
    }
}
