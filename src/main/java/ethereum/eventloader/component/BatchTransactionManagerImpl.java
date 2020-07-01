package ethereum.eventloader.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchTransactionManagerImpl implements BatchTransactionManager {

    private final Web3j web3j;

    @Override
    public List<EthBlock.Block> sendBatch(List<Request<?, EthBlock>> requests) {
        BatchRequest batchRequest = this.web3j.newBatch();
        requests.forEach(batchRequest::add);
        try {
            return batchRequest.sendAsync()
                    .get()
                    .getResponses()
                    .stream()
                    .map(response -> (EthBlock) response)
                    .map(EthBlock::getBlock)
                    .collect(toList());
        } catch (ExecutionException | InterruptedException ex) {
            log.error("[TRANSACTIONS] exception while sending batch request", ex);
            return emptyList();
        }
    }

}
