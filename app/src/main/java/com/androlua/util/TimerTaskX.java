
package com.androlua.util;

public abstract class TimerTaskX implements Runnable {
    final Object lock = new Object();

    boolean cancelled;

    long when;

    long period;

    boolean fixedRate;

    private long scheduledTime;

	private boolean mEnabled;

	public void setPeriod(long period)
	{
		this.period = period;
	}

	public long getPeriod()
	{
		return period;
	}

    long getWhen() {
        synchronized (lock) {
            return when;
        }
    }

   public  void setScheduledTime(long time) {
        synchronized (lock) {
            scheduledTime = time;
        }
    }

    boolean isScheduled() {
        synchronized (lock) {
            return when > 0 || scheduledTime > 0;
        }
    }

    protected TimerTaskX() {
    }

    public boolean cancel() {
        synchronized (lock) {
            boolean willRun = !cancelled && when > 0;
            cancelled = true;
            return willRun;
        }
    }

    public long scheduledExecutionTime() {
        synchronized (lock) {
            return scheduledTime;
        }
    }

    public abstract void run();


	public void setEnabled(boolean enabled)
	{
		mEnabled = enabled;
	}

	public boolean isEnabled()
	{
		return mEnabled;
	}

}
