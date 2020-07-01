package ethereum.eventloader;

import ethereum.eventloader.component.BlockchainAdapter;
import ethereum.eventloader.component.entity.Events;
import ethereum.eventloader.component.MessageBrokerAdapter;
import ethereum.eventloader.metrics.EventMetrics;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.apache.curator.retry.ExponentialBackoffRetry;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLoader {

    private static final String ZNODE_PROCESSED_BLOCK = "/processed_block";

    private final EventMetrics metrics;

    private final BlockchainAdapter blockchain;

    private final MessageBrokerAdapter messageBroker;

    private final CuratorFramework curatorFramework;

    /**
     * Milliseconds to sleep between event-load attempts
     */
    @Value("${eventloader.sleep_interval_ms:3000}")
    private long sleepIntervalMs;

    @Value("#{web3jConfig.startBlock}")
    private BigInteger startBlock;

    @Timed(longTask = true, value = "loading_time")
    @Scheduled(fixedDelay = 100L)
    public void update() {
        boolean atLatestBlock = false;
        try {
            atLatestBlock = this.metrics.recordExecutionTime(this::eventLoadAttempt);
        } catch (Exception ex) {
            log.error("[SERVICE] event load failed, will retry", ex);
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
            log.error("[SERVICE] sleep error", ex);
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
                log.info("[SERVICE] last processed is least of start block, updated: {} ==> {}", lastProcessed, startBlock.toString());
                lastProcessed = startBlock.longValue();
            }
            Events events;
            if (latestBlock > lastProcessed) {
                events = blockchain.eventsLog(lastProcessed, latestBlock);
            } else if (lastProcessed > latestBlock) {
                long lag = lastProcessed - latestBlock;
                if (lag > 50) {
                    log.warn("[SERVICE] lag detected. Node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
                } else {
                    log.info("[SERVICE] node is on block {} while latest processed is {}. Lag: {}", latestBlock, lastProcessed, lag);
                }
                waitForNodeToCatchup(lag);
                return true;
            } else {
                log.info("[SERVICE] at latest block: {}", latestBlock);
                return true;
            }

            long blocks = events.getEndBlock() - lastProcessed;
            List<LogResult> logs = events.getLogs(lastProcessed);
            blockchain.loadBlocks(lastProcessed, events.getEndBlock())
                    .forEach(messageBroker::publishBlock);
            if (logs.isEmpty()) {
                log.info("[SERVICE] all events published");
            } else {
                messageBroker.publish(logs);
            }

            if (events.getEndBlock() > lastProcessed) {
                lastBlock.forceSet(events.getEndBlock());
            }

            if (latestBlock > events.getEndBlock()) {
                //we process limited number of blocks at once
                log.info("[SERVICE] blocks to process: {}", latestBlock - events.getEndBlock());
                atLatestBlock = false;
            }

            this.metrics.setCurrentBlockNumber(events.getEndBlock());
            this.metrics.addProcessedEventsCount((long)logs.size());
            this.metrics.addProcessedBlocksCount(blocks > 0 ? blocks : 0);
            this.metrics.setLatestBlockNumber(latestBlock);
        } catch (Exception ex) {
            log.error("[SERVICE] loader error", ex);
        }
        return atLatestBlock;
    }

    private static void waitForNodeToCatchup(long gap) {
        if (gap > 1000) {
            log.info("[SERVICE] sleeping for 60 sec...");
            sleep(60000); // node is syncing so lets give it some time to catchup
        } else if (gap > 100) {
            log.info("[SERVICE] sleeping for 30 sec...");
            sleep(30000);
        }
    }
}
