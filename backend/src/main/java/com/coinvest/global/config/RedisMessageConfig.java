package com.coinvest.global.config;

import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.service.PriceEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisMessageConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PriceEventHandler priceEventHandler) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(priceEventHandler, new ChannelTopic(RedisKeyConstants.PRICE_TICKER_CHANNEL));
        return container;
    }
}
