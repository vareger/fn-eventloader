package ethereum.eventloader;

import org.web3j.protocol.core.methods.response.EthSyncing;

public interface BlockchainAdapter {

	long latestBlockNumber();

	Events eventsLog(long latestProcessed, long latestBlock);

	EthSyncing syncing();

}
