package com.yu.aicodeGeneration.ai.model.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用消息
 * 学习提示：AI 决定调用工具时，会先把“即将调用什么工具、参数是什么”以该消息推送给前端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {

    // 工具调用请求 ID，后续 ToolExecutedMessage 会使用同一个 ID 关联结果。
    private String id;

    // 工具名称，例如 readFile、writeFile 等，具体取决于工具类上的 @Tool 定义。
    private String name;

    // 工具参数 JSON 字符串，前端可以用它展示 AI 想对哪些文件做操作。
    private String arguments;

    public ToolRequestMessage(ToolExecutionRequest toolExecutionRequest) {
        // 标记消息类型为工具请求。
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        // 从 LangChain4j 的工具调用请求中复制必要字段，避免前端依赖框架内部对象结构。
        this.id = toolExecutionRequest.id();
        this.name = toolExecutionRequest.name();
        this.arguments = toolExecutionRequest.arguments();
    }
}
