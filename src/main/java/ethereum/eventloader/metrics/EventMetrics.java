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

@Service
public class EventMetrics {
    private static final String BLOCK_NUMBER = "block_number";
    private static final String BLOCK_NUMBER_TAG = "tag";
    private static final String TYPE = "service";
    private static final String EVENT_PROCESSED = "events_processed";
    private static final String MESSAGE = "message_published_topic";
    private static final String PROCESS_TIME = "events_fetch_time";
    public static final String EVENT_LOADER = "event_loader";

    private Long currentBlockNumber;
    private Long latestBlockNumber;
    private Counter eventProcessed;
    private Timer processTime;
    private Map<String, Counter> topicCounters;

    @Autowired
    public EventMetrics(MeterRegistry registry, KafkaTopics topics) {
        Gauge.builder(BLOCK_NUMBER, this::getCurrentBlockNumber).tag(BLOCK_NUMBER_TAG, "current").tag(TYPE, EVENT_LOADER).register(registry);
        Gauge.builder(BLOCK_NUMBER, this::getLatestBlockNumber).tag(BLOCK_NUMBER_TAG, "latest").tag(TYPE, EVENT_LOADER).register(registry);
        Gauge.builder(BLOCK_NUMBER, this::getLag).tag(BLOCK_NUMBER_TAG, "lag").tag(TYPE, EVENT_LOADER).register(registry);
        this.eventProcessed = Counter.builder(EVENT_PROCESSED).tag(TYPE, EVENT_LOADER).register(registry);
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

    public void setCurrentBlockNumber(Long currentBlock) {
        this.currentBlockNumber = currentBlock;
    }

    public void setLatestBlockNumber(Long latestBlockNumber) {
        this.latestBlockNumber = latestBlockNumber;
    }

    public void addProcessedEventsCount(Long eventsCount) {
        this.eventProcessed.increment(eventsCount.doubleValue());
    }

    public void addPublishedMessage(String topic) {
        this.topicCounters.get(topic).increment();
    }

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
