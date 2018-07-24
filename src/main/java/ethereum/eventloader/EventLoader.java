package ethereum.eventloader;

import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

/**
 * Loads Ethereum events into EMS topic
 */
@Component
public class EventLoader {
	private static final Logger log = LoggerFactory.getLogger(EventLoader.class);
	
	@Autowired private BlockchainAdapter blockchain;
	@Autowired private CoordinatorAdapter coordinator;
	@Autowired private MessageBrokerAdapter messageBroker;

	/** Milliseconds to sleep between event-load attempts */
	@Value("${eventloader.sleep_interval_ms:3000}") private long sleepIntervalMs;

	@PostConstruct
	public void start() {
		Thread t = new Thread(() -> {
			try {
				start0();
			} catch (Throwable e) {
				log.error("Main loop error", e);
			}
		});
		t.setDaemon(false);
		t.setName("main-loop");
		t.start();
	}

	void start0() throws Exception {
		log.info("Starting event load loop...");
		boolean connected = true;
		while (true) {
			if (!connected) {
				sleep(sleepIntervalMs);
				connected = reconnect();
				if (!connected)
					continue;
			}
			
			boolean atLatestBlock = false;
			try {
				atLatestBlock = eventLoadAttempt();
			} catch (Exception e) {
				log.error("Event load failed, will reconnect and retry", e);
				sleep(sleepIntervalMs);
				connected = reconnect();
			}
			
			try {
				if (atLatestBlock)
					Thread.sleep(sleepIntervalMs);
			} catch (InterruptedException e) {}
		}
	}

	private static void sleep(long interval) {
		try {
			Thread.sleep(interval);
		} catch (InterruptedException ex) {}
	}

	private boolean reconnect() {
		try {
			log.info("Reconnecting coordinator...");
			coordinator.reconnect();
			log.info("Reconnecting blockchain...");
			blockchain.reconnect();
			log.info("Reconnecting message broker...");
			messageBroker.reconnect();
			log.info("Reconnected!");
			return true;
		} catch (Exception re) {
			log.info("Reconnect failed", re);
			return false;
		}
	}

	/**
	 * @return true if at latest block
	 */
	@SuppressWarnings("rawtypes")
	boolean eventLoadAttempt() {
		//operations without lock
		int latestBlock = blockchain.latestBlockNumber();
		int lastProcessed = coordinator.lastProcessedBlock();
		Events events;
		if (latestBlock > lastProcessed) {
			events = blockchain.eventsLog(lastProcessed, latestBlock);
		} else if (lastProcessed > latestBlock) {
			log.warn("Lag detected. Node is on block {} while latest processed is {}", latestBlock, lastProcessed);
			return true;
		} else {
			log.info("At latest block: {}", latestBlock);
			return true;
		}
		
		DistributedLock lock = coordinator.obtainLock();
		boolean atLatestBlock = true;
		try {
			lastProcessed = coordinator.lastProcessedBlock(); //reload under lock
			
			if (latestBlock > lastProcessed) {
				List<LogResult> logs = events.getLogs(lastProcessed);
				if (logs.isEmpty()) {
					log.info("All events published in parallel");
				} else {
					messageBroker.publish(logs);
				}
				
				if (events.getEndBlock() > lastProcessed)
					coordinator.saveState(events.getEndBlock());
				
				if (latestBlock > events.getEndBlock()) {
					//we process limited number of blocks at once
					log.info("Blocks to process: {}", latestBlock - events.getEndBlock());
					atLatestBlock = false;
				}
			} else if (lastProcessed > latestBlock) {
				log.warn("Lag detected. Node is on block {} while latest processed is {}", latestBlock, lastProcessed);
			} else { //latestProcessed == latestBlock
				log.info("At latest block: {}", latestBlock);
			}
		} finally {
			lock.release();
		}
		return atLatestBlock;
	}

	public void setBlockchain(BlockchainAdapter blockchain) {
		this.blockchain = blockchain;
	}

	public void setCoordinator(CoordinatorAdapter coordinator) {
		this.coordinator = coordinator;
	}

	public void setMessageBroker(MessageBrokerAdapter messageBroker) {
		this.messageBroker = messageBroker;
	}

	public void setSleepIntervalMs(long sleepIntervalMs) {
		this.sleepIntervalMs = sleepIntervalMs;
	}
	
}
