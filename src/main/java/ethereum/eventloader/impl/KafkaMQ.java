package ethereum.eventloader.impl;

import ethereum.eventloader.MessageBrokerAdapter;
import ethereum.eventloader.config.KafkaTopics;
import ethereum.eventloader.messages.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import org.web3j.protocol.core.methods.response.EthLog;

import java.util.List;

@Component
public class KafkaMQ implements MessageBrokerAdapter, SuccessCallback<SendResult<String, EventMessage>>, FailureCallback {
    private static final Logger log = LoggerFactory.getLogger(KafkaMQ.class);

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;

    private final KafkaTopics topics;

    @Autowired
    public KafkaMQ(KafkaTemplate<String, EventMessage> kafkaTemplate, KafkaTopics topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void publish(List<EthLog.LogResult> logs) {
        if (logs.isEmpty()) {
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

    private void sendEvent(EventMessage eventMessage) {
        this.kafkaTemplate.send(topics.getAll(), eventMessage.getTopics().get(0), eventMessage).addCallback(this, this);
        topics.getEvents()
                .stream()
                .filter(eventTopicMap -> eventTopicMap.equalsEvent(eventMessage))
                .findAny()
                .ifPresent(eventTopicMap ->
                        this.kafkaTemplate.send(eventTopicMap.getTopic(), eventMessage.getContractAddress(), eventMessage)
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
