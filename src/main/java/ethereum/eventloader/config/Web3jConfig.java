package ethereum.eventloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * web3j property container.
 */
@Configuration
@ConfigurationProperties("ethereum")
public class Web3jConfig {

    private String clientAddress;
    private Boolean adminClient = false;
    private String networkId = "1";
    private Long httpTimeoutSeconds = 360L;
    private Long batchSize = 10L;
    private Long blockLag = 12L;
    private BigInteger startBlock;
    private boolean fullTransactionObject = false;

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public Boolean getAdminClient() {
        return adminClient;
    }

    public void setAdminClient(Boolean adminClient) {
        this.adminClient = adminClient;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public Long getHttpTimeoutSeconds() {
        return httpTimeoutSeconds;
    }

    public void setHttpTimeoutSeconds(Long httpTimeoutSeconds) {
        this.httpTimeoutSeconds = httpTimeoutSeconds;
    }

    public Long getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Long batchSize) {
        this.batchSize = batchSize;
    }

    public BigInteger getStartBlock() {
        return startBlock;
    }

    public void setStartBlock(BigInteger startBlock) {
        this.startBlock = startBlock;
    }

    public Long getBlockLag() {
        return blockLag;
    }

    public void setBlockLag(Long blockLag) {
        this.blockLag = blockLag;
    }

    public boolean isFullTransactionObject() {
        return fullTransactionObject;
    }

    public void setFullTransactionObject(boolean fullTransactionObject) {
        this.fullTransactionObject = fullTransactionObject;
    }
}
