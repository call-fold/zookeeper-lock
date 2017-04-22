package org.slf.demo;


import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The type Simple zookeeper lock.
 */
public class SimpleZookeeperLock {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleZookeeperLock.class);

    private static final int SESSION_TIMEOUT = 5000;
    private static final String hosts = "ubuntu01:2181,ubuntu02:2181,ubuntu03:2181";
    private static final String groupNode = "locks";
    private static final String subNode = "sub";

    private ZooKeeper zkClient;
    private volatile String thisPath;
    private volatile String waitPath;

    private CountDownLatch countDownLatch = new CountDownLatch(1);


    /**
     * Connect zookeeper.
     *
     * @throws Exception the exception
     */
    private void connectZookeeper() throws Exception {

        zkClient = new ZooKeeper(hosts, SESSION_TIMEOUT, watchedEvent -> {
            if (watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected) {
                countDownLatch.countDown();
            }

            if (watchedEvent.getType() == Watcher.Event.EventType.NodeDeleted && watchedEvent.getPath().equals(this.waitPath)) {
                try {
                    //这里可能出现, 因为网络抖动客户端突然断开, 这里考虑重新判断与重试机制
                    List<String> childrenNodes = zkClient.getChildren("/" + groupNode, false);
                    String thisNode = this.thisPath.substring(("/" + groupNode + "/").length());
                    Collections.sort(childrenNodes);
                    int index = childrenNodes.indexOf(thisNode);
                    if (index == -1) {
                        LOG.error("never happened!");
                    } else if (index == 0) {
                        doSomething();
                    } else {
                        this.waitPath = "/" + groupNode + "/" + childrenNodes.get(index - 1);
                        if (zkClient.exists(this.waitPath, true) == null) {
                            doSomething();
                        }
                    }
                } catch (Exception e) {
                    LOG.error(ExceptionUtils.getFullStackTrace(e));
                }
            }
        });

        countDownLatch.await();

        this.thisPath = zkClient.create("/" + groupNode + "/" + subNode, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        Thread.sleep(10);

        List<String> childrenNodes = zkClient.getChildren("/" + groupNode, false);

        if (childrenNodes.size() == 1) {
            doSomething();
        } else {
            String thisNode = this.thisPath.substring(("/" + groupNode + "/").length());
            Collections.sort(childrenNodes);
            int index = childrenNodes.indexOf(thisNode);
            if (index == -1) {
                LOG.error("never happened!");
            } else if (index == 0) {
                doSomething();
            } else {
                this.waitPath = "/" + groupNode + "/" + childrenNodes.get(index - 1);
                zkClient.getData(this.waitPath, true, new Stat());
            }
        }

    }

    private void closeZookeeper() throws InterruptedException {
        zkClient.close();
    }

    private void doSomething() throws Exception {

        try {
            LOG.warn("gain lock: " + this.thisPath);
            Thread.sleep(5000);
            //do something
        } finally {
            LOG.warn("finished: " + this.thisPath);
            zkClient.delete(this.thisPath, -1);
        }

    }

    private static class MyRunnable implements Runnable {

        private int threadID = 0;

        /**
         * Instantiates a new My runnable.
         *
         * @param threadID the thread id
         */
        MyRunnable(int threadID) {
            this.threadID = threadID;
        }

        @Override
        public void run() {
            try {
                SimpleZookeeperLock szkLock = new SimpleZookeeperLock();
                szkLock.connectZookeeper();
                //关闭一个客户端, 测试删除中间一个临时节点的情况
                if (threadID == 3) {
                    szkLock.closeZookeeper();
                }
            } catch (Exception e) {
                LOG.error(ExceptionUtils.getFullStackTrace(e));
            }
        }
    }

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws InterruptedException the interrupted exception
     */
    public static void main(String[] args) throws InterruptedException {
        int threadNo = 5;
        int timeout = 60;
        ExecutorService executorService = Executors.newFixedThreadPool(threadNo);
        for (int i = 0; i < threadNo; i++) {
            MyRunnable myRunnable = new MyRunnable(i);
            executorService.submit(myRunnable);
        }

        try {
            executorService.awaitTermination(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error(ExceptionUtils.getFullStackTrace(e));
        } finally {
            executorService.shutdown();
        }

    }

}
