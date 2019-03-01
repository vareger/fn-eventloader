package ethereum.eventloader;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
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
import org.web3j.protocol.core.methods.response.EthSyncing;

import java.io.File;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Loads Ethereum events into EMS topic
 */
@Service
public class EventLoader {
    private static final Logger log = LoggerFactory.getLogger(EventLoader.class);
    private static final Logger INFO_EMAIL = LoggerFactory.getLogger("ethereum.eventloader.INFO_EMAIL");

    private static final String ZNODE_PROCESSED_BLOCK = "/processed_block";

    private final BlockchainAdapter blockchain;
    private final MessageBrokerAdapter messageBroker;

    private final CuratorFramework curatorFramework;

    /**
     * Milliseconds to sleep between event-load attempts
     */
    @Value("${eventloader.sleep_interval_ms:3000}")
    private long sleepIntervalMs;

    /**
     * Milliseconds between email reports
     */
    @Value("${eventloader.report_email_interval_ms:3600000}")
    private long reportEmailIntervalMs;

    @Value("${eventloader.eth.data_folder:/eth_data}")
    private String dataFolder;

    @Value("${eventloader.eth.log_folder:/home/ubuntu/logs}")
    private String logFolder;

    @Value("#{web3jConfig.startBlock}")
    private BigInteger startBlock;

    private Stats stats;

    @Autowired
    public EventLoader(BlockchainAdapter blockchain, MessageBrokerAdapter messageBroker, CuratorFramework curatorFramework) {
        this.blockchain = blockchain;
        this.messageBroker = messageBroker;
        this.curatorFramework = curatorFramework;
        stats = new Stats();
    }

    @Scheduled(fixedDelay = 100L)
    public void update() {
        boolean atLatestBlock = false;
        try {
            atLatestBlock = eventLoadAttempt();
        } catch (Exception e) {
            stats.errors++;
            log.error("Event load failed, will reconnect and retry", e);
            sleep(sleepIntervalMs);
        }

        try {
            if (atLatestBlock) {
                Thread.sleep(sleepIntervalMs);
            }
        } catch (InterruptedException ignored) { }
    }

    @Scheduled(fixedDelayString = "#{${eventloader.report_email_interval_ms:3600000}}", initialDelay = 3600000)
    public void sendEmail() {
        emailReport();
        stats = new Stats();
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
        } catch (InterruptedException ex) {
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
                stats.lag = 0;
            } else if (lastProcessed > latestBlock) {
                long lag = lastProcessed - latestBlock;
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
            stats.locks++;

            long blocks = events.getEndBlock() - lastProcessed;
            stats.blocksProcessed += blocks > 0 ? blocks : 0;
            List<LogResult> logs = events.getLogs(lastProcessed);
            blockchain.loadBlocks(lastProcessed, events.getEndBlock()).sequential().subscribe(messageBroker::publishBlock).dispose();
            if (logs.isEmpty()) {
                log.info("All events published in parallel");
            } else {
                stats.eventsPublished += logs.size();
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

            long queueSize = latestBlock - Math.max(lastProcessed, events.getEndBlock());
            stats.queueSize = queueSize > 0 ? queueSize : 0;
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

    public void setSleepIntervalMs(long sleepIntervalMs) {
        this.sleepIntervalMs = sleepIntervalMs;
    }

    private static class Stats {
        int eventsPublished;
        int blocksProcessed;
        long lag;
        long queueSize;
        int locks;
        int errors;

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }
    }
}
