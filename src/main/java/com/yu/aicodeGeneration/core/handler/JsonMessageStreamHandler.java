package com.yu.aicodeGeneration.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yu.aicodeGeneration.ai.model.message.*;
import com.yu.aicodeGeneration.ai.tools.BaseTool;
import com.yu.aicodeGeneration.ai.tools.ToolManager;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.model.enums.ChatHistoryMessageTypeEnum;
import com.yu.aicodeGeneration.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 *
 * 学习重点：
 * Vue 工程模式不是普通文本流，而是一串 JSON 消息：
 * 1. AI_RESPONSE：模型说的话；
 * 2. TOOL_REQUEST：模型准备调用某个工具；
 * 3. TOOL_EXECUTED：工具执行完毕，返回执行结果。
 *
 * 这个类负责把机器友好的 JSON 流转换成用户可读的聊天内容，并把重要内容持久化到数据库。
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    // 工具管理器：根据工具名找到具体工具，用于生成“工具请求/执行结果”的展示文本。
    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
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
        // 收集最终要入库的 AI 回复。
        // 注意：不是所有 JSON 消息都要原样入库，工具调用会被格式化成更适合阅读的文本。
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具 ID，判断是否是第一次调用。
        // 同一个工具请求可能分多段流式返回，避免前端重复显示“正在调用 xxx 工具”。
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块，并转换成要继续发给前端的文本。
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                // 有些重复工具请求会返回空字符串，这里过滤掉，避免前端收到空消息。
                .filter(StrUtil::isNotEmpty)
                // 当响应流正常完成时执行回调逻辑
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史。
                    // 这条历史会在下一轮生成时被加载回 AI memory。
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果 AI 回复失败，也要记录错误消息，方便后续排查。
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 第一步先解析成基础 StreamMessage，只读取 type 字段判断具体消息类型。
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                // 普通 AI 文本：转换成 AiResponseMessage 后拿 data 字段。
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 普通文本既要返回给前端，也要拼到历史记录中。
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                // 工具请求：表示模型打算调用某个工具，例如写文件、读文件、修改文件。
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID。
                // 同一个工具调用的参数可能分片返回，第一次显示即可。
                // !seenToolIds.contains(toolId) 判断当前工具 ID 是否尚未处理过，避免重复处理同一个工具调用
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息。
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例，让每种工具自己决定展示文案。
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息，如“正在写入文件...”。
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次看到这个工具 ID，说明是同一次工具调用的重复片段，直接丢弃。
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                // 工具执行完成：包含工具名和参数，通常可以生成“已写入 xxx 文件”这样的结果消息。
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                // arguments 是 JSON 字符串，这里转成 JSONObject 方便具体工具读取 path、content 等字段。
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 根据工具名称获取工具实例。
                String toolName = toolExecutedMessage.getName();
                BaseTool tool = toolManager.getTool(toolName);
                // 让工具自己根据参数生成执行结果文案。
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出给前端和持久化的内容都使用同一份格式化文本。
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                // 未知消息类型不让流程崩溃，只记录日志并过滤掉。
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
