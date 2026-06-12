package com.yu.aicodeGeneration.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 持久化对话记忆
 *
 * 学习重点：
 * LangChain4j 的 ChatMemory 负责给模型提供上下文。
 * 这里把记忆存到 Redis，避免只存在 JVM 内存里导致重启后丢失。
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    // Redis 主机地址。
    private String host;

    // Redis 端口。
    private int port;

    // Redis 密码，可为空。
    private String password;

    // 聊天记忆在 Redis 中的过期时间，单位取决于 RedisChatMemoryStore 的实现配置。
    private long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        // 构造 RedisChatMemoryStore，供 MessageWindowChatMemory 持久化消息窗口。
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .password(password)
                .ttl(ttl);
        // 如果配置了密码，则设置默认用户。
        // 这里适配需要 ACL 用户名的 Redis 配置；无密码时不额外设置。
        if (StrUtil.isNotBlank(password)) {
            builder.user("default");
        }
        return builder.build();
    }
}
