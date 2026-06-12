package com.yu.aicodeGeneration.ai.model.message;

import dev.langchain4j.service.tool.ToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果消息
 * 学习提示：当 AI 工具调用执行完毕后，把工具名称、参数和结果包装成这个消息推给前端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolExecutedMessage extends StreamMessage {

    // 工具调用请求 ID，用于和前面的 ToolRequestMessage 对应起来。
    private String id;

    // 工具名称，例如文件读取、文件写入、目录读取等。
    private String name;

    // 工具调用参数，通常是 LangChain4j 传入的 JSON 字符串。
    private String arguments;

    // 工具执行返回结果，可能是文件内容、写入成功信息或错误信息。
    private String result;

    public ToolExecutedMessage(ToolExecution toolExecution) {
        // 标记消息类型为工具执行结果。
        super(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        // 从 LangChain4j ToolExecution 中拆出请求元数据和执行结果，便于前端展示完整过程。
        this.id = toolExecution.request().id();
        this.name = toolExecution.request().name();
        this.arguments = toolExecution.request().arguments();
        this.result = toolExecution.result();
    }
}
