package com.yu.aicodeGeneration.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 智能路由模型配置
 *
 * 学习重点：
 * 路由模型只负责把用户需求分类成 CodeGenTypeEnum，不负责生成代码。
 * 因此它通常可以用更快、更便宜、maxTokens 更小的模型。
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@Data
public class RoutingAiModelConfig {

    // OpenAI 兼容接口地址，例如 DashScope compatible-mode。
    private String baseUrl;

    // 路由模型 API Key。
    private String apiKey;

    // 路由模型名称，例如 qwen-turbo。
    private String modelName;

    // 路由只返回一个枚举，maxTokens 不需要很大。
    private Integer maxTokens;

    // 分类任务通常希望稳定，temperature 可以设置较低。
    private Double temperature;

    // 是否打印请求。
    private Boolean logRequests = false;

    // 是否打印响应。
    private Boolean logResponses = false;

    /**
     * 创建用于路由判断的ChatModel
     */
    @Bean
    @Scope("prototype")
    public ChatModel routingChatModelPrototype() {
        // 使用非流式 ChatModel，因为路由只需要一次性返回分类结果。
        // prototype 避免并发调用共享同一模型实例状态。
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
