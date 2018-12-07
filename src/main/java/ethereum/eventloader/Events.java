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
	
	private final long startBlock;
	private final long endBlock;
	private final Map<Long, List<LogResult>> logs;
	
	public Events(long startBlock, long endBlock) {
		this.startBlock = startBlock;
		this.endBlock = endBlock;
		this.logs = new LinkedHashMap<>();
	}

    public long getStartBlock() {
		return startBlock;
	}

	public long getEndBlock() {
		return endBlock;
	}
	
	public void addLogs(long block, List<LogResult> list) {
		logs.put(block, list);
	}
	
	public List<LogResult> getLogs(long afterBlock) {
		int skipped = 0;
		List<LogResult> all = new ArrayList<>();
		for (Entry<Long, List<LogResult>> e: logs.entrySet()) {
			List<LogResult> events = e.getValue();
			Long block = e.getKey();
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
