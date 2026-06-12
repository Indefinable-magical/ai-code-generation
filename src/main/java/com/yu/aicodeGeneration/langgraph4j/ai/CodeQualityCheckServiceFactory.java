package com.yu.aicodeGeneration.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 代码质量检查服务工厂
 * 学习提示：这里创建的是“代码质检 AI 代理”，给 CodeQualityCheckNode 注入使用。
 */
@Slf4j
@Configuration
public class CodeQualityCheckServiceFactory {

    // 用于质检的普通对话模型。
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    /**
     * 创建代码质量检查 AI 服务
     */
    @Bean
    public CodeQualityCheckService createCodeQualityCheckService() {
        // 接口 + 注解 + ChatModel 组合成一个可调用的 AI 服务。
        return AiServices.builder(CodeQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }
}
