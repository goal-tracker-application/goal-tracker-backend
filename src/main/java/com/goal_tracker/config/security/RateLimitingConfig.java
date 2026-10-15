package com.goal_tracker.config.security;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for rate limiting using Bucket4j and Redis (Lettuce client).
 */
@Configuration
public class RateLimitingConfig {

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