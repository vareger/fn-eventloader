package ethereum.eventloader.messages;

import org.web3j.protocol.core.methods.response.EthLog;

import java.math.BigInteger;
import java.util.List;

public class EventMessage {

    private List<String> topics;
    private String transactionHash;
    private String contractAddress;
    private BigInteger blockNumber;
    private String data;

    public EventMessage() {
    }

    public EventMessage(List<String> topics, String transactionHash, String contractAddress, BigInteger blockNumber, String data) {
        this.topics = topics;
        this.transactionHash = transactionHash;
        this.contractAddress = contractAddress;
        this.blockNumber = blockNumber;
        this.data = data;
    }

    public EventMessage(EthLog.LogObject logObject) {
        this(
                logObject.getTopics(),
                logObject.getTransactionHash(),
                logObject.getAddress(),
                logObject.getBlockNumber(),
                logObject.getData()
        );
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(BigInteger blockNumber) {
        this.blockNumber = blockNumber;
    }

}
