
package com.androlua.util;

import java.util.*;

public class TimerX
 {

    private static final class TimerImpl extends Thread {

        private static final class TimerHeap {
            private int DEFAULT_HEAP_SIZE = 256;

            private TimerTaskX[] timers = new TimerTaskX[DEFAULT_HEAP_SIZE];

            private int size = 0;

            private int deletedCancelledNumber = 0;

            public TimerTaskX minimum() {
                return timers[0];
            }

            public boolean isEmpty() {
                return size == 0;
            }

            public void insert(TimerTaskX task) {
                if (timers.length == size) {
                    TimerTaskX[] appendedTimers = new TimerTaskX[size * 2];
                    System.arraycopy(timers, 0, appendedTimers, 0, size);
                    timers = appendedTimers;
                }
                timers[size++] = task;
                upHeap();
            }

            public void delete(int pos) {
                if (pos >= 0 && pos < size) {
                    timers[pos] = timers[--size];
                    timers[size] = null;
                    downHeap(pos);
                }
            }

            private void upHeap() {
                int current = size - 1;
                int parent = (current - 1) / 2;

                while (timers[current].when < timers[parent].when) {
                    TimerTaskX tmp = timers[current];
                    timers[current] = timers[parent];
                    timers[parent] = tmp;

                    current = parent;
                    parent = (current - 1) / 2;
                }
            }

            private void downHeap(int pos) {
                int current = pos;
                int child = 2 * current + 1;

                while (child < size && size > 0) {
                    if (child + 1 < size
                            && timers[child + 1].when < timers[child].when) {
                        child++;
                    }

                    if (timers[current].when < timers[child].when) {
                        break;
                    }

                    TimerTaskX tmp = timers[current];
                    timers[current] = timers[child];
                    timers[child] = tmp;

                    current = child;
                    child = 2 * current + 1;
                }
            }

            public void reset() {
                timers = new TimerTaskX[DEFAULT_HEAP_SIZE];
                size = 0;
            }

            public void adjustMinimum() {
                downHeap(0);
            }

            public void deleteIfCancelled() {
                for (int i = 0; i < size; i++) {
                    if (timers[i].cancelled) {
                        deletedCancelledNumber++;
                        delete(i);
                        i--;
                    }
                }
            }

            private int getTask(TimerTaskX task) {
                for (int i = 0; i < timers.length; i++) {
                    if (timers[i] == task) {
                        return i;
                    }
                }
                return -1;
            }

        }

        private boolean cancelled;

        private boolean finished;

        private TimerHeap tasks = new TimerHeap();

        TimerImpl(String name, boolean isDaemon) {
            this.setName(name);
            this.setDaemon(isDaemon);
            this.start();
        }

        @Override
        public void run() {
            while (true) {
                TimerTaskX task;
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    if (tasks.isEmpty()) {
                        if (finished) {
                            return;
                        }
                        try {
                            this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    long currentTime = System.currentTimeMillis();

                    task = tasks.minimum();
                    long timeToSleep;

                    synchronized (task.lock) {
                        if (task.cancelled) {
                            tasks.delete(0);
                            continue;
                        }

                        timeToSleep = task.when - currentTime;
                    }

                    if (timeToSleep > 0) {
                        try {
                            this.wait(timeToSleep);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }


                    synchronized (task.lock) {
                        int pos = 0;
                        if (tasks.minimum().when != task.when) {
                            pos = tasks.getTask(task);
                        }
                        if (task.cancelled) {
                            tasks.delete(tasks.getTask(task));
                            continue;
                        }

                        task.setScheduledTime(task.when);

                        tasks.delete(pos);

                        if (task.period >= 0) {
                            if (task.fixedRate) {
                                task.when = task.when + task.period;
                            } else {
                                task.when = System.currentTimeMillis()
                                        + task.period;
                            }

                            insertTask(task);
                        } else {
                            task.when = 0;
                        }
                    }
                }

                boolean taskCompletedNormally = false;
                try {
					if (task.isEnabled())
                    	task.run();
                    taskCompletedNormally = true;
                } finally {
                    if (!taskCompletedNormally) {
                        synchronized (this) {
                            cancelled = true;
                        }
                    }
                }
            }
        }

        private void insertTask(TimerTaskX newTask) {
            tasks.insert(newTask);
            this.notify();
        }

        public synchronized void cancel() {
            cancelled = true;
            tasks.reset();
            this.notify();
        }

        public int purge() {
            if (tasks.isEmpty()) {
                return 0;
            }
            tasks.deletedCancelledNumber = 0;
            tasks.deleteIfCancelled();
            return tasks.deletedCancelledNumber;
        }

    }

    private static final class FinalizerHelper {
        private final TimerImpl impl;

        FinalizerHelper(TimerImpl impl) {
            this.impl = impl;
        }

        @Override protected void finalize() throws Throwable {
            try {
                synchronized (impl) {
                    impl.finished = true;
                    impl.notify();
                }
            } finally {
                super.finalize();
            }
        }
    }

    private static long timerId;

    private synchronized static long nextId() {
        return timerId++;
    }

    private final TimerImpl impl;

    @SuppressWarnings("unused")
    private final FinalizerHelper finalizer;

    public TimerX(String name, boolean isDaemon) {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        this.impl = new TimerImpl(name, isDaemon);
        this.finalizer = new FinalizerHelper(impl);
    }

    public TimerX(String name) {
        this(name, false);
    }

    public TimerX(boolean isDaemon) {
        this("Timer-" + TimerX.nextId(), isDaemon);
    }

    public TimerX() {
        this(false);
    }

    public void cancel() {
        impl.cancel();
    }

    public int purge() {
        synchronized (impl) {
            return impl.purge();
        }
    }

    public void schedule(TimerTaskX task, Date when) {
        if (when.getTime() < 0) {
            throw new IllegalArgumentException();
        }
        long delay = when.getTime() - System.currentTimeMillis();
        scheduleImpl(task, delay < 0 ? 0 : delay, -1, false);
    }

    public void schedule(TimerTaskX task, long delay) {
        if (delay < 0) {
            throw new IllegalArgumentException();
        }
        scheduleImpl(task, delay, -1, false);
    }

    public void schedule(TimerTaskX task, long delay, long period) {
        if (delay < 0 || period <= 0) {
            throw new IllegalArgumentException();
        }
        scheduleImpl(task, delay, period, false);
    }

    public void schedule(TimerTaskX task, Date when, long period) {
        if (period <= 0 || when.getTime() < 0) {
            throw new IllegalArgumentException();
        }
        long delay = when.getTime() - System.currentTimeMillis();
        scheduleImpl(task, delay < 0 ? 0 : delay, period, false);
    }

    public void scheduleAtFixedRate(TimerTaskX task, long delay, long period) {
        if (delay < 0 || period <= 0) {
            throw new IllegalArgumentException();
        }
        scheduleImpl(task, delay, period, true);
    }

    public void scheduleAtFixedRate(TimerTaskX task, Date when, long period) {
        if (period <= 0 || when.getTime() < 0) {
            throw new IllegalArgumentException();
        }
        long delay = when.getTime() - System.currentTimeMillis();
        scheduleImpl(task, delay, period, true);
    }

    private void scheduleImpl(TimerTaskX task, long delay, long period, boolean fixed) {
        synchronized (impl) {
            if (impl.cancelled) {
                throw new IllegalStateException("Timer was canceled");
            }

            long when = delay + System.currentTimeMillis();

            if (when < 0) {
                throw new IllegalArgumentException("Illegal delay to start the TimerTask: " + when);
            }

            synchronized (task.lock) {
                if (task.isScheduled()) {
                    throw new IllegalStateException("TimerTask is scheduled already");
                }

                if (task.cancelled) {
                    throw new IllegalStateException("TimerTask is canceled");
                }

                task.when = when;
                task.period = period;
                task.fixedRate = fixed;
            }

            impl.insertTask(task);
        }
    }
}
