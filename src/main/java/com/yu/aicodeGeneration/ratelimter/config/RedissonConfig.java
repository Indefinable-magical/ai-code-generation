package com.yu.aicodeGeneration.ratelimter.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    // Redis 主机地址，从 application 配置中读取，和 Spring Data Redis 共用一套连接信息。
    @Value("${spring.data.redis.host}")
    private String redisHost;

    // Redis 端口。
    @Value("${spring.data.redis.port}")
    private Integer redisPort;

    // Redis 密码；本地开发环境可能为空，生产环境通常会配置。
    @Value("${spring.data.redis.password}")
    private String redisPassword;

    // Redis 数据库编号，限流 key 会写入该库。
    @Value("${spring.data.redis.database}")
    private Integer redisDatabase;

    @Bean
    public RedissonClient redissonClient() {
        // Redisson 是更偏分布式能力的 Redis 客户端，这里主要用于 RRateLimiter 实现分布式限流。
        Config config = new Config();
        // Redisson 单机模式地址必须带 redis:// 协议前缀。
        String address = "redis://" + redisHost + ":" + redisPort;
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase)
                // 下面是连接池和超时配置：既保证轻量，又避免 Redis 抖动时请求无限等待。
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(10)
                .setIdleConnectionTimeout(30000)
                .setConnectTimeout(5000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        // 如果有密码则设置密码；为空时不调用 setPassword，兼容无密码的本地 Redis。
        if (redisPassword != null && !redisPassword.isEmpty()) {
            singleServerConfig.setPassword(redisPassword);
        }
        // 创建并交给 Spring 容器管理，其他组件可直接注入 RedissonClient。
        return Redisson.create(config);
    }
}
