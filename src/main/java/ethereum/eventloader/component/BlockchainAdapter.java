package ethereum.eventloader.component;

import ethereum.eventloader.component.entity.Events;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSyncing;

import java.util.List;

public interface BlockchainAdapter {

	/**
	 * Get latest block number on blockchain node
	 *
	 * @return Number of the latest block
	 */
	long latestBlockNumber();

	/**
	 * Load events in transactions between blocks in range [startBlock -> endBlock)
	 *
	 * @param startBlock Start block number
	 * @param endBlock End block number
	 * @return {@link Events} contains loaded events
	 */
	Events eventsLog(long startBlock, long endBlock);

	/**
	 * Load blocks in range [startBlock -> endBlock), must be startBlock > endBlock
	 * @param startBlock Start block number
	 * @param endBlock End block number
	 * @return List of blocks {@link EthBlock.Block}
	 */
	List<EthBlock.Block> loadBlocks(long startBlock, long endBlock);

	/**
	 * Get syncing state of the Ethereum Blockchain Node
	 *
	 * @return Sync response {@link EthSyncing}
	 */
	EthSyncing syncing();

}
