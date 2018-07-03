package ethereum.eventloader;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads Ethereum events into EMS topic
 * 
 * TODO: fast mode for initial catchup
 * TODO: possibility to reload specific blocks range
 * TODO: move ethereum calls out of the lock
 * TODO: test for EventLoader with mocked eth/ems/zk
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
		while (true) {
			boolean atLatestBlock = eventLoadAttempt();
			try {
				if (atLatestBlock)
					Thread.sleep(sleepIntervalMs);
			} catch (InterruptedException e) {}
		}
	}

	boolean eventLoadAttempt() {
		DistributedLock lock = coordinator.obtainLock();
		boolean atLatestBlock = true;
		try {
			int latestBlock = blockchain.latestBlockNumber();
			int lastProcessed = coordinator.lastProcessedBlock();
			
			if (latestBlock > lastProcessed) {
				Events events = blockchain.eventsLog(lastProcessed, latestBlock);
				messageBroker.publish(events);
				coordinator.saveState(events);
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
		} catch (Exception e) {
			log.error("Event load attempt failed, will continue trying", e);
		} finally {
			lock.release();
		}
		return atLatestBlock;
	}
}
