package ethereum.eventloader.component;

import ethereum.eventloader.config.KafkaTopics;
import ethereum.eventloader.messages.BlockMessage;
import ethereum.eventloader.messages.EventMessage;
import ethereum.eventloader.metrics.EventMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;

import java.util.List;

/**
 * Kafka implementation of Message Broker Publisher
 *
 * @see MessageBrokerAdapter
 * @author Maxim Fischuk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMQ implements MessageBrokerAdapter {

    private final KafkaTopics topics;

    private final EventMetrics metrics;

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;

    private final KafkaTemplate<String, BlockMessage> kafkaBlockTemplate;

    /**
     * Publish logs to specific topics
     *
     * @param logs List of logs loaded from transaction
     */
    @Override
    public void publish(List<EthLog.LogResult> logs) {
        if (logs.isEmpty()) {
            log.warn("[KAFKA] logs is empty, ignore sending!");
            return;
        }
        final long start = System.currentTimeMillis();
        log.info("[KAFKA] sending {} events", logs.size());
        logs.stream()
                .map(logResult -> (EthLog.LogObject) logResult)
                .map(EventMessage::new)
                .filter(logMessage -> !logMessage.getTopics().isEmpty())
                .peek(logMessage -> log.debug("[KAFKA] sending event topic {}", logMessage.getTopics().get(0)))
                .forEach(this::sendEvent);

        long tookMs = System.currentTimeMillis() - start;
        log.info("[KAFKA] sent {} messages in {} ms.", logs.size(), tookMs);
    }

    /**
     * Publish block information to specific topic
     *
     * @param block Ethereum block response
     */
    @Override
    public void publishBlock(EthBlock.Block block) {
        if (topics.getBlocks() != null) {
            log.debug("[KAFKA] sending block {}", block.getNumber().toString());
            this.kafkaBlockTemplate.send(
                    topics.getBlocks(),
                    block.getHash(),
                    new BlockMessage(block.getNumber(), block.getHash())
            ).addCallback(this::onBlockSuccess, this::onFailure);
        }
        if (topics.getBlocksFull() != null) {
            log.debug("[KAFKA] sending block {}", block.getNumber().toString());
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
        log.debug("[KAFKA] method \"reconnect\" doesn't uses.");
    }

    private void onFailure(Throwable throwable) {
        log.error("[KAFKA] error sending message", throwable);
    }

    private void onEventSuccess(SendResult<String, EventMessage> eventMessageSendResult) {
        EventMessage eventMessage = eventMessageSendResult.getProducerRecord().value();
        this.metrics.addPublishedMessage(eventMessageSendResult.getProducerRecord().topic());
        log.debug("[KAFKA] published {} event topic to {}", eventMessage.getTopics().get(0), eventMessageSendResult.getProducerRecord().topic());
    }

    private void onBlockSuccess(SendResult<String, BlockMessage> blockMessageSendResult) {
        log.debug("[KAFKA] published block {} to {}", blockMessageSendResult.getProducerRecord().key(), blockMessageSendResult.getRecordMetadata().topic());
        this.metrics.addPublishedMessage(blockMessageSendResult.getProducerRecord().topic());
    }
}
