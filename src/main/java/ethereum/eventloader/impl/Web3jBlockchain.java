package ethereum.eventloader.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.http.HttpService;

import ethereum.eventloader.BlockchainAdapter;
import ethereum.eventloader.BlockchainException;
import ethereum.eventloader.Events;

@Component
public class Web3jBlockchain implements BlockchainAdapter {
	private static final Logger log = LoggerFactory.getLogger(Web3jBlockchain.class);
	
	private Web3j w3;
	
	@Value("${eventloader.eth.node_url}") 
	private String nodeUrl;
	
	/** We may not want to operate on latest block because 
	 * it can be displaced from the main chain by uncle. */
	@Value("${eventloader.eth.latest_block_lag:10}") private int latestBlockLag;
	
	/** We don't load unlimited number of blocks at once 
	 * to avoid high memory usage on server and client */
	@Value("${eventloader.eth.block_batch_size:5}") private int blockBatchSize;
	
	@Override
	public int latestBlockNumber() {
		try {
			log.info("Querying latest block number...");
			int latestBlock = w3.ethBlockNumber().send().getBlockNumber().intValue();
			if (latestBlockLag > 0) {
				latestBlock = latestBlock - latestBlockLag;
			}
			log.info("Latest block number: {}", latestBlock);
			return latestBlock;
		} catch (IOException e) {
			throw new BlockchainException(e);
		}
	}

	@Override
	public Events eventsLog(int latestProcessed, int latestBlock) {
		int startBlock = latestProcessed + 1;
		int endBlock = latestBlock;
		
		if (endBlock - startBlock >= blockBatchSize) {
			endBlock = startBlock + blockBatchSize - 1;
		}
		
		Events events = new Events(startBlock, endBlock);
		log.info("Querying logs in blocks range [{}..{}]", startBlock, endBlock);
		
		//TODO: are gaps in block-sequence possible? if yes, we should handle that
		for (int block = startBlock; block <= endBlock; block++) {
			try {
				log.info("Querying logs in block: {}", block);
				EthFilter filter = new EthFilter(
						DefaultBlockParameter.valueOf(BigInteger.valueOf(block)),
						DefaultBlockParameter.valueOf(BigInteger.valueOf(block)), 
						(List<String>) null);
				EthLog ethLog = w3.ethGetLogs(filter).send();
				log.info("Found {} events", ethLog.getLogs().size());
				events.getLogs().addAll(ethLog.getLogs());
			} catch (IOException e) {
				throw new BlockchainException(e);
			}
		}
		
		log.info("Total events found: {}", events.getLogs().size());
		return events;
	}

	@PostConstruct
	public void start() {
		w3 = Web3j.build(new HttpService(nodeUrl));
	}
	
	@PreDestroy
	public void stop() {
		w3.shutdown();
	}
}
