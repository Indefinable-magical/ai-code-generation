package com.yu.aicodeGeneration.ai;

import com.yu.aicodeGeneration.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由服务工厂
 *
 * 学习重点：
 * 1. 路由模型只负责“分类”，所以使用更轻量的 routingChatModelPrototype。
 * 2. 每次 create 都从 Spring 容器拿 prototype Bean，避免并发场景复用同一个模型实例。
 * 3. 生成结果会落到 App.codeGenType，后续聊天不会重复路由。
 *
 * @author yupi
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    /**
     * 创建AI代码生成类型路由服务实例
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        // 从 Spring 容器里取路由专用模型。
        // routingChatModelPrototype 的配置来自 RoutingAiModelConfig，对应 application.yml 中的 routing-chat-model。
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        // AiServices 会根据 AiCodeGenTypeRoutingService 接口上的注解创建代理对象。
        // 调用 routeCodeGenType 时，代理会自动组装系统提示词、用户输入并请求模型。
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        // 提供默认 Bean 方便注入；业务中如果担心并发，优先调用 createAiCodeGenTypeRoutingService 创建新实例。
        return createAiCodeGenTypeRoutingService();
    }
}
