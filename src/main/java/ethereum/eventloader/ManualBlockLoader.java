package ethereum.eventloader;

import java.util.List;

import ethereum.eventloader.impl.KafkaMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

import ethereum.eventloader.impl.Web3jBlockchain;

/**
 * Special utility to load range of blocks manually.
 */
public class ManualBlockLoader {
	private static final Logger log = LoggerFactory.getLogger(ManualBlockLoader.class);
	
	@SuppressWarnings("rawtypes")
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			log.info("Expected args: <startBlock> <endBlock>");
			System.exit(0);
		}
		
		int startBlock = Integer.parseInt(args[0]);
		int endBlock = Integer.parseInt(args[1]);
		
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.scan("ethereum.eventloader.impl");
		ctx.getEnvironment().getPropertySources().addFirst(new ResourcePropertySource("/application.properties"));
		ctx.refresh();
		ctx.start();
		
		log.info("Started Spring context");
		
		Web3jBlockchain blockchain = ctx.getBean(Web3jBlockchain.class);
		MessageBrokerAdapter messageBroker = ctx.getBean(KafkaMQ.class);
		
		log.info("Loading events from the blockchain...");
		Events events = blockchain.eventsLog0(startBlock, endBlock);
		List<LogResult> logs = events.getLogs(0);
		log.info("Fetched {} events", logs.size());
		
		log.info("Publishing events...");
		messageBroker.publish(logs);
		log.info("DONE");
		
		ctx.close();
	}
	
}
