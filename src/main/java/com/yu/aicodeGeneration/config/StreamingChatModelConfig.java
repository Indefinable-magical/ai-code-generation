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
 * 流式对话模型配置
 *
 * 学习重点：
 * 这个配置类绑定 application.yml 中的 langchain4j.open-ai.streaming-chat-model。
 * 它创建的是普通流式模型，主要用于 HTML 和 MULTI_FILE 两种代码生成。
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

    // AI 模型监听器：用于记录请求次数、错误、token、响应耗时等监控指标。
    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    // OpenAI 兼容接口地址，例如 DeepSeek、通义千问兼容模式等。
    private String baseUrl;

    // 模型 API Key，来自配置文件或环境变量，不能硬编码到代码仓库。
    private String apiKey;

    // 模型名称，例如 deepseek-chat。
    private String modelName;

    // 单次响应最大 token 数，限制模型最长输出。
    private Integer maxTokens;

    // 温度参数，越高越发散，越低越稳定。
    private Double temperature;

    // 是否打印请求日志，开发调试有用，生产要注意敏感信息。
    private boolean logRequests;

    // 是否打印响应日志，代码生成内容可能很长，生产环境要谨慎开启。
    private boolean logResponses;

    /**
     * 流式模型
     */
    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype() {
        // 使用 prototype，每次从 Spring 获取都会创建新对象。
        // 流式请求通常带有回调和状态，prototype 能减少并发请求之间相互影响。
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                // 挂载模型监听器，让每次模型请求都能进入监控链路。
                .listeners(List.of(aiModelMonitorListener))
                .build();
    }
}
