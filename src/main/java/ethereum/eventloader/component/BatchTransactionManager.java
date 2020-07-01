package ethereum.eventloader.component;

import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.util.List;

public interface BatchTransactionManager {

    List<EthBlock.Block> sendBatch(List<Request<?, EthBlock>> requests);

}
