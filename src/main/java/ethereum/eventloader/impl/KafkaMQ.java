package ethereum.eventloader.impl;

import ethereum.eventloader.MessageBrokerAdapter;
import ethereum.eventloader.config.KafkaTopics;
import ethereum.eventloader.messages.BlockMessage;
import ethereum.eventloader.messages.EventMessage;
import ethereum.eventloader.metrics.EventMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;

import java.util.List;

/**
 * Kafka implementation of Message Broker Publisher
 *
 * @see ethereum.eventloader.MessageBrokerAdapter
 * @author Maxim Fischuk
 */
@Component
public class KafkaMQ implements MessageBrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(KafkaMQ.class);

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;
    private final KafkaTemplate<String, BlockMessage> kafkaBlockTemplate;
    private final EventMetrics metrics;

    private final KafkaTopics topics;

    @Autowired
    public KafkaMQ(KafkaTemplate<String, EventMessage> kafkaTemplate, KafkaTemplate<String, BlockMessage> kafkaBlockTemplate, KafkaTopics topics, EventMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaBlockTemplate = kafkaBlockTemplate;
        this.topics = topics;
        this.metrics = metrics;
    }

    /**
     * Publish logs to specific topics
     *
     * @param logs List of logs loaded from transaction
     */
    @Override
    public void publish(List<EthLog.LogResult> logs) {
        if (logs.isEmpty()) {
            log.warn("Logs is empty, ignore sending!");
            return;
        }
        final long start = System.currentTimeMillis();
        log.info("Sending {} events", logs.size());
        logs.stream()
                .map(logResult -> (EthLog.LogObject) logResult)
                .map(EventMessage::new)
                .filter(logMessage -> !logMessage.getTopics().isEmpty())
                .peek(logMessage -> log.debug("Sending event topic {}", logMessage.getTopics().get(0)))
                .forEach(this::sendEvent);

        long tookMs = System.currentTimeMillis() - start;
        log.info("Sent {} messages in {} ms.", logs.size(), tookMs);
    }

    /**
     * Publish block information to specific topic
     *
     * @param block Ethereum block response
     */
    @Override
    public void publishBlock(EthBlock.Block block) {
        if (topics.getBlocks() != null) {
            log.debug("Sending block {}", block.getNumber().toString());
            this.kafkaBlockTemplate.send(
                    topics.getBlocks(),
                    block.getHash(),
                    new BlockMessage(block.getNumber(), block.getHash())
            ).addCallback(this::onBlockSuccess, this::onFailure);
        }
        if (topics.getBlocksFull() != null) {
            log.debug("Sending block {}", block.getNumber().toString());
            this.kafkaBlockTemplate.send(
                    topics.getBlocksFull(),
                    block.getHash(),
                    new BlockMessage(block.getNumber(), block.getHash(), block)
            ).addCallback(this::onBlockSuccess, this::onFailure);
        }
    }

    private void sendEvent(EventMessage eventMessage) {
        topics.getEvents()
                .stream()
                .filter(eventTopicMap -> eventTopicMap.equalsEvent(eventMessage))
                .forEach(eventTopicMap ->
                        this.kafkaTemplate.send(eventTopicMap.getTopic(), eventMessage.getTopics().get(0), eventMessage)
                                .addCallback(this::onEventSuccess, this::onFailure)
                );
    }

    @Override
    public void reconnect() {
        log.debug("Method \"reconnect\" doesn't uses.");
    }

    private void onFailure(Throwable throwable) {
        log.error("Error sending message", throwable);
    }

    private void onEventSuccess(SendResult<String, EventMessage> eventMessageSendResult) {
        EventMessage eventMessage = eventMessageSendResult.getProducerRecord().value();
        this.metrics.addPublishedMessage(eventMessageSendResult.getProducerRecord().topic());
        log.debug("Published {} event topic to {}", eventMessage.getTopics().get(0), eventMessageSendResult.getProducerRecord().topic());
    }

    private void onBlockSuccess(SendResult<String, BlockMessage> blockMessageSendResult) {
        log.debug("Published block {} to {}", blockMessageSendResult.getProducerRecord().key(), blockMessageSendResult.getRecordMetadata().topic());
        this.metrics.addPublishedMessage(blockMessageSendResult.getProducerRecord().topic());
    }
}
