package com.yu.aicodeGeneration.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集规划服务工厂
 * 学习提示：工厂把 ChatModel 和接口绑定起来，让 ImageCollectionPlanService 变成可注入的 Spring Bean。
 */
@Configuration
public class ImageCollectionPlanServiceFactory {

    // 使用普通 OpenAI 对话模型完成规划任务；规划输出需要稳定结构化结果。
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Bean
    public ImageCollectionPlanService createImageCollectionPlanService() {
        // AiServices 会读取接口上的 @SystemMessage/@UserMessage，并生成运行时代理对象。
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }
}
