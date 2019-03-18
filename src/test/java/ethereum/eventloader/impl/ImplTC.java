package ethereum.eventloader.impl;

import ethereum.eventloader.beans.Web3jBeans;
import ethereum.eventloader.config.Web3jConfig;
import ethereum.eventloader.messages.BlockMessage;
import ethereum.eventloader.messages.EventMessage;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.ClassRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

@TestConfiguration
@EnableKafka
@TestPropertySource("classpath:test.yml")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@ComponentScan(basePackages = {"ethereum.eventloader.impl", "ethereum.eventloader.config"})
public class ImplTC {

    @ClassRule
    public static KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, "test.topic");

    @Autowired
    private Web3jConfig config;

    @Bean
    public ProducerFactory<String, EventMessage> producerEventFactory() {
        return new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(embeddedKafka));
    }

    @Bean
    public KafkaTemplate<String, EventMessage> kafkaEventTemplate() {
        KafkaTemplate<String, EventMessage> kafkaTemplate = new KafkaTemplate<>(producerEventFactory());
        kafkaTemplate.setDefaultTopic("test.topic.events");
        return kafkaTemplate;
    }

    @Bean
    public ProducerFactory<String, BlockMessage> producerBlockFactory() {
        return new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(embeddedKafka));
    }

    @Bean
    public KafkaTemplate<String, BlockMessage> kafkaBlockTemplate() {
        KafkaTemplate<String, BlockMessage> kafkaTemplate = new KafkaTemplate<>(producerBlockFactory());
        kafkaTemplate.setDefaultTopic("test.topic.blocks");
        return kafkaTemplate;
    }

    @Bean
    public CuratorFramework curatorFramework() throws Exception {
        TestingServer zooKeeperServer = new TestingServer(2181);
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(zooKeeperServer.getConnectString(), new RetryOneTime(2000));
        curatorFramework.start();
        return curatorFramework;
    }

    @Bean
    public Web3jBeans web3jBeans() {
        return new Web3jBeans(config);
    }
}