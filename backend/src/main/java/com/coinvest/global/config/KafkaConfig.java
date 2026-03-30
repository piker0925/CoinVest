package com.coinvest.global.config;

import com.coinvest.global.common.KafkaTopicConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정.
 * JSON 직렬화를 지원하며 토픽 자동 생성을 관리함.
 */
@Configuration
public class KafkaConfig {

    private final KafkaProperties properties;

    public KafkaConfig(KafkaProperties properties) {
        this.properties = properties;
    }

    /**
     * Kafka 토픽 정의 (자동 생성).
     */
    @Bean
    public NewTopic priceTickerTopic() {
        return TopicBuilder.name(KafkaTopicConstants.PRICE_TICKER_UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertRebalanceTopic() {
        return TopicBuilder.name(KafkaTopicConstants.ALERT_REBALANCE_TRIGGERED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic portfolioUpdatedTopic() {
        return TopicBuilder.name(KafkaTopicConstants.PORTFOLIO_UPDATED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic evaluatePortfolioTopic() {
        return TopicBuilder.name(KafkaTopicConstants.EVALUATE_PORTFOLIO)
                .partitions(3) // 평가 작업 분산을 위해 파티션 3개 설정
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradeOrderFilledTopic() {
        return TopicBuilder.name(KafkaTopicConstants.TRADE_ORDER_FILLED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Producer 설정.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all"); // 보장 수준 최대로 설정 (리밸런싱 알림 등 유실 방지)
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
