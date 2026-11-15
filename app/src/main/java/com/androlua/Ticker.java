package com.androlua;

import android.os.*;

public class Ticker implements LuaGcable
{
        private Handler mHandler;

        private Ticker.OnTickListener mOnTickListener;

        private Thread mThread;

        private long mPeriod=1000;

        private boolean mEnabled=true;

        private volatile boolean isRun=false;

        private long mLast;

        private long mOffset;

        private boolean mGc = false;


        public Ticker()
        {
                init();
        }

        @Override
        public void gc()
        {
                stop();
                mGc = true;
        }

        @Override
        public boolean isGc()
        {
                return mGc;
        }

        private void init()
        {
                mHandler = new Handler()
                {
                        public void handleMessage(Message msg)
                        {
                                try
                                {
                                        if (mOnTickListener != null)
                                                mOnTickListener.onTick();
                                }
                                catch (Throwable ignored)
                                {
                                }
                        }
                };
                mThread = new Thread(){
                        @Override
                        public void run()
                        {
                                isRun = true;
                                while (isRun)
                                {
                                        long now = System.currentTimeMillis();
                                        if (!mEnabled)
                                                mLast = now - mOffset;
                                        if (now - mLast >= mPeriod)
                                        {
                                                mLast = now;
                                                mHandler.sendEmptyMessage(0);
                                        }

                                        try
                                        {
                                                sleep(Math.min(50, Math.max(1, mPeriod / 4)));
                                        }
                                        catch (InterruptedException e)
                                        {
                                        }
                                }
                        }
                };
        }

        public void setPeriod(long period)
        {
                mLast=System.currentTimeMillis();
                mPeriod = period;
        }

        public long getPeriod()
        {
                return mPeriod;
        }


        public void setInterval(long period)
        {
                mLast=System.currentTimeMillis();
                mPeriod = period;
        }

        public long getInterval()
        {
                return mPeriod;
        }

        public void setEnabled(boolean enabled)
        {
                mEnabled = enabled;
                if (!enabled)
                        mOffset = System.currentTimeMillis() - mLast;
        }

        public boolean getEnabled()
        {
                return mEnabled;
        }

        public void setOnTickListener(OnTickListener ltr)
        {
                mOnTickListener = ltr;
        }

        public void start()
        {
                mThread.start();
        }

        public void stop()
        {
                isRun = false;
                if (mHandler != null)
                        mHandler.removeCallbacksAndMessages(null);
        }

        public boolean isRun()
        {
                return isRun;
        }


        public interface OnTickListener
        {
                public void onTick();
        }
}
