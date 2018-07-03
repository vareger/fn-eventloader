package ethereum.eventloader.impl;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ethereum.eventloader.CoordinatorAdapter;
import ethereum.eventloader.CoordinatorException;
import ethereum.eventloader.DistributedLock;
import ethereum.eventloader.Events;

@Component
public class ZookeeperCoordinator implements CoordinatorAdapter {
	private static final Logger log = LoggerFactory.getLogger(ZookeeperCoordinator.class);
	
	private static final String ZNODE_ROOT = "/eventloader";
	private static final String ZNODE_LOCK_ROOT = "/eventloader/lock_root";
	private static final String ZNODE_LOCK = "/eventloader/lock_root/lock-";
	private static final String ZNODE_PROCESSED_BLOCK = "/eventloader/processed_block";
	
	private ZooKeeper zk;
	
	@Value("${eventloader.zk.url}") 
	private String zkUrl;
	
	@Value("${eventloader.zk.session_timeout_ms:3000}") 
	private int zkSessionTimeout;
	
	@Value("${eventloader.start_block:5883269}") 
	private int startBlock;
	
	@Override
	public DistributedLock obtainLock() {
		log.info("Obtaining lock...");
		String lockPath = null;
		try {
			lockPath = zk.create(ZNODE_LOCK, null, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
			Thread.sleep(50);
			String thisNode = lockPath.substring((ZNODE_LOCK_ROOT + "/").length());
			waitForLock(thisNode);
			return new Lock(zk, lockPath);
		} catch (Throwable e) {
			if (lockPath != null) {
				try {
					zk.delete(lockPath, -1);
				} catch (InterruptedException | KeeperException e1) {
					log.info("Failed to delete lock", e1);
				}
			}
			throw new CoordinatorException(e);
		}
	}

	private void waitForLock(String thisNode) throws KeeperException, InterruptedException {
		log.info("Checking lock state for: {}", thisNode);
		List<String> nodes = zk.getChildren(ZNODE_LOCK_ROOT, false);			
		Collections.sort(nodes);
		log.info("Nodes: {}", nodes);
		int index = nodes.indexOf(thisNode);
		if (index == 0) {
			log.info("Lock obtained!");
			return;
		} else {
			String waitNode = ZNODE_LOCK_ROOT + "/" + nodes.get(index - 1);
			log.info("Waiting on previous node: {}", waitNode);
			CountDownLatch latch = new CountDownLatch(1);
			Stat stat = zk.exists(waitNode, new Watcher() {
				@Override
				public void process(WatchedEvent event) {
					log.info("Previous node event: {}", event);
					latch.countDown();
				}
			});
			if (stat == null) {
				log.info("Previous node not found, retrying check...");
				waitForLock(thisNode);
			} else {
				latch.await(10000, TimeUnit.MILLISECONDS);
				log.info("Previous node changed or time elapsed, retrying check...");
				waitForLock(thisNode);
			}
		}
	}

	@Override
	public int lastProcessedBlock() {
		try {
			byte[] data = zk.getData(ZNODE_PROCESSED_BLOCK, null, null);
			int block = fromBytes(data);
			log.info("Last processed block: {}", block);
			return block;
		} catch (KeeperException | InterruptedException e) {
			throw new CoordinatorException(e);
		}
	}
	
	@Override
	public void saveState(Events events) {
		try {
			byte[] data = toBytes(events.getEndBlock());
			zk.setData(ZNODE_PROCESSED_BLOCK, data, -1);
			log.info("Updated last processed block to: {}", events.getEndBlock());
		} catch (KeeperException | InterruptedException e) {
			throw new CoordinatorException(e);
		}
	}

	@PostConstruct
	public void start() {
		try {
			log.info("Connecting to Zookeeper at: {}", zkUrl);
			CountDownLatch latch = new CountDownLatch(1);  
			zk = new ZooKeeper(zkUrl, zkSessionTimeout, new Watcher() {
				@Override
				public void process(WatchedEvent event) {
					log.info("ZK event: " + event);
					if (event.getState() == KeeperState.SyncConnected) {
						latch.countDown();
					}
				}
			});
			log.info("Waiting for Zookeeper...");
			latch.await();
			
			if (zk.exists(ZNODE_PROCESSED_BLOCK, false) == null) {
				initNodes();
			}
		} catch (Exception e) {
			throw new RuntimeException("Initialization failed", e);
		}
	}
	
	@PreDestroy
	public void stop() {
		try {
			zk.close();
		} catch (InterruptedException e) {}
	}
	
	void initNodes() {
		try {
			log.info("Initializing znodes...");
			zk.create(ZNODE_ROOT, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			zk.create(ZNODE_LOCK_ROOT, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			zk.create(ZNODE_PROCESSED_BLOCK, toBytes(startBlock), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		} catch (KeeperException | InterruptedException e) {
			throw new RuntimeException("Failed to setup znodes", e);
		}
	}

	static byte[] toBytes(int num) {
		return ByteBuffer.allocate(4).putInt(num).array();
	}
	
	static int fromBytes(byte[] data) {
		return ByteBuffer.wrap(data).getInt();
	}

	public static class Lock implements DistributedLock {
		private final ZooKeeper zk;
		private final String lockPath;
		
		public Lock(ZooKeeper zk, String lockPath) {
			this.zk = zk;
			this.lockPath = lockPath;
		}

		@Override
		public void release() {
			try {
				log.info("Releasing lock...");
				zk.delete(lockPath, -1);
				log.info("Lock released");
			} catch (KeeperException | InterruptedException e) {
				throw new RuntimeException("Failed to release lock", e);
			}
		}
	}
}
