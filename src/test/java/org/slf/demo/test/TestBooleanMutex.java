package org.slf.demo.test;


import org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.Test;
import org.slf.demo.BooleanMutex;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Test boolean mutex.
 */
public class TestBooleanMutex {

    private static final Logger LOG = LoggerFactory.getLogger(TestBooleanMutex.class);

    /**
     * Test init true.
     */
    @Test
    public void test_init_true() {
        BooleanMutex mutex = new BooleanMutex(true);
        try {
            mutex.get(); //不会被阻塞
        } catch (InterruptedException e) {
//            LOG.error(ExceptionUtils.getStackTrace(e));
            e.printStackTrace();
        }
    }

    /**
     * Test init false.
     */
    @Test
    public void test_init_false() {
        final BooleanMutex mutex = new BooleanMutex(false);
        try {
            final CountDownLatch count = new CountDownLatch(1);
            ExecutorService executor = Executors.newCachedThreadPool();

            executor.submit(new Callable() {
                public Object call() throws Exception {
                    System.out.println("asynchronous begin!");
                    Thread.sleep(5000);
                    mutex.set(true);
                    count.countDown();
                    System.out.println("asynchronous end!");
                    return null;
                }
            });
            System.out.println("mutex start wait!");
            mutex.get(); //会被阻塞，等异步线程释放锁对象
            System.out.println("mutex end wait!");
            count.await();
            executor.shutdown();
        } catch (InterruptedException e) {
//            LOG.error(ExceptionUtils.getStackTrace(e));
            e.printStackTrace();
        }
    }

    /**
     * Test concurrent true.
     */
    @Test
    public void test_concurrent_true() {
        try {
            final BooleanMutex mutex = new BooleanMutex(true);
            final CountDownLatch count = new CountDownLatch(10);
            ExecutorService executor = Executors.newCachedThreadPool();

            for (int i = 0; i < 10; i++) {
                executor.submit(new Callable() {
                    public Object call() throws Exception {
                        mutex.get();
                        count.countDown();
                        return null;
                    }
                });
            }
            count.await();
            executor.shutdown();
        } catch (InterruptedException e) {
//            LOG.error(ExceptionUtils.getStackTrace(e));
            e.printStackTrace();
        }
    }

    /**
     * Test concurrent false.
     */
    @Test
    public void test_concurrent_false() {
        try {
            final BooleanMutex mutex = new BooleanMutex(false);//初始为false
            final CountDownLatch count = new CountDownLatch(10);
            ExecutorService executor = Executors.newCachedThreadPool();

            for (int i = 0; i < 10; i++) {
                int finalI = i;
                executor.submit(new Callable() {
                    public Object call() throws Exception {
                        System.out.println("mutex" + finalI + "start wait!");
                        mutex.get();//被阻塞
                        System.out.println("mutex" + finalI + "end wait!");
                        count.countDown();
                        return null;
                    }
                });
            }
            Thread.sleep(5000);
            mutex.set(true);
            count.await();
            executor.shutdown();
        } catch (InterruptedException e) {
//            LOG.error(ExceptionUtils.getStackTrace(e));
            e.printStackTrace();
        }
    }
}
