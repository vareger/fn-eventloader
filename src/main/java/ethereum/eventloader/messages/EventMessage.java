package ethereum.eventloader.messages;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.web3j.protocol.core.methods.response.EthLog;

import java.math.BigInteger;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class EventMessage {

    private List<String> topics;
    private String transactionHash;
    private String contractAddress;
    private BigInteger blockNumber;
    private String data;
    private BigInteger index;

    public EventMessage(List<String> topics, String transactionHash, String contractAddress, BigInteger blockNumber, String data, BigInteger index) {
        this.topics = topics;
        this.transactionHash = transactionHash;
        this.contractAddress = contractAddress;
        this.blockNumber = blockNumber;
        this.data = data;
        this.index = index;
    }

    public EventMessage(EthLog.LogObject logObject) {
        this(
                logObject.getTopics(),
                logObject.getTransactionHash(),
                logObject.getAddress(),
                logObject.getBlockNumber(),
                logObject.getData(),
                logObject.getLogIndex()
        );
    }
}
