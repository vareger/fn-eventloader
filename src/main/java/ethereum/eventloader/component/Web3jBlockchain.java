package ethereum.eventloader.component;

import ethereum.eventloader.BlockchainException;
import ethereum.eventloader.component.beans.Web3jBeans;
import ethereum.eventloader.component.entity.Events;
import ethereum.eventloader.config.Web3jConfig;
import ethereum.eventloader.metrics.BlockchainMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSyncing;
import org.web3j.protocol.core.methods.response.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Web3J implementation for Ethereum Blockchain
 *
 * @see BlockchainAdapter
 */
@Slf4j
@Component
public class Web3jBlockchain implements BlockchainAdapter {

    private Web3j web3j;

    private final Web3jBeans beans;

    private final Web3jConfig config;

    private final BlockchainMetrics metrics;

    private final BatchTransactionManager batchTxManager;

    @Autowired
    public Web3jBlockchain(Web3jBeans beans, Web3jConfig config,
                           BlockchainMetrics metrics,
                           BatchTransactionManager batchTxManager) {
        this.beans = beans;
        this.web3j = beans.web3j();
        this.config = config;
        this.metrics = metrics;
        this.batchTxManager = batchTxManager;
    }

    @Override
    public long latestBlockNumber() {
        try {
            log.info("[BLOCKCHAIN] querying latest block number...");
            long latestBlock = web3j.ethBlockNumber().send().getBlockNumber().intValue();

            // workaround for case when eth.syncing shows currentBlock but eth.blockNumber is zero
            if (latestBlock == 0) {
                Object resp = web3j.ethSyncing().send().getResult(); // EthSyncing
                if (resp instanceof EthSyncing.Syncing) {
                    EthSyncing.Syncing syncing = (EthSyncing.Syncing) resp;
                    String currentBlockHex = syncing.getCurrentBlock();
                    int currentBlock = Integer.parseInt(currentBlockHex.substring(2), 16);
                    latestBlock = currentBlock;
                    log.info("[BLOCKCHAIN] using current block from eth.syncing: {}", currentBlock);
                } else {
                    log.info("[BLOCKCHAIN] not syncing. Result: " + resp);
                }
            }

            if (config.getBlockLag() > 0 && latestBlock > config.getBlockLag()) {
                latestBlock = latestBlock - config.getBlockLag();
            }

            log.info("[BLOCKCHAIN] latest block number: {}", latestBlock);
            this.metrics.setBlockNumber(latestBlock);
            return latestBlock;
        } catch (IOException | WebsocketNotConnectedException ex) {
            this.web3j = beans.web3j();
            throw new BlockchainException(ex);
        }
    }

    @Override
    public Events eventsLog(long latestProcessed, long latestBlock) {
        long startBlock = latestProcessed + 1;
        long endBlock = latestBlock;

        if (endBlock - startBlock >= config.getBatchSize()) {
            endBlock = startBlock + config.getBatchSize() - 1;
        }

        return eventsLog0(startBlock, endBlock);
    }

    @Override
    public List<EthBlock.Block> loadBlocks(long startBlock, long endBlock) {
        final List<Request<?, EthBlock>> requests = new ArrayList<>();
        for (long block = startBlock; block < endBlock; block++) {
            final DefaultBlockParameter defaultBlockParameter = new DefaultBlockParameterNumber(block);
            final Request<?, EthBlock> request = web3j.ethGetBlockByNumber(defaultBlockParameter, config.isFullTransactionObject());
            requests.add(request);
        }
        return this.batchTxManager.sendBatch(requests);
    }

    public Events eventsLog0(long startBlock, long endBlock) {
        Events events = new Events(startBlock, endBlock);
        log.info("[BLOCKCHAIN] querying logs in blocks range [{}..{}]", startBlock, endBlock);

        int foundLogsCount = 0;

        try {
            log.info("[BLOCKCHAIN] querying logs in blocks (from: {}, to: {})", startBlock, endBlock);
            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(startBlock)),
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(endBlock)),
                    (List<String>) null);
            EthLog ethLog = web3j.ethGetLogs(filter).send();
            foundLogsCount = ethLog.getLogs().size();
            if (foundLogsCount > 0) {
                log.info("[BLOCKCHAIN] found {} events", foundLogsCount);
                for (EthLog.LogResult<Log> logResult : ethLog.getLogs()) {
                    events.addLogs(logResult.get().getBlockNumber().longValue(), ethLog.getLogs());
                }
            } else {
                log.warn("[BLOCKCHAIN] no events found in blocks (from: {}, to: {})", startBlock, endBlock);
            }
        } catch (IOException | WebsocketNotConnectedException ex) {
            this.web3j = beans.web3j();
            throw new BlockchainException(ex);
        }

        log.info("[BLOCKCHAIN] total events found: {}", foundLogsCount);
        return events;
    }

    @Override
    public EthSyncing syncing() {
        try {
            EthSyncing syncing = web3j.ethSyncing().send();
            this.metrics.setSyncStatus(syncing.isSyncing());
            return syncing;
        } catch (IOException | WebsocketNotConnectedException ex) {
            this.web3j = beans.web3j();
            throw new BlockchainException(ex);
        }
    }
}
