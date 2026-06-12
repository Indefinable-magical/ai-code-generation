package com.yu.aicodeGeneration.langgraph4j.ai;

import com.yu.aicodeGeneration.langgraph4j.model.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 代码质量检查服务
 * 学习提示：该服务让模型扮演代码审查器，输出 QualityResult 给工作流条件边使用。
 */
public interface CodeQualityCheckService {

    /**
     * 检查代码质量
     * AI 会分析代码并返回质量检查结果
     * 学习提示：返回结果是结构化对象，isValid 决定工作流继续构建还是回到代码生成重试。
     */
    @SystemMessage(fromResource = "prompt/code-quality-check-system-prompt.txt")
    QualityResult checkCodeQuality(@UserMessage String codeContent);
}
