package ethereum.eventloader;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import ethereum.eventloader.impl.ImplTC;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ImplTC.class)
@TestPropertySource("/test.properties")
public class SampleSubscriber {
	Logger log = LoggerFactory.getLogger("TEST");
	
	private Connection connection;
	private Session session;
	
	@Value("${eventloader.jms.all_topic}") 
	private String allTopic;
	
	@Value("${eventloader.jms.url}") 
	private String url;
	
	@Test @Ignore("not for regular run: hangs forever")
	public void test_sample_subscriber() throws Exception {
		log.info("Connecting to JMS at: {}", url);
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
		try {
			connection = connectionFactory.createConnection();
			connection.setClientID("test_client1");
			connection.start();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Topic topic = session.createTopic(allTopic);
			
			TopicSubscriber sub1 = session.createDurableSubscriber(topic, "test_durable1", "address = '0x79650799e7899a802cb96c0bc33a6a8d4ce4936c'", true);
			sub1.setMessageListener(new MessageListener() {
				@Override
				public void onMessage(Message msg) {
					log.info("[SUB1] Received: " + msg);
				}
			});
			
			TopicSubscriber sub2 = session.createDurableSubscriber(topic, "test_durable2", "address = '0x4212fea9fec90236ecc51e41e2096b16ceb84555'", true);
			sub2.setMessageListener(new MessageListener() {
				@Override
				public void onMessage(Message msg) {
					log.info("[SUB2] Received: " + msg);
				}
			});
			
			Thread.sleep(Long.MAX_VALUE);
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
	
}
