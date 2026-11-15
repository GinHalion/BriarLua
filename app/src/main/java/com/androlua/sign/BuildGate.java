package com.androlua.sign;

public final class BuildGate {

    private static final long STALE_MS = 15 * 60 * 1000L;

    private static volatile boolean locked = false;
    private static volatile long ts = 0L;
    private static final Object MUTEX = new Object();

    private BuildGate() {
    }

    public static boolean tryEnter() {
        synchronized (MUTEX) {
            long now = System.currentTimeMillis();
            if (locked && (now - ts) < STALE_MS) {
                return false;
            }
            locked = true;
            ts = now;
            return true;
        }
    }

    public static void exit() {
        synchronized (MUTEX) {
            locked = false;
            ts = 0L;
        }
    }

    public static boolean isLocked() {
        synchronized (MUTEX) {
            return locked && (System.currentTimeMillis() - ts) < STALE_MS;
        }
    }
}
