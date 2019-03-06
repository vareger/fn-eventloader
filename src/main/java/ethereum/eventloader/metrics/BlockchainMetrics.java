package ethereum.eventloader.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlockchainMetrics {

    private static final String BLOCK_NUMBER = "block_number";
    private static final String BLOCK_NUMBER_TAG = "tag";
    private static final String TYPE = "service";
    private static final String SYNC_STATUS = "state";
    private static final String SYNC = "node_sync";

    private Long blockNumber;
    private boolean inSync;

    @Autowired
    public BlockchainMetrics(MeterRegistry registry) {
        Gauge.builder(BLOCK_NUMBER, this::getBlockNumber).tag(BLOCK_NUMBER_TAG, "current").tag(TYPE, "blockchain").register(registry);
        Gauge.builder(SYNC, this::isSyncTrue).tag(SYNC_STATUS, "in_sync").register(registry);
        Gauge.builder(SYNC, this::isSyncFalse).tag(SYNC_STATUS, "synced").register(registry);
    }

    public void setBlockNumber(Long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public void setSyncStatus(boolean isSync) {
        this.inSync = isSync;
    }

    private Long getBlockNumber() {
        return this.blockNumber;
    }

    private Integer isSyncTrue() {
        return inSync ? 1 : 0;
    }

    private Integer isSyncFalse() {
        return !inSync ? 1 : 0;
    }

}
