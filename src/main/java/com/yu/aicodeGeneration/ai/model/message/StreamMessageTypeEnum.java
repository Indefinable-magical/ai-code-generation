package com.yu.aicodeGeneration.ai.model.message;

import lombok.Getter;

/**
 * 流式消息类型枚举
 * 学习提示：枚举集中维护前后端约定的 type 字符串，避免不同地方手写导致拼写不一致。
 */
@Getter
public enum StreamMessageTypeEnum {

    // AI 普通文本响应。
    AI_RESPONSE("ai_response", "AI响应"),
    // AI 请求调用工具，前端可以展示“正在调用某工具”的过程。
    TOOL_REQUEST("tool_request", "工具请求"),
    // 工具执行完成，携带执行参数和结果，方便前端展示代理式生成过程。
    TOOL_EXECUTED("tool_executed", "工具执行结果");

    // 给前端和序列化使用的稳定值。
    private final String value;
    // 中文说明，主要用于后端日志或管理端展示。
    private final String text;

    StreamMessageTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据值获取枚举
     */
    public static StreamMessageTypeEnum getEnumByValue(String value) {
        // 遍历枚举常量，根据 value 字段反查枚举对象。
        for (StreamMessageTypeEnum typeEnum : values()) {
            if (typeEnum.getValue().equals(value)) {
                return typeEnum;
            }
        }
        // 未匹配时返回 null，让调用方自行决定是忽略还是报错。
        return null;
    }
}
