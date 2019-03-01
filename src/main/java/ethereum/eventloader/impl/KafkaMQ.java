package ethereum.eventloader.impl;

import ethereum.eventloader.MessageBrokerAdapter;
import ethereum.eventloader.config.KafkaTopics;
import ethereum.eventloader.messages.BlockMessage;
import ethereum.eventloader.messages.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;

import java.math.BigInteger;
import java.util.List;

@Component
public class KafkaMQ implements MessageBrokerAdapter, SuccessCallback<SendResult<String, EventMessage>>, FailureCallback {
    private static final Logger log = LoggerFactory.getLogger(KafkaMQ.class);

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;
    private final KafkaTemplate<String, BlockMessage> kafkaBlockTemplate;

    private final KafkaTopics topics;

    @Autowired
    public KafkaMQ(KafkaTemplate<String, EventMessage> kafkaTemplate, KafkaTemplate<String, BlockMessage> kafkaBlockTemplate, KafkaTopics topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaBlockTemplate = kafkaBlockTemplate;
        this.topics = topics;
    }

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
                .peek(logMessage -> log.info("Sending event topic {}", logMessage.getTopics().get(0)))
                .forEach(this::sendEvent);

        long tookMs = System.currentTimeMillis() - start;
        log.info("Sent {} messages in {} ms.", logs.size(), tookMs);
    }

    @Override
    public void publishBlock(EthBlock.Block block) {
        if (topics.getBlocks() == null) {
            log.warn("Topic for block messages does not present, ignore sending!");
            return;
        }
        log.info("Sending block {}", block.getNumber().toString());
        this.kafkaBlockTemplate.send(
                topics.getBlocks(),
                block.getHash(),
                new BlockMessage(block.getNumber(), block.getHash())
        ).addCallback(
                message -> log.info("Published block {} to {}", message.getProducerRecord().key(), message.getRecordMetadata().topic()),
                this
        );
    }

    private void sendEvent(EventMessage eventMessage) {
        topics.getEvents()
                .stream()
                .filter(eventTopicMap -> eventTopicMap.equalsEvent(eventMessage))
                .forEach(eventTopicMap ->
                        this.kafkaTemplate.send(eventTopicMap.getTopic(), eventMessage.getTopics().get(0), eventMessage)
                                .addCallback(this, this)
                );
    }

    @Override
    public void reconnect() {
        log.debug("Method \"reconnect\" doesn't uses.");
    }

    @Override
    public void onFailure(Throwable throwable) {
        log.error("Error sending message", throwable);
    }

    @Override
    public void onSuccess(SendResult<String, EventMessage> eventMessageSendResult) {
        EventMessage eventMessage = eventMessageSendResult.getProducerRecord().value();
        log.info("Published {} event topic to {}", eventMessage.getTopics().get(0), eventMessageSendResult.getProducerRecord().topic());
    }
}
