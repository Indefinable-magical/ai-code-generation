package com.yu.aicodeGeneration.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 响应消息
 * 学习提示：流式生成时，普通文本片段会包装成这个类型，再通过 SSE 发送给前端。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AiResponseMessage extends StreamMessage {

    // AI 本次输出的文本片段，前端收到后按顺序追加到聊天窗口。
    private String data;

    public AiResponseMessage(String data) {
        // 父类 type 标记为 ai_response，前端据此区分“文本输出”和“工具调用事件”。
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.data = data;
    }
}
