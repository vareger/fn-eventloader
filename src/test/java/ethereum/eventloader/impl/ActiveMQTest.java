package ethereum.eventloader.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import ethereum.eventloader.BlockchainAdapter;
import ethereum.eventloader.Events;
import ethereum.eventloader.MessageBrokerAdapter;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ImplTC.class)
@TestPropertySource("/test.properties")
public class ActiveMQTest extends Assert {
	Logger log = LoggerFactory.getLogger("TEST");
	
	@Autowired BlockchainAdapter blockchain;
	@Autowired MessageBrokerAdapter jms;
	
	@Test
	public void test_publish_events() throws Exception {
		int latest = blockchain.latestBlockNumber();
		Events events = blockchain.eventsLog(latest-10, latest-9);
		jms.publish(events);
	}
	
	@Test
	public void test_performance() throws Exception {		
		Events events = blockchain.eventsLog(5897110, 5897120);
		
		//warmup
		jms.publish(events);
		
		long start = System.currentTimeMillis();
		int cnt = 0;
		while (cnt < 10) {
			jms.publish(events);
			cnt++;
		}
		long took = System.currentTimeMillis() - start;
		long avg = took/cnt;
		log.info("Published {} events X {} times in total {} ms (avg: {} ms))",
				events.getLogs().size(), cnt, took, avg);
	}
}
