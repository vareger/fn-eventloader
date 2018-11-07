package ethereum.eventloader;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.core.methods.response.EthSyncing;

/**
 * Loads Ethereum events into EMS topic
 */
@Component
public class EventLoader {
	private static final Logger log = LoggerFactory.getLogger(EventLoader.class);
	private static final Logger INFO_EMAIL = LoggerFactory.getLogger("ethereum.eventloader.INFO_EMAIL");
	
	@Autowired private BlockchainAdapter blockchain;
	@Autowired private CoordinatorAdapter coordinator;
	@Autowired private MessageBrokerAdapter messageBroker;

	/** Milliseconds to sleep between event-load attempts */
	@Value("${eventloader.sleep_interval_ms:3000}") 
	private long sleepIntervalMs;

	/** Milliseconds between email reports */
	@Value("${eventloader.report_email_interval_ms:3600000}") 
	private long reportEmailIntervalMs;
	
	@Value("${eventloader.eth.data_folder:/eth_data}") 
	private String dataFolder;
	
	@Value("${eventloader.eth.log_folder:/home/ubuntu/logs}") 
	private String logFolder;
	
	private Stats stats;
	
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
		long lastReportTimeMs = System.currentTimeMillis();
		stats = new Stats();
//		emailReport(); //fail fast is something wrong with reporting
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
				stats.errors++;
				log.error("Event load failed, will reconnect and retry", e);
				sleep(sleepIntervalMs);
				connected = reconnect();
			}
			
			try {
				if (atLatestBlock)
					Thread.sleep(sleepIntervalMs);
			} catch (InterruptedException e) {}
			
			long nowMs = System.currentTimeMillis();
			if (nowMs - lastReportTimeMs > reportEmailIntervalMs) {
				emailReport();
				stats = new Stats();
				lastReportTimeMs = nowMs;
			}
		}
	}

	private void emailReport() {
		NumberFormat fmt = new DecimalFormat("#0.00"); 
		
		StringBuilder b = new StringBuilder();
		b.append("Disk usage: ").append(dataFolder).append("\n");
		b.append(fmt.format(diskUsagePct(dataFolder))).append("%");
		
		b.append("\n\nDisk usage: ").append(logFolder).append("\n");
		b.append(fmt.format(diskUsagePct(logFolder))).append("%");
		
		b.append("\n\nStats")
			.append("\n  lag: ").append(stats.lag)
			.append("\n  queue: ").append(stats.queueSize)
			.append("\n  blocks-processed: ").append(stats.blocksProcessed)
			.append("\n  events-published: ").append(stats.eventsPublished)
			.append("\n  locks-obtained: ").append(stats.locks)
			.append("\n  errors: ").append(stats.errors);
		
		b.append("\n\nSyncing: ");
		
		EthSyncing res = blockchain.syncing();
		if (!res.isSyncing()) {
			b.append("No");
		} else {
			b.append("Yes");
			EthSyncing.Syncing sync = (EthSyncing.Syncing) res.getResult();
			int current = Integer.parseInt(sync.getCurrentBlock().substring(2), 16);
			b.append("\n  current: ").append(current);
			int latest = Integer.parseInt(sync.getHighestBlock().substring(2), 16);
			b.append("\n  latest:  ").append(latest);
			b.append("\n  pending: ").append(latest - current);
		}
		
		b.append("\n\nGenerated at: ").append(new Date());
		
		INFO_EMAIL.info(b.toString());
	}

	static double diskUsagePct(String path) {
		File folder = new File(path);
		if (!folder.exists() || !folder.isDirectory()) {
			throw new RuntimeException("Folder expected: " + folder);
		}
		return 100 - ((double) folder.getFreeSpace() / folder.getTotalSpace()) * 100;
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
	 * Main eventloader logic is here.
	 * 
	 * 1) Get latest block number in Ethereum node blockchain
	 * 2) Get last processed block number from Zookeeper
	 * 3) If needed, query event logs from Ethereum node
	 * 4) Obtain Zookeeper lock
	 * 5) Reload last processed block number from Zookeeper 
	 *	  (it can change in parallel, reliable data can be retrieved with ZK lock only)
	 * 6) If needed, publish events to message broker
	 * 7) Save updated "last processed block" to Zookeeper
	 * 8) Release Zookeeper lock
	 * 
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
			stats.lag = 0;
		} else if (lastProcessed > latestBlock) {
			int lag = lastProcessed - latestBlock;
			if (lag > 50) {
				log.warn("Lag detected. Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
			} else {
				log.info("Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
			}
			waitForNodeToCatchup(lag);
			stats.lag = lag;
			return true;
		} else {
			log.info("At latest block: {}", latestBlock);
			stats.lag = 0;
			return true;
		}
		
		DistributedLock lock = coordinator.obtainLock();
		stats.locks++;
		boolean atLatestBlock = true;
		try {
			lastProcessed = coordinator.lastProcessedBlock(); //reload under lock
			
			if (latestBlock > lastProcessed) {
				stats.lag = 0;
				int blocks = events.getEndBlock() - lastProcessed;
				stats.blocksProcessed += blocks > 0 ? blocks : 0;
				List<LogResult> logs = events.getLogs(lastProcessed);
				if (logs.isEmpty()) {
					log.info("All events published in parallel");
				} else {
					stats.eventsPublished += logs.size();
					messageBroker.publish(logs);
				}
				
				if (events.getEndBlock() > lastProcessed)
					coordinator.saveState(events.getEndBlock());
				
				if (latestBlock > events.getEndBlock()) {
					//we process limited number of blocks at once
					log.info("Blocks to process: {}", latestBlock - events.getEndBlock());
					atLatestBlock = false;
				}
				
				int queueSize = latestBlock - Math.max(lastProcessed, events.getEndBlock());
				stats.queueSize = queueSize > 0 ? queueSize : 0;
			} else if (lastProcessed > latestBlock) {
				int lag = lastProcessed - latestBlock;
				if (lag > 50) {
					log.warn("Lag detected. Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
				} else {
					log.info("Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
				}
				waitForNodeToCatchup(lag);
				stats.lag = lag;
			} else { //latestProcessed == latestBlock
				log.info("At latest block: {}", latestBlock);
				stats.lag = 0;
			}
		} finally {
			lock.release();
		}
		return atLatestBlock;
	}

	private static void waitForNodeToCatchup(int gap) {
		if (gap > 1000) {
			log.info("Sleeping for 60 sec...");
			sleep(60000); //node is syncing so lets give it some time to catchup
		} else if (gap > 100) {
			log.info("Sleeping for 30 sec...");
			sleep(30000);
		}
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
	
	private static class Stats {
		int eventsPublished;
		int blocksProcessed;
		int lag;
		int queueSize;
		int locks;
		int errors;
		
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
		}
	}
}
