package ethereum.eventloader.impl;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
public class ZookeeperCoordinatorTest extends Assert {

    private static final String ZNODE_PROCESSED_BLOCK = "/processed_block";

    private TestingServer zooKeeperServer;
    private CuratorFramework curatorFramework;

    @Before
    public void loadZookeeper() throws Exception {
        zooKeeperServer = new TestingServer(2181);
        curatorFramework = CuratorFrameworkFactory.newClient(zooKeeperServer.getConnectString(), new RetryOneTime(2000));
        curatorFramework.start();
    }

    @After
    public void closeZookeeper() throws IOException {
        curatorFramework.close();
        zooKeeperServer.close();
    }

    @Test
    public void test_processed_block_state() throws Exception {
        DistributedAtomicLong lastBlock = new DistributedAtomicLong(
                curatorFramework,
                ZNODE_PROCESSED_BLOCK,
                new ExponentialBackoffRetry(1000, 5)
        );
        long block = lastBlock.get().postValue();
        lastBlock.add(5L);
        assertEquals(block + 5, lastBlock.get().postValue().longValue());
    }

    @Test
    public void test_lock() throws Exception {
        InterProcessMutex mutex = new InterProcessMutex(curatorFramework, ZNODE_PROCESSED_BLOCK);
        Locker lock = new Locker(mutex, 120, TimeUnit.SECONDS);
        lock.close();
    }

    @Test
    public void test_lock_emulate_multiple_workers() throws Exception {
        InterProcessMutex mutex = new InterProcessMutex(curatorFramework, ZNODE_PROCESSED_BLOCK);
        Locker lock0 = new Locker(mutex, 120, TimeUnit.SECONDS);
        Thread t0 = new Thread(() -> {
            try {
                Thread.sleep(1000);
                lock0.close();
            } catch (Exception e) {
            }
        });
        t0.setName("lock0-worker");
        t0.start();

        Locker lock1 = new Locker(mutex, 120, TimeUnit.SECONDS);
        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(1000);
                lock1.close();
            } catch (Exception e) {
            }
        });
        t1.setName("lock1-worker");
        t1.start();

        Locker lock2 = new Locker(mutex, 120, TimeUnit.SECONDS);
        lock2.close();
    }
}
