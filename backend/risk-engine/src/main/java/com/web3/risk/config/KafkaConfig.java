package com.web3.risk.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置类
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    /**
     * 消费者工厂配置
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 基础配置
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        
        // 序列化配置
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // 性能优化配置
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 手动提交
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100); // 每次拉取最多100条消息
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024); // 最小拉取1KB数据
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // 最多等待500ms
        
        // 会话配置
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000); // 30秒会话超时
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000); // 10秒心跳间隔
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Kafka监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // 并发配置
        factory.setConcurrency(3); // 3个并发消费者
        
        // 确认模式配置
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // 错误处理配置
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());
        
        return factory;
    }

    /**
     * JSON消费者工厂（用于复杂对象）
     */
    @Bean
    public ConsumerFactory<String, Object> jsonConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-json");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // JSON反序列化配置
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.web3.risk.dto");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.web3.risk.dto.TransactionDto");
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * JSON监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> jsonKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(jsonConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }
}