package org.slf.demo;


import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * The type Boolean mutex.
 */
public class BooleanMutex {

    private Sync sync;

    /**
     * Instantiates a new Boolean mutex.
     */
    public BooleanMutex() {
        sync = new Sync();
        set(false);
    }

    /**
     * Instantiates a new Boolean mutex.
     *
     * @param mutex the mutex
     */
    public BooleanMutex(Boolean mutex) {
        sync = new Sync();
        set(mutex);
    }

    /**
     * 阻塞等待Boolean为true
     *
     * @throws InterruptedException the interrupted exception
     */
    public void get() throws InterruptedException {
        sync.innerGet();
    }

    /**
     * 阻塞等待Boolean为true,允许设置超时时间
     *
     * @param timeout the timeout
     * @param unit    the unit
     * @throws InterruptedException the interrupted exception
     * @throws TimeoutException     the timeout exception
     */
    public void get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        sync.innerGet(unit.toNanos(timeout));
    }

    /**
     * 重新设置对应的Boolean mutex
     *
     * @param mutex the mutex
     */
    public void set(Boolean mutex) {
        if (mutex) {
            sync.innerSetTrue();
        } else {
            sync.innerSetFalse();
        }
    }

    /**
     * State boolean.
     *
     * @return the boolean
     */
    public boolean state() {
        return sync.innerState();
    }

    /**
     * Synchronization control for BooleanMutex. Uses AQS sync state to
     * represent run status
     */
    private final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -7828117401763700385L;

        /**
         * State value representing that TRUE
         */
        private static final int TRUE = 1;
        /**
         * State value representing that FALSE
         */
        private static final int FALSE = 2;

        private boolean isTrue(int state) {
            return (state & TRUE) != 0;
        }

        /**
         * 实现AQS的接口，获取共享锁的判断
         */
        protected int tryAcquireShared(int state) {
            // 如果为true，直接允许获取锁对象
            // 如果为false，进入阻塞队列，等待被唤醒
            return isTrue(getState()) ? 1 : -1;
        }

        /**
         * 实现AQS的接口，释放共享锁的判断
         */
        protected boolean tryReleaseShared(int ignore) {
            //始终返回true，代表可以release
            return true;
        }

        /**
         * Inner state boolean.
         *
         * @return the boolean
         */
        boolean innerState() {
            return isTrue(getState());
        }

        /**
         * Inner get.
         *
         * @throws InterruptedException the interrupted exception
         */
        void innerGet() throws InterruptedException {
            acquireSharedInterruptibly(0);
        }

        /**
         * Inner get.
         *
         * @param nanosTimeout the nanos timeout
         * @throws InterruptedException the interrupted exception
         * @throws TimeoutException     the timeout exception
         */
        void innerGet(long nanosTimeout) throws InterruptedException, TimeoutException {
            if (!tryAcquireSharedNanos(0, nanosTimeout))
                throw new TimeoutException();
        }

        /**
         * Inner set true.
         */
        void innerSetTrue() {
            for (; ; ) {
                int s = getState();
                if (s == TRUE) {
                    return; //直接退出
                }
                if (compareAndSetState(s, TRUE)) {// cas更新状态，避免并发更新true操作
                    releaseShared(0);//释放一下锁对象，唤醒一下阻塞的Thread
                }
            }
        }

        /**
         * Inner set false.
         */
        void innerSetFalse() {
            for (; ; ) {
                int s = getState();
                if (s == FALSE) {
                    return; //直接退出
                }
                if (compareAndSetState(s, FALSE)) {//cas更新状态，避免并发更新false操作
                    setState(FALSE);
                }
            }
        }

    }
}
