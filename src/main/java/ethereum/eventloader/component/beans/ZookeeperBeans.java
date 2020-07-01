package ethereum.eventloader.component.beans;

import ethereum.eventloader.config.ZookeeperProperties;
import lombok.RequiredArgsConstructor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ZookeeperBeans {

    private final ZookeeperProperties properties;

    @Bean
    public CuratorFramework initCuratorFramework() {
        CuratorFramework framework = CuratorFrameworkFactory.builder()
                .connectString(properties.getConnectString())
                .sessionTimeoutMs(properties.getSessionTimeout())
                .connectionTimeoutMs(properties.getConnectionTimeout())
                .namespace(properties.getNamespace())
                .retryPolicy(new ExponentialBackoffRetry(1000, 5))
                .build();
        framework.start();
        return framework;
    }

}
