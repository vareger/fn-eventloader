package ethereum.eventloader.messages;

import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;

public class BlockMessage {

    private BigInteger number;
    private String hash;
    private EthBlock.Block block;

    public BlockMessage() {
    }

    public BlockMessage(BigInteger number, String hash) {
        this.number = number;
        this.hash = hash;
        this.block = null;
    }

    public BlockMessage(BigInteger number, String hash, EthBlock.Block block) {
        this.number = number;
        this.hash = hash;
        this.block = block;
    }

    public BigInteger getNumber() {
        return number;
    }

    public void setNumber(BigInteger number) {
        this.number = number;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public String toString() {
        return "BlockMessage{" +
                "number=" + number +
                ", hash='" + hash + '\'' +
                '}';
    }
}
