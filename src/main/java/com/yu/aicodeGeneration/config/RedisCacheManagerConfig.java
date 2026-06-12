package com.yu.aicodeGeneration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存管理器配置
 *
 * 学习重点：
 * 这个配置服务于 Spring Cache，例如 AppController 中的 @Cacheable 精选应用列表。
 * 它和 RedisChatMemoryStore 不同：前者缓存业务查询结果，后者保存 AI 对话记忆。
 */
@Configuration
public class RedisCacheManagerConfig {

    // Spring Data Redis 的连接工厂，由 Spring Boot 根据 redis 配置自动创建。
    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public CacheManager cacheManager() {
        // 配置 ObjectMapper 支持 Java 8 时间类型，如 LocalDateTime。
        // 当前 value JSON 序列化代码被注释了，但保留 objectMapper 方便后续开启。
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 默认缓存配置。
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 默认 30 分钟过期，避免缓存长期占用内存。
                .entryTtl(Duration.ofMinutes(30))
                // 禁用 null 值缓存，避免一次空结果把后续真实数据挡住。
                .disableCachingNullValues()
                // key 使用 String 序列化器，Redis 里更容易读懂。
                // 指定 Redis Key 的序列化方式为字符串序列化，确保 Key 以可读字符串形式存储
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));
//                // value 使用 JSON 序列化器（支持复杂对象）但是要注意开启后需要给序列化增加默认类型配置，否则无法反序列化
//                .serializeValuesWith(RedisSerializationContext.SerializationPair
//                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        // 构建 RedisCacheManager，并对特定缓存名设置特殊过期时间。
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                // 针对 good_app_page 配置 5 分钟过期：首页精选列表变化后能较快刷新。
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}
