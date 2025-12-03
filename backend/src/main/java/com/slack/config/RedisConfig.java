package com.slack.config;

import com.slack.service.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Pub/Sub messaging
 * 
 * This configuration sets up Redis for inter-server message broadcasting
 * in a distributed architecture.
 */
@Configuration
public class RedisConfig {

    /**
     * Redis template for Pub/Sub operations
     * Uses String serializer for message keys and values
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * Channel topic for WebSocket message broadcasting
     * All servers subscribe to this topic to receive messages from other servers
     */
    @Bean
    public ChannelTopic messageTopic() {
        return new ChannelTopic("slack:websocket:messages");
    }

    /**
     * Redis message listener container
     * Manages Redis Pub/Sub subscriptions
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter,
            ChannelTopic messageTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListenerAdapter, messageTopic);
        return container;
    }

    /**
     * Message listener adapter for Redis Pub/Sub
     * Routes Redis messages to RedisMessageSubscriber
     * MessageListener 인터페이스를 구현한 경우 자동으로 onMessage 메서드를 호출합니다.
     */
    @Bean
    public MessageListenerAdapter messageListenerAdapter(RedisMessageSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber);
        // MessageListener 인터페이스를 구현했으므로 자동으로 onMessage 메서드가 호출됩니다.
        return adapter;
    }
}

