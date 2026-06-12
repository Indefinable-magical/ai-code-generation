package com.yu.aicodeGeneration.config;

import com.yu.aicodeGeneration.monitor.AiModelMonitorListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * 推理流式模型配置。
 *
 * 学习重点：
 * 这套模型用于更复杂的 Vue 工程生成，因为 Vue 工程需要模型规划目录、调用工具、修改文件。
 * 配置来源是 application.yml 中的 langchain4j.open-ai.reasoning-streaming-chat-model。
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    // 模型调用监听器：统一采集请求、响应、错误和耗时指标。
    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    // OpenAI 兼容 API 地址。
    private String baseUrl;

    // API 密钥。
    private String apiKey;

    // 推理模型名称，例如 deepseek-reasoner。
    private String modelName;

    // 推理模型通常输出更长，所以 maxTokens 可以比普通模型大。
    private Integer maxTokens;

    // 推理任务更需要稳定性，通常 temperature 会设置得更低。
    private Double temperature;

    // 是否记录模型请求。
    private Boolean logRequests = false;

    // 是否记录模型响应。
    private Boolean logResponses = false;

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用）
     */
    @Bean
    // 将该 Bean 设置为原型作用域：每次从 Spring 容器获取时都会创建一个新的实例
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        // prototype 作用域保证每次获取的是新的流式模型实例，减少并发状态串扰。
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                // 监听器让推理模型也纳入统一监控。
                .listeners(List.of(aiModelMonitorListener))
                .build();
    }
}
