package com.androlua;

import android.os.FileObserver;


public class LuaFileObserver extends FileObserver implements LuaGcable {

    private OnEventListener mOnEventListener;
    private boolean mGc = false;
    private LuaContext mContext;

    public LuaFileObserver(String path) {
        super(path);
    }

    public LuaFileObserver(String path, int mask) {
        super(path, mask);
    }

    public LuaFileObserver(LuaContext context, String path) {
        super(path);
        regTo(context);
    }

    public LuaFileObserver(LuaContext context, String path, int mask) {
        super(path, mask);
        regTo(context);
    }

    private void regTo(LuaContext context) {
        if (context == null) return;
        mContext = context;
        try {
            context.regGc(this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void gc() {
        mGc = true;
        try {
            stopWatching();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isGc() {
        return mGc;
    }

    public void setOnEventListener(OnEventListener listener){
        mOnEventListener=listener;
    }

    @Override
    public void onEvent(int event, String path) {
        if(mOnEventListener!=null) {
            try {
                mOnEventListener.onEvent(event, path);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnEventListener{
        public void onEvent(int event, String path);
    }
}
