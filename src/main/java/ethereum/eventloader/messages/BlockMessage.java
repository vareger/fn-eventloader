package ethereum.eventloader.messages;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BlockMessage {

    private BigInteger number;
    private String hash;
    private EthBlock.Block block;

    public BlockMessage(BigInteger number, String hash) {
        this(number, hash, null);
    }

    public BlockMessage(BigInteger number, String hash, EthBlock.Block block) {
        this.number = number;
        this.hash = hash;
        this.block = block;
    }

}
