package ethereum.eventloader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

@SuppressWarnings("rawtypes")
public class Events {
	private static final Logger log = LoggerFactory.getLogger(Events.class);
	
	private final int startBlock;
	private final int endBlock;
	private final Map<Integer, List<LogResult>> logs;
	
	public Events(int startBlock, int endBlock) {
		this.startBlock = startBlock;
		this.endBlock = endBlock;
		this.logs = new LinkedHashMap<>();
	}

	public int getStartBlock() {
		return startBlock;
	}

	public int getEndBlock() {
		return endBlock;
	}
	
	public void addLogs(int block, List<LogResult> list) {
		logs.put(block, list);
	}
	
	public List<LogResult> getLogs(int afterBlock) {
		int skipped = 0;
		List<LogResult> all = new ArrayList<>();
		for (Entry<Integer, List<LogResult>> e: logs.entrySet()) {
			List<LogResult> events = e.getValue();
			Integer block = e.getKey();
			if (block > afterBlock) {
				all.addAll(events);
			} else {
				skipped += events.size();
			}
		}
		if (skipped > 0) {
			log.info("Skipped {} events (processed in parallel)", skipped);
		}
		return all;
	}
}
