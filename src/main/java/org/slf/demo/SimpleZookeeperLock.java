package org.slf.demo;


import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The type Simple zookeeper lock.
 */
public class SimpleZookeeperLock {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleZookeeperLock.class);

    private static final int SESSION_TIMEOUT = 5000;
    private String hosts = "ubuntu01:2181,ubuntu02:2181,ubuntu03:2181";
    private String groupNode = "locks";
    private String subNode = "sub";

    private ZooKeeper zkClient;
    private volatile String thisPath;
    private volatile String waitPath;

    private CountDownLatch countDownLatch = new CountDownLatch(1);

    /**
     * Connect zookeeper.
     *
     * @throws Exception the exception
     */
    public void connectZookeeper() throws Exception {

        zkClient = new ZooKeeper(hosts, SESSION_TIMEOUT, watchedEvent -> {
            if (watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected) {
                countDownLatch.countDown();
            }

            if (watchedEvent.getType() == Watcher.Event.EventType.NodeDeleted && watchedEvent.getPath().equals(waitPath)) {
                try {
                    doSomething();
                } catch (Exception e) {
                    LOG.error(ExceptionUtils.getFullStackTrace(e));
                }
            }
        });

        countDownLatch.await();

        thisPath = zkClient.create("/" + groupNode + "/" + subNode, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        Thread.sleep(10);

        List<String> childrenNodes = zkClient.getChildren("/" + groupNode, false);

        if (childrenNodes.size() == 1) {
            doSomething();
        } else {
            String thisNode = thisPath.substring(("/" + groupNode + "/").length());
            Collections.sort(childrenNodes);
            int index = childrenNodes.indexOf(thisNode);
            if (index == -1) {
                LOG.error("never happened!");
            } else if (index == 0) {
                doSomething();
            } else {
                this.waitPath = "/" + groupNode + "/" + childrenNodes.get(index - 1);
                zkClient.getData(waitPath, true, new Stat());
            }
        }

    }

    private void doSomething() throws Exception {

        try {
            LOG.warn("gain lock: " + thisPath);
            Thread.sleep(2000);
            //do something
        } finally {
            LOG.warn("finished: " + thisPath);
            zkClient.delete(thisPath, -1);
        }

    }

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws InterruptedException the interrupted exception
     */
    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 5; i++)
            new Thread(() -> {
                try {
                    SimpleZookeeperLock szkLock = new SimpleZookeeperLock();
                    szkLock.connectZookeeper();
                } catch (Exception e) {
                    LOG.error(ExceptionUtils.getFullStackTrace(e));
                }
            }).start();

        Thread.sleep(Long.MAX_VALUE);
    }

}
