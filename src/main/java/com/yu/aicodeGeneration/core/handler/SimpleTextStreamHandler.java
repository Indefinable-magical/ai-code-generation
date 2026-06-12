package com.yu.aicodeGeneration.core.handler;

import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.model.enums.ChatHistoryMessageTypeEnum;
import com.yu.aicodeGeneration.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 *
 * 学习重点：
 * 这个处理器不改变前端看到的流，只在旁边“偷看并收集”所有 chunk。
 * 等 AI 输出完成后，把完整 AI 回复保存到 chat_history，供下次对话恢复上下文。
 */
@Slf4j
public class SimpleTextStreamHandler {


    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 用 StringBuilder 收集完整 AI 回复。
        // Flux 是一段一段来的，如果不收集，流结束后就拿不到完整内容入库。
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    // map 中做两个动作：
                    // 1. 追加当前 chunk 到完整回复；
                    // 2. 原样返回 chunk，保证前端仍能实时收到同样内容。
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，完整 AI 回复已经拼好，可以写入数据库。
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果 AI 回复失败，也保存一条错误消息。
                    // 这样管理后台或历史记录里能看到失败原因，而不是凭空少一条 AI 消息。
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }
}
