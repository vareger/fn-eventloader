package ethereum.eventloader.metrics;

import ethereum.eventloader.config.KafkaTopics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Metrics collector of events loading state
 *
 * @author Maxim Fischuk
 */
@Service
public class EventMetrics {
    private static final String BLOCK_NUMBER = "block_number";
    private static final String BLOCK_NUMBER_TAG = "tag";
    private static final String TYPE = "service";
    private static final String EVENT_PROCESSED = "events_processed";
    private static final String BLOCK_PROCESSED = "blocks_processed";
    private static final String MESSAGE = "message_published_topic";
    private static final String PROCESS_TIME = "events_fetch_time";
    private static final String EVENT_LOADER = "event_loader";

    private Long currentBlockNumber;
    private Long latestBlockNumber;
    private Counter eventProcessed;
    private Counter blockProcessed;
    private Timer processTime;
    private Map<String, Counter> topicCounters;

    @Autowired
    public EventMetrics(MeterRegistry registry, KafkaTopics topics) {

        this.currentBlockNumber = 0L;
        this.latestBlockNumber = 0L;

        Gauge.builder(BLOCK_NUMBER, this::getCurrentBlockNumber).tag(BLOCK_NUMBER_TAG, "current").tag(TYPE, EVENT_LOADER).register(registry);
        Gauge.builder(BLOCK_NUMBER, this::getLatestBlockNumber).tag(BLOCK_NUMBER_TAG, "latest").tag(TYPE, EVENT_LOADER).register(registry);
        Gauge.builder(BLOCK_NUMBER, this::getLag).tag(BLOCK_NUMBER_TAG, "lag").tag(TYPE, EVENT_LOADER).register(registry);
        this.eventProcessed = Counter.builder(EVENT_PROCESSED).tag(TYPE, EVENT_LOADER).register(registry);
        this.blockProcessed = Counter.builder(BLOCK_PROCESSED).tag(TYPE, EVENT_LOADER).register(registry);
        this.processTime = Timer.builder(PROCESS_TIME).tag(TYPE, EVENT_LOADER).publishPercentileHistogram().register(registry);
        topicCounters = new HashMap<>(topics.getEvents().size());
        topics.getEvents().forEach(topic -> {
            Counter counter = Counter.builder(MESSAGE)
                    .tag("topic", topic.getTopic())
                    .tag("event", topic.getEvent())
                    .tag("name", topic.getName())
                    .register(registry);
            topicCounters.put(topic.getTopic(), counter);
        });
        Counter counter = Counter.builder(MESSAGE)
                .tag("topic", topics.getBlocks())
                .tag("event", "")
                .tag("name", "Blocks")
                .register(registry);
        topicCounters.put(topics.getBlocks(), counter);
    }

    /**
     * Set current number of processed block
     *
     * @param currentBlock Number of block
     */
    public void setCurrentBlockNumber(Long currentBlock) {
        this.currentBlockNumber = currentBlock;
    }

    /**
     * Set latest seen number of block
     *
     * @param latestBlockNumber Number of block
     */
    public void setLatestBlockNumber(Long latestBlockNumber) {
        this.latestBlockNumber = latestBlockNumber;
    }

    /**
     * Add events count processed during iteration
     *
     * @param eventsCount Count of events
     */
    public void addProcessedEventsCount(Long eventsCount) {
        this.eventProcessed.increment(eventsCount.doubleValue());
    }

    /**
     * Add blocks count processed during iteration
     *
     * @param blocksCount Count of blocks
     */
    public void addProcessedBlocksCount(Long blocksCount) {
        this.blockProcessed.increment(blocksCount.doubleValue());
    }

    /**
     * Increment count of published messages to topic
     *
     * @param topic Topic name
     */
    public void addPublishedMessage(String topic) {
        this.topicCounters.get(topic).increment();
    }

    /**
     *  Execute function and measure the execution time
     *
     * @param callable Function to execute and measure the execution time
     * @param <T> The return type of the {@link Callable}
     * @return Result of execution {@code callable}
     * @throws Exception Any exception bubbling up from the callable
     */
    public <T> T recordExecutionTime(Callable<T> callable) throws Exception {
        return this.processTime.recordCallable(callable);
    }

    private Long getCurrentBlockNumber() {
        return currentBlockNumber;
    }

    private Long getLatestBlockNumber() {
        return latestBlockNumber;
    }

    private Long getLag() {
        return this.getCurrentBlockNumber() - this.getLatestBlockNumber();
    }
}
