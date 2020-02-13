package ethereum.eventloader.impl;

import ethereum.eventloader.BlockchainAdapter;
import ethereum.eventloader.BlockchainException;
import ethereum.eventloader.Events;
import ethereum.eventloader.beans.Web3jBeans;
import ethereum.eventloader.config.Web3jConfig;
import ethereum.eventloader.metrics.BlockchainMetrics;
import io.reactivex.Flowable;
import io.reactivex.parallel.ParallelFlowable;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSyncing;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * Web3J implementation for Ethereum Blockchain
 *
 * @see ethereum.eventloader.BlockchainAdapter
 */
@Component
public class Web3jBlockchain implements BlockchainAdapter {
    private static final Logger log = LoggerFactory.getLogger(Web3jBlockchain.class);

    private final Web3jBeans beans;
    private final Web3jConfig config;
    private final BlockchainMetrics metrics;
    private Web3j w3;

    @Autowired
    public Web3jBlockchain(Web3jBeans beans, Web3jConfig config, BlockchainMetrics metrics) {
        this.beans = beans;
        this.w3 = beans.web3j();
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public long latestBlockNumber() {
        try {
            log.info("Querying latest block number...");
            long latestBlock = w3.ethBlockNumber().send().getBlockNumber().intValue();

            //workaround for case when eth.syncing shows currentBlock but eth.blockNumber is zero
            if (latestBlock == 0) {
                Object resp = w3.ethSyncing().send().getResult(); //EthSyncing
                if (resp instanceof EthSyncing.Syncing) {
                    EthSyncing.Syncing syncing = (EthSyncing.Syncing) resp;
                    String currentBlockHex = syncing.getCurrentBlock();
                    int currentBlock = Integer.parseInt(currentBlockHex.substring(2), 16);
                    latestBlock = currentBlock;
                    log.info("Using current block from eth.syncing: {}", currentBlock);
                } else {
                    log.info("Not syncing. Result: " + resp);
                }
            }

            if (config.getBlockLag() > 0 && latestBlock > config.getBlockLag()) {
                latestBlock = latestBlock - config.getBlockLag();
            }

            log.info("Latest block number: {}", latestBlock);
            this.metrics.setBlockNumber(latestBlock);
            return latestBlock;
        } catch (IOException | WebsocketNotConnectedException e) {
            this.w3 = beans.web3j();
            throw new BlockchainException(e);
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
    public ParallelFlowable<EthBlock.Block> loadBlocks(long startBlock, long endBlock) {
        return ParallelFlowable.from(
                Flowable.rangeLong(startBlock, endBlock - startBlock)
                        .map(DefaultBlockParameterNumber::new)
                        .map(block -> w3.ethGetBlockByNumber(block, config.isFullTransactionObject()).send())
                        .map(EthBlock::getBlock)
        );
    }

    public Events eventsLog0(long startBlock, long endBlock) {
        Events events = new Events(startBlock, endBlock);
        log.info("Querying logs in blocks range [{}..{}]", startBlock, endBlock);

        int cnt = 0;

        for (long block = startBlock; block <= endBlock; block++) {
            try {
                log.info("Querying logs in block: {}", block);
                EthFilter filter = new EthFilter(
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(block)),
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(block)),
                        (List<String>) null);
                EthLog ethLog = w3.ethGetLogs(filter).send();
                if (ethLog.getLogs().size() > 0) {
                    log.info("Found {} events", ethLog.getLogs().size());
                } else {
                    log.warn("No events found in block: {}", block);
                }
                cnt += ethLog.getLogs().size();
                events.addLogs(block, ethLog.getLogs());
            } catch (IOException | WebsocketNotConnectedException e) {
                this.w3 = beans.web3j();
                throw new BlockchainException(e);
            }
        }

        log.info("Total events found: {}", cnt);
        return events;
    }

    @Override
    public EthSyncing syncing() {
        try {
            EthSyncing syncing = w3.ethSyncing().send();
            this.metrics.setSyncStatus(syncing.isSyncing());
            return syncing;
        } catch (IOException | WebsocketNotConnectedException e) {
            this.w3 = beans.web3j();
            throw new BlockchainException(e);
        }
    }
}
