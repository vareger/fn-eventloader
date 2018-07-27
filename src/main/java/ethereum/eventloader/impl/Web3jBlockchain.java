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
import org.web3j.protocol.core.methods.response.EthSyncing;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.ipc.UnixIpcService;

import ethereum.eventloader.BlockchainAdapter;
import ethereum.eventloader.BlockchainException;
import ethereum.eventloader.Events;

@Component
public class Web3jBlockchain implements BlockchainAdapter {
	private static final Logger log = LoggerFactory.getLogger(Web3jBlockchain.class);
	
	private Web3j w3;
	
	@Value("${eventloader.eth.node_url}") 
	private String nodeUrl;
	
	@Value("${eventloader.eth.use_unix_ipc:false}") 
	private boolean useUnixIpc;
	
	@Value("${eventloader.eth.ipc_socket_path:#{null}}") 
	private String ipcSocketPath;
	
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
			
			//workaround for case when eth.syncing shows currentBlock but eth.blockNumber is zero
			if (latestBlock == 0) {
				Object resp = w3.ethSyncing().send().getResult(); //EthSyncing
				if (resp instanceof EthSyncing.Syncing) {
					EthSyncing.Syncing syncing = (EthSyncing.Syncing) resp;
					String currentBlockHex = syncing.getCurrentBlock();
					int currentBlock = Integer.parseInt(currentBlockHex.substring(2), 16);
					latestBlock = currentBlock;
					log.info("Using current block from eth.syncing: {}", currentBlock);
				} else {
					log.info("Not syncing. Result: " + resp);
				}
			}
			
			if (latestBlockLag > 0 && latestBlock > latestBlockLag) {
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
		
		return eventsLog0(startBlock, endBlock);
	}

	public Events eventsLog0(int startBlock, int endBlock) {
		Events events = new Events(startBlock, endBlock);
		log.info("Querying logs in blocks range [{}..{}]", startBlock, endBlock);
		
		int cnt = 0;
		
		for (int block = startBlock; block <= endBlock; block++) {
			try {
				log.info("Querying logs in block: {}", block);
				EthFilter filter = new EthFilter(
						DefaultBlockParameter.valueOf(BigInteger.valueOf(block)),
						DefaultBlockParameter.valueOf(BigInteger.valueOf(block)), 
						(List<String>) null);
				EthLog ethLog = w3.ethGetLogs(filter).send();
				if (ethLog.getLogs().size() > 0) {
					log.info("Found {} events", ethLog.getLogs().size());
				} else {
					log.warn("No events found in block: {}", block);
				}
				cnt += ethLog.getLogs().size();
				events.addLogs(block, ethLog.getLogs());
			} catch (IOException e) {
				throw new BlockchainException(e);
			}
		}
		
		log.info("Total events found: {}", cnt);
		return events;
	}

	private String nodeInfo() {
		try {
			return w3.web3ClientVersion().send().getResult();
		} catch (IOException e) {
			throw new BlockchainException(e);
		}
	}
	
	@PostConstruct
	public void start() {
		if (useUnixIpc) {
			log.info("Connecting via Unix/IPC...");
			w3 = Web3j.build(new UnixIpcService(ipcSocketPath));
		} else {
			log.info("Connecting via HTTP...");
			w3 = Web3j.build(new HttpService(nodeUrl));
		}
		log.info("Connected to: {}", nodeInfo());
	}
	
	@PreDestroy
	public void stop() {
		w3.shutdown();
	}
	
	@Override
	public void reconnect() {
		try {
			log.info("Stopping...");
			stop();
			log.info("Stopped");
		} catch (Exception e) {
			log.info("Stop failed");
		}
		
		log.info("Starting...");
		start();
		log.info("Started");
	}

	@Override
	public EthSyncing syncing() {
		try {
			return w3.ethSyncing().send();
		} catch (IOException e) {
			throw new BlockchainException(e);
		}
	}
}
