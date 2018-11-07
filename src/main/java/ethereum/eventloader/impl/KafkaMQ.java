package ethereum.eventloader.impl;

import ethereum.eventloader.MessageBrokerAdapter;
import ethereum.eventloader.messages.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
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

    @Autowired
    private KafkaTemplate<String, EventMessage> kafkaTemplate;

    @Override
    public void publish(List<EthLog.LogResult> logs) {
        if (logs.isEmpty()) {
            return;
        }
        final long start = System.currentTimeMillis();
        log.info("Sending {} events", logs.size());
        this.kafkaTemplate.setDefaultTopic("ethereum.events.all");
        logs.stream()
                .map(logResult -> (EthLog.LogObject) logResult)
                .map(EventMessage::new)
                .peek(logMessage -> log.info("Sending event topic {}", logMessage.getTopics().get(0)))
                .forEach(logMessage -> this.kafkaTemplate.sendDefault(logMessage.getTopics().get(0), logMessage).addCallback(this, this));

        long tookMs = System.currentTimeMillis() - start;
        log.info("Published {} messages in {} ms.", logs.size(), tookMs);
    }

    @Override
    public void reconnect() {
        log.debug("Method \"reconnect\" doesn't uses.");
    }

    @KafkaListener(topics = "ethereum.events.all")
    public void receiveMessage(EventMessage message) {
        log.info("Received topic: {}", message.getTopics().get(0));
    }

    @Override
    public void onFailure(Throwable throwable) {
        log.error("Error sending message", throwable);
    }

    @Override
    public void onSuccess(SendResult<String, EventMessage> eventMessageSendResult) {
        EventMessage eventMessage = eventMessageSendResult.getProducerRecord().value();
        log.info("Published {} topic. Data {}", eventMessage.getTopics().get(0), eventMessage.getData());
    }
}
