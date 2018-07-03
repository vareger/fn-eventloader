package ethereum.eventloader;

public interface BlockchainAdapter {

	int latestBlockNumber();

	Events eventsLog(int latestProcessed, int latestBlock);

}
