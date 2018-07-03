package ethereum.eventloader.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import ethereum.eventloader.DistributedLock;
import ethereum.eventloader.Events;
import ethereum.eventloader.impl.ZookeeperCoordinator;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ImplTC.class)
@TestPropertySource("/test.properties")
public class ZookeeperCoordinatorTest extends Assert {

	@Autowired ZookeeperCoordinator coordinator;
	
	@Test
	public void test_processed_block_state() throws Exception {
		int block = coordinator.lastProcessedBlock();
		coordinator.saveState(new Events(block+1, block+5));
		assertEquals(block+5, coordinator.lastProcessedBlock());
	}
	
	@Test
	public void test_lock() throws Exception {
		DistributedLock lock = coordinator.obtainLock();
		lock.release();
	}
	
	@Test
	public void test_lock_emulate_multiple_workers() throws Exception {
		DistributedLock lock0 = coordinator.obtainLock();
		Thread t0 = new Thread(() -> {
			try {
				Thread.sleep(1000);
				lock0.release();
			} catch (InterruptedException e) {}
		});
		t0.setName("lock0-worker");
		t0.start();
		
		DistributedLock lock1 = coordinator.obtainLock();
		Thread t1 = new Thread(() -> {
			try {
				Thread.sleep(1000);
				lock1.release();
			} catch (InterruptedException e) {}
		});
		t1.setName("lock1-worker");
		t1.start();
		
		DistributedLock lock2 = coordinator.obtainLock();
		lock2.release();
	}
}
