package ethereum.eventloader;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mockito.Mockito;

public class EventLoaderTest {

//	@Test
//	public void test_normal_load() throws Exception {
//		EventLoader loader = new EventLoader();
//		
//		//configure
//		loader.setSleepIntervalMs(100);
//		BlockchainAdapter blockchain = Mockito.mock(BlockchainAdapter.class);
//		loader.setBlockchain(blockchain);
//		CoordinatorAdapter coordinator = Mockito.mock(CoordinatorAdapter.class);
//		loader.setCoordinator(coordinator);
//		MessageBrokerAdapter messageBroker = Mockito.mock(MessageBrokerAdapter.class);
//		loader.setMessageBroker(messageBroker);
//		
//		Mockito.when(blockchain.latestBlockNumber()).thenReturn(100);
//		Mockito.when(coordinator.lastProcessedBlock()).thenReturn(96);
//		Events events = new Events(97, 100);
//		events.addLogs(97, new ArrayList<>());
//		events.addLogs(98, new ArrayList<>());
//		Mockito.when(blockchain.eventsLog(100, 96)).thenReturn(events);
//
//		boolean atLatestBlock = loader.eventLoadAttempt();
//		assertTrue(atLatestBlock);
//	}
	
	@Test
	public void test_at_latest_block() throws Exception {
		EventLoader loader = new EventLoader();
		
		//configure
		loader.setSleepIntervalMs(100);
		BlockchainAdapter blockchain = Mockito.mock(BlockchainAdapter.class);
		loader.setBlockchain(blockchain);
		CoordinatorAdapter coordinator = Mockito.mock(CoordinatorAdapter.class);
		loader.setCoordinator(coordinator);
		MessageBrokerAdapter messageBroker = Mockito.mock(MessageBrokerAdapter.class);
		loader.setMessageBroker(messageBroker);
		
		Mockito.when(blockchain.latestBlockNumber()).thenReturn(100);
		Mockito.when(coordinator.lastProcessedBlock()).thenReturn(100);
		
		boolean atLatestBlock = loader.eventLoadAttempt();
		
		assertTrue(atLatestBlock);
	}
	
	@Test
	public void test_node_syncing() throws Exception {
		EventLoader loader = new EventLoader();
		
		//configure
		loader.setSleepIntervalMs(100);
		BlockchainAdapter blockchain = Mockito.mock(BlockchainAdapter.class);
		loader.setBlockchain(blockchain);
		CoordinatorAdapter coordinator = Mockito.mock(CoordinatorAdapter.class);
		loader.setCoordinator(coordinator);
		MessageBrokerAdapter messageBroker = Mockito.mock(MessageBrokerAdapter.class);
		loader.setMessageBroker(messageBroker);
		
		Mockito.when(blockchain.latestBlockNumber()).thenReturn(100);
		Mockito.when(coordinator.lastProcessedBlock()).thenReturn(200);
		
		boolean atLatestBlock = loader.eventLoadAttempt();
		assertTrue(atLatestBlock);
	}
	
}
