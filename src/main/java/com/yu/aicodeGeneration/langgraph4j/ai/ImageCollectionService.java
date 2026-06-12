package com.yu.aicodeGeneration.langgraph4j.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集 AI 服务接口
 * 使用 AI 调用工具收集不同类型的图片资源
 * 学习提示：这个接口让模型根据 prompt 自主选择工具，适合“先让 AI 判断要搜什么图”的串行收集方式。
 */
public interface ImageCollectionService {

    /**
     * 根据用户提示词收集所需的图片资源
     * AI 会根据需求自主选择调用相应的工具
     * 学习提示：返回 String 是工具调用后的综合结果，节点再把结果写入 WorkflowContext。
     */
    @SystemMessage(fromResource = "prompt/image-collection-system-prompt.txt")
    String collectImages(@UserMessage String userPrompt);
}
