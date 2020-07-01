package ethereum.eventloader.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "zookeeper")
@EnableConfigurationProperties
public class ZookeeperProperties {

    private String namespace;

    private String connectString;

    private Integer connectionTimeout;

    private Integer sessionTimeout;

}
