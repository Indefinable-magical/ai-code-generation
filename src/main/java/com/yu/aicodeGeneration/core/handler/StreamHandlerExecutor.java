package com.yu.aicodeGeneration.core.handler;

import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import com.yu.aicodeGeneration.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流处理器执行器
 * 根据代码生成类型创建合适的流处理器：
 * 1. 传统的 Flux<String> 流（HTML、MULTI_FILE） -> SimpleTextStreamHandler
 * 2. TokenStream 格式的复杂流（VUE_PROJECT） -> JsonMessageStreamHandler
 *
 * 学习重点：
 * AI 生成出来的“流”有两种形态：
 * - HTML / MULTI_FILE：就是普通文本片段，直接拼起来保存成一条 AI 消息。
 * - VUE_PROJECT：是 JSON 消息流，里面混有 AI 文本、工具请求、工具执行结果，需要专门解析。
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    // JsonMessageStreamHandler 依赖 ToolManager，因此交给 Spring 注入。
    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    /**
     * 创建流处理器并处理聊天历史记录
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @param codeGenType        代码生成类型
     * @return 处理后的流
     */
    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        // 根据生成类型选择流处理策略。
        // 这里使用 switch 表达式，让每一种类型对应的处理器非常直观。
        return switch (codeGenType) {
            // Vue 工程模式会出现工具调用消息，需要解析 JSON 并格式化成用户可读内容。
            case VUE_PROJECT ->
                    jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser);
            // HTML 和多文件模式只是纯文本流，简单收集并保存即可。
            case HTML, MULTI_FILE ->
                    new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser);
        };
    }
}
