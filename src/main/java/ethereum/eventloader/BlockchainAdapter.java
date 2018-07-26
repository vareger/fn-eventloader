package ethereum.eventloader;

import org.web3j.protocol.core.methods.response.EthSyncing;

public interface BlockchainAdapter {

	int latestBlockNumber();

	Events eventsLog(int latestProcessed, int latestBlock);

	void reconnect();
	
	EthSyncing syncing();

}
