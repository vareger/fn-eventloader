package ethereum.eventloader.impl;

import ethereum.eventloader.MessageBrokerAdapter;
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

    private static final String ERC20_TRANSFER_EVENT_TOPIC = "ethereum.events.erc20.transfer";
    private static final String ERC20_TRANSFER_EVENT = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @Autowired
    private KafkaTemplate<String, EventMessage> kafkaTemplate;

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
                .peek(logMessage -> log.info("Sending event topic {}", logMessage.getTopics().get(0)))
                .forEach(this::sendEvent);

        long tookMs = System.currentTimeMillis() - start;
        log.info("Sent {} messages in {} ms.", logs.size(), tookMs);
    }

    private void sendEvent(EventMessage eventMessage) {
        this.kafkaTemplate.sendDefault(eventMessage.getTopics().get(0), eventMessage).addCallback(this, this);
        if (eventMessage.getTopics().get(0).equalsIgnoreCase(ERC20_TRANSFER_EVENT)) {
            this.kafkaTemplate.send(ERC20_TRANSFER_EVENT_TOPIC, eventMessage.getContractAddress(), eventMessage).addCallback(this, this);
        }
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
