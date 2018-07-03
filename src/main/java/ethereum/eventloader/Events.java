package ethereum.eventloader;

import java.util.ArrayList;
import java.util.List;

import org.web3j.protocol.core.methods.response.EthLog.LogResult;

@SuppressWarnings("rawtypes")
public class Events {
	private final int startBlock;
	private final int endBlock;
	private final List<LogResult> logs;
	
	public Events(int startBlock, int endBlock) {
		this.startBlock = startBlock;
		this.endBlock = endBlock;
		this.logs = new ArrayList<>();
	}

	public int getStartBlock() {
		return startBlock;
	}

	public int getEndBlock() {
		return endBlock;
	}

	public List<LogResult> getLogs() {
		return logs;
	}
}
