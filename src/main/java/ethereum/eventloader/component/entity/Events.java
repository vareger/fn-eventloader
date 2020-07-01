package ethereum.eventloader.component.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

@Slf4j
@SuppressWarnings("rawtypes")
public class Events {
	
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
			log.info("[EVENTS] skipped {} events", skipped);
		}
		return all;
	}
}
