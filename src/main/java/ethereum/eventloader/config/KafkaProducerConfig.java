package ethereum.eventloader.config;

import ethereum.eventloader.messages.EventMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

//    @Value(value = "${kafka.bootstrapAddress}")
//    private String bootstrapAddress;
//
//    @Bean
//    public ProducerFactory<String, EventMessage> eventMessageProducerFactory() {
//        Map<String, Object> configProps = new HashMap<>();
//        configProps.put(
//                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
//                bootstrapAddress);
//        configProps.put(
//                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
//                StringSerializer.class);
//        configProps.put(
//                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
//                JsonSerializer.class);
//        configProps.put(
//                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
//                "10000");
//        configProps.put(
//                ProducerConfig.LINGER_MS_CONFIG,
//                "100");
//        configProps.put(
//                ProducerConfig.ACKS_CONFIG,
//                "all");
//        configProps.put(
//                ProducerConfig.BATCH_SIZE_CONFIG,
//                "100000");
//        configProps.put(
//                ProducerConfig.RETRIES_CONFIG,
//                "5");
//        configProps.put(
//                ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
//                "4713360");
//        return new DefaultKafkaProducerFactory<>(configProps);
//    }
//
//    @Bean
//    public KafkaTemplate<String, String> kafkaTemplateString() {
//        return new KafkaTemplate<>(eventMessageProducerFactoryString());
//    }
//
//    @Bean
//    public ProducerFactory<String, String> eventMessageProducerFactoryString() {
//        Map<String, Object> configProps = new HashMap<>();
//        configProps.put(
//                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
//                bootstrapAddress);
//        configProps.put(
//                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
//                StringSerializer.class);
//        configProps.put(
//                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
//                StringSerializer.class);
//        return new DefaultKafkaProducerFactory<>(configProps);
//    }
//
//    @Bean
//    public KafkaTemplate<String, EventMessage> kafkaTemplate() {
//        return new KafkaTemplate<>(eventMessageProducerFactory());
//    }

//    @Bean
//    public ConcurrentKafkaListenerContainerFactoryConfigurer kafkaListenerContainerFactoryConfigurer(
//            KafkaProperties kafkaProperties,
//            ObjectProvider<RecordMessageConverter> messageConverterObjectProvider,
//            ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplateObjectProvider) {
//
//        RecordMessageConverter messageConverter = messageConverterObjectProvider.getIfUnique();
//        KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplateObjectProvider.getIfUnique();
//
//        return new ConcurrentKafkaListenerContainerFactoryConfigurer() {
//
//            @Override
//            public void configure(ConcurrentKafkaListenerContainerFactory<Object, Object> listenerFactory,
//                                  ConsumerFactory<Object, Object> consumerFactory) {
//
//                listenerFactory.setConsumerFactory(consumerFactory);
//                configureListenerFactory(listenerFactory);
//                configureContainer(listenerFactory.getContainerProperties());
//            }
//
//            private void configureListenerFactory(
//                    ConcurrentKafkaListenerContainerFactory<Object, Object> factory) {
//                PropertyMapper map = PropertyMapper.get();
//                KafkaProperties.Listener properties = kafkaProperties.getListener();
//                map.from(properties::getConcurrency).whenNonNull().to(factory::setConcurrency);
//                map.from(() -> messageConverter).whenNonNull()
//                        .to(factory::setMessageConverter);
//                map.from(() -> kafkaTemplate).whenNonNull().to(factory::setReplyTemplate);
//                map.from(properties::getType).whenEqualTo(KafkaProperties.Listener.Type.BATCH)
//                        .toCall(() -> factory.setBatchListener(true));
//            }
//
//            private void configureContainer(ContainerProperties container) {
//                PropertyMapper map = PropertyMapper.get();
//                KafkaProperties.Listener properties = kafkaProperties.getListener();
//                map.from(properties::getAckMode).whenNonNull().to(container::setAckMode);
//                map.from(properties::getAckCount).whenNonNull().to(container::setAckCount);
//                map.from(properties::getAckTime).whenNonNull().as(Duration::toMillis)
//                        .to(container::setAckTime);
//                map.from(properties::getPollTimeout).whenNonNull().as(Duration::toMillis)
//                        .to(container::setPollTimeout);
//                map.from(properties::getNoPollThreshold).whenNonNull()
//                        .to(container::setNoPollThreshold);
//                map.from(properties::getIdleEventInterval).whenNonNull().as(Duration::toMillis)
//                        .to(container::setIdleEventInterval);
//                map.from(properties::getMonitorInterval).whenNonNull().as(Duration::getSeconds)
//                        .as(Number::intValue).to(container::setMonitorInterval);
//            }
//
//        };
//    }

}
