package ethereum.eventloader;

import io.reactivex.parallel.ParallelFlowable;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSyncing;

public interface BlockchainAdapter {

	long latestBlockNumber();

	Events eventsLog(long latestProcessed, long latestBlock);

	/**
	 * Returns blocks in range [startBlock -> endBlock), must be startBlock > endBlock
	 * @param startBlock Start block number
	 * @param endBlock End block number
	 * @return List of blocks
	 */
	ParallelFlowable<EthBlock.Block> loadBlocks(long startBlock, long endBlock);

	EthSyncing syncing();

}
