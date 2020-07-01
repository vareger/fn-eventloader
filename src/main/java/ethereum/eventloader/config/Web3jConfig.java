package ethereum.eventloader.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * web3j property container.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties("ethereum")
public class Web3jConfig {

    private String clientAddress;

    private Boolean adminClient = false;

    private String networkId = "1";

    private Long httpTimeoutSeconds = 360L;

    private Long batchSize;

    private Long blockLag;

    private BigInteger startBlock;

    private boolean fullTransactionObject = false;

}
