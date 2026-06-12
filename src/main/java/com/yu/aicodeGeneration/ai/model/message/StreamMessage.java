package com.yu.aicodeGeneration.ai.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息响应基类
 * 学习提示：SSE 通道里会发送多种事件，统一继承这个基类后，前端只需先看 type 再按类型解析。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {

    /**
     * 消息类型
     * 例如 ai_response、tool_request、tool_executed。
     */
    private String type;
}
