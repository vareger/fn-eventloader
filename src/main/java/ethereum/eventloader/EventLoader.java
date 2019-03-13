package ethereum.eventloader;

import ethereum.eventloader.metrics.EventMetrics;
import io.micrometer.core.annotation.Timed;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Loads Ethereum events into EMS topic
 */
@Service
public class EventLoader {
    private static final Logger log = LoggerFactory.getLogger(EventLoader.class);

    private static final String ZNODE_PROCESSED_BLOCK = "/processed_block";

    private final BlockchainAdapter blockchain;
    private final MessageBrokerAdapter messageBroker;

    private final CuratorFramework curatorFramework;

    private final EventMetrics metrics;

    /**
     * Milliseconds to sleep between event-load attempts
     */
    @Value("${eventloader.sleep_interval_ms:3000}")
    private long sleepIntervalMs;

    @Value("#{web3jConfig.startBlock}")
    private BigInteger startBlock;

    @Autowired
    public EventLoader(BlockchainAdapter blockchain, MessageBrokerAdapter messageBroker, CuratorFramework curatorFramework, EventMetrics metrics) {
        this.blockchain = blockchain;
        this.messageBroker = messageBroker;
        this.curatorFramework = curatorFramework;
        this.metrics = metrics;
    }

    @Timed(longTask = true, value = "loading_time")
    @Scheduled(fixedDelay = 100L)
    public void update() {
        boolean atLatestBlock = false;
        try {
            atLatestBlock = this.metrics.recordExecutionTime(this::eventLoadAttempt);
        } catch (Exception e) {
            log.error("Event load failed, will retry", e);
            sleep(sleepIntervalMs);
        }

        try {
            if (atLatestBlock) {
                Thread.sleep(sleepIntervalMs);
            }
        } catch (InterruptedException ignored) { }
    }

    private static void sleep(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException ex) {
            log.error("Sleep error", ex);
        }
    }

    /**
     * Main eventloader logic is here.
     * <p>
     * 1) Obtain Zookeeper lock
     * 2) Get latest block number in Ethereum node blockchain
     * 3) Get last processed block number from Zookeeper
     * 4) If needed, query event logs from Ethereum node
     * 5) If needed, publish events to message broker
     * 6) Save updated "last processed block" to Zookeeper
     * 7) Release Zookeeper lock (Auto unlock)
     *
     * @return true if at latest block
     */
    @SuppressWarnings("rawtypes")
    boolean eventLoadAttempt() {
        boolean atLatestBlock = true;
        InterProcessMutex mutex = new InterProcessMutex(curatorFramework, ZNODE_PROCESSED_BLOCK);
        try (Locker ignored = new Locker(mutex, 120, TimeUnit.SECONDS)) {
            DistributedAtomicLong lastBlock = new DistributedAtomicLong(
                    curatorFramework,
                    ZNODE_PROCESSED_BLOCK,
                    new ExponentialBackoffRetry(1000, 5)
            );
            long latestBlock = blockchain.latestBlockNumber();
            long lastProcessed = lastBlock.get().preValue();
            if (startBlock.longValue() > lastProcessed) {
                log.info("Last processed is least of start block, updated: {} ==> {}", lastProcessed, startBlock.toString());
                lastProcessed = startBlock.longValue();
            }
            Events events;
            if (latestBlock > lastProcessed) {
                events = blockchain.eventsLog(lastProcessed, latestBlock);
            } else if (lastProcessed > latestBlock) {
                long lag = lastProcessed - latestBlock;
                if (lag > 50) {
                    log.warn("Lag detected. Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
                } else {
                    log.info("Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
                }
                waitForNodeToCatchup(lag);
                return true;
            } else {
                log.info("At latest block: {}", latestBlock);
                return true;
            }

            long blocks = events.getEndBlock() - lastProcessed;
            List<LogResult> logs = events.getLogs(lastProcessed);
            blockchain.loadBlocks(lastProcessed, events.getEndBlock()).sequential().subscribe(messageBroker::publishBlock).dispose();
            if (logs.isEmpty()) {
                log.info("All events published in parallel");
            } else {
                messageBroker.publish(logs);
            }

            if (events.getEndBlock() > lastProcessed) {
                lastBlock.forceSet(events.getEndBlock());
            }

            if (latestBlock > events.getEndBlock()) {
                //we process limited number of blocks at once
                log.info("Blocks to process: {}", latestBlock - events.getEndBlock());
                atLatestBlock = false;
            }

            this.metrics.setCurrentBlockNumber(events.getEndBlock());
            this.metrics.addProcessedEventsCount((long)logs.size());
            this.metrics.addProcessedBlocksCount(blocks > 0 ? blocks : 0);
            this.metrics.setLatestBlockNumber(latestBlock);
        } catch (Exception e) {
            log.error("Loader error", e);
        }
        return atLatestBlock;
    }

    private static void waitForNodeToCatchup(long gap) {
        if (gap > 1000) {
            log.info("Sleeping for 60 sec...");
            sleep(60000); //node is syncing so lets give it some time to catchup
        } else if (gap > 100) {
            log.info("Sleeping for 30 sec...");
            sleep(30000);
        }
    }
}
