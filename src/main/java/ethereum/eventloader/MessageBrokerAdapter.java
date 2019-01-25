package ethereum.eventloader;

import java.util.List;

import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

public interface MessageBrokerAdapter {

	@SuppressWarnings("rawtypes")
	void publish(List<LogResult> logs);

	void publishBlock(EthBlock.Block block);

	void reconnect();

}
