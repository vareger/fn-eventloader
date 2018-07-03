package ethereum.eventloader.impl;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthLog.LogObject;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

import ethereum.eventloader.Events;
import ethereum.eventloader.MessageBrokerAdapter;
import ethereum.eventloader.MessageBrokerException;

/**
 * Publishes events to ActiveMQ topic
 * 
 * TODO: use spring connection wrappers for automatic reconnects
 */
@Component
public class ActiveMQ implements MessageBrokerAdapter {
	private static final Logger log = LoggerFactory.getLogger(ActiveMQ.class);
	
	private Connection connection;
	private Session session;
	private MessageProducer producer;
	
	@Value("${eventloader.jms.all_topic}") 
	private String allTopic;
	
	@Value("${eventloader.jms.url}") 
	private String url;
	
	@Override 
	@SuppressWarnings("rawtypes")
	public void publish(Events events) {
		if (events.getLogs().isEmpty())
			return;
		
		try {
			long start = System.currentTimeMillis();
			log.info("Sending {} events to {}", events.getLogs().size(), allTopic);
			for (LogResult res: events.getLogs()) {
				LogObject evt = (LogObject) res;
				TextMessage msg = session.createTextMessage();
				msg.setStringProperty("address", evt.getAddress());
				msg.setStringProperty("txn_hash", evt.getTransactionHash());
				msg.setStringProperty("block_hash", evt.getBlockHash());
				msg.setStringProperty("block_number", evt.getBlockNumber().toString());
				msg.setText(evt.getData());
				msg.setStringProperty("type", evt.getType());
				int i = 0;
				for (String topic: evt.getTopics()) {
					msg.setStringProperty("topic-" + i, topic);
					i++;
				}
				if (log.isDebugEnabled())
					log.debug(msg.toString());
				producer.send(msg);
			}
			session.commit();
			long tookMs = System.currentTimeMillis() - start;
			log.info("Published {} messages in {} ms", events.getLogs().size(), tookMs);
		} catch (Exception e) {
			try {
				session.rollback();
			} catch (Exception re) {
				log.error("Failed to rollback JMS transaction", e);
			}
			throw new MessageBrokerException(e);
		}
	}

	@PostConstruct
	public void start() {
		log.info("Connecting to JMS at: {}", url);
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
		try {
			connection = connectionFactory.createConnection();
			connection.start();
			session = connection.createSession(true, Session.SESSION_TRANSACTED);
			Destination destination = session.createTopic(allTopic);
			producer = session.createProducer(destination);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            log.info("Connected");
		} catch (Exception e) {
			if (connection != null) {
				try {
					connection.close();
				} catch (JMSException jmse) {
					log.error("Failed to close JMS connection");
				}
			}
			throw new RuntimeException("JMS connection failed", e);
		}
	}
	
	@PreDestroy
	public void stop() {
		try {
			session.close();
		} catch (JMSException jmse) {
			log.error("Failed to close JMS session");
		}
		
		try {
			connection.close();
		} catch (JMSException jmse) {
			log.error("Failed to close JMS connection");
		}
	}
}
