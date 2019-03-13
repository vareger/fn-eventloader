package ethereum.eventloader;

import java.util.List;

import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

/**
 * Basic interface for publishing transaction logs and blocks to Message Broker
 */
public interface MessageBrokerAdapter {

	/**
	 * Publish Ethereum transaction's logs
	 *
	 * @param logs List of logs loaded from transaction
	 */
	@SuppressWarnings("rawtypes")
	void publish(List<LogResult> logs);

	/**
	 * Publish Ethereum block
	 *
	 * @param block Ethereum block response
	 */
	void publishBlock(EthBlock.Block block);

	/**
	 * Try to reconnect to Message Broker
	 */
	void reconnect();

}
