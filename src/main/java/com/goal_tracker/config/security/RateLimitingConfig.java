package com.goal_tracker.config.security;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for rate limiting using Bucket4j and Redis (Lettuce client).
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Creates and configures the Lettuce RedisClient bean using the host and port
     * from application properties (dynamically overridden by Testcontainers during tests).
     *
     * @param host Redis server host
     * @param port Redis server port
     * @return the configured RedisClient instance
     */
    @Bean
    public RedisClient redisClient(@Value("${spring.data.redis.host:localhost}") String host,
                                   @Value("${spring.data.redis.port:6379}") int port) {
        return RedisClient.create("redis://" + host + ":" + port);
    }
    
    /**
     * Creates a ProxyManager bean for managing distributed token buckets in Redis.
     * 
     * @param redisClient the Lettuce Redis client autoconfigured by Spring Boot
     * @return a ProxyManager using String keys backed by Redis
     */
    @Bean
    public ProxyManager<String> lettuceProxyManager(RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        );
        return Bucket4jLettuce.casBasedBuilder(connection)
                .build();
    }
}