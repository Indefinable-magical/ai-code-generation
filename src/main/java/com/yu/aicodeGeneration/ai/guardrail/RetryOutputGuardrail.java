package com.yu.aicodeGeneration.ai.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * 重试输出护轨
 * 学习提示：OutputGuardrail 会检查模型输出；这里不是直接失败，而是通过 reprompt 要求模型重新生成。
 */
public class RetryOutputGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        // 提取模型返回文本；结构化输出失败、空响应等问题会在这里暴露。
        String response = responseFromLLM.text();
        // 检查响应是否为空或过短：空内容或明显太短通常说明模型调用异常或没按要求生成。
        if (response == null || response.trim().isEmpty()) {
            return reprompt("响应内容为空", "请重新生成完整的内容");
        }
        if (response.trim().length() < 10) {
            return reprompt("响应内容过短", "请提供更详细的内容");
        }
        // 检查是否包含敏感信息或不当内容：避免把密钥、密码等看起来敏感的内容继续传给前端。
        if (containsSensitiveContent(response)) {
            return reprompt("包含敏感信息", "请重新生成内容，避免包含敏感信息");
        }
        // 输出质量满足最基本规则，LangChain4j 会把结果交给业务层继续处理。
        return success();
    }
    
    /**
     * 检查是否包含敏感内容
     */
    private boolean containsSensitiveContent(String response) {
        // 转小写后做关键词匹配，兼容英文敏感词大小写变化。
        String lowerResponse = response.toLowerCase();
        String[] sensitiveWords = {
            "密码", "password", "secret", "token", 
            "api key", "私钥", "证书", "credential"
        };
        // 任意敏感词命中就认为需要重新生成。
        for (String word : sensitiveWords) {
            if (lowerResponse.contains(word)) {
                return true;
            }
        }
        // 没命中敏感关键词，返回 false。
        return false;
    }
}
