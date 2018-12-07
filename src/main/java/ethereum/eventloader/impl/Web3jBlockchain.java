package ethereum.eventloader.impl;

import ethereum.eventloader.BlockchainAdapter;
import ethereum.eventloader.BlockchainException;
import ethereum.eventloader.Events;
import ethereum.eventloader.config.Web3jConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSyncing;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

@Component
public class Web3jBlockchain implements BlockchainAdapter {
    private static final Logger log = LoggerFactory.getLogger(Web3jBlockchain.class);

    private final Web3j w3;
    private final Web3jConfig config;

    @Autowired
    public Web3jBlockchain(Web3j w3, Web3jConfig config) {
        this.w3 = w3;
        this.config = config;
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
            return latestBlock;
        } catch (IOException e) {
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
            } catch (IOException e) {
                throw new BlockchainException(e);
            }
        }

        log.info("Total events found: {}", cnt);
        return events;
    }

    @Override
    public EthSyncing syncing() {
        try {
            return w3.ethSyncing().send();
        } catch (IOException e) {
            throw new BlockchainException(e);
        }
    }
}
