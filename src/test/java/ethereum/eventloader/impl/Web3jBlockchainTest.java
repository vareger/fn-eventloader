package ethereum.eventloader.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import ethereum.eventloader.BlockchainAdapter;
import ethereum.eventloader.Events;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ImplTC.class)
@TestPropertySource("/test.properties")
public class Web3jBlockchainTest extends Assert {

	@Autowired BlockchainAdapter blockchain;
	
	@Test
	public void test_get_latest_block_number() throws Exception {
		int num = blockchain.latestBlockNumber();
		assertTrue(num > 100);
	}
	
	@Test
	public void test_get_logs_for_single_block() throws Exception {
		int latest = blockchain.latestBlockNumber();
		Events events = blockchain.eventsLog(latest-10, latest-9);
		assertFalse(events.getLogs(0).isEmpty());
	}
	
	@Test
	public void test_get_logs_for_two_blocks() throws Exception {
		int latest = blockchain.latestBlockNumber();
		Events events = blockchain.eventsLog(latest-10, latest-8);
		assertFalse(events.getLogs(0).isEmpty());
	}
	
	@Test
	public void test_get_logs_for_many_blocks() throws Exception {
		int latest = blockchain.latestBlockNumber();
		Events events = blockchain.eventsLog(latest-20, latest-5);
		assertFalse(events.getLogs(0).isEmpty());
	}
	
}
