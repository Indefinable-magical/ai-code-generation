package com.yu.aicodeGeneration.langgraph4j.ai;

import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集规划服务
 * 学习提示：这是一个 LangChain4j AI Service 接口，运行时会由工厂创建动态代理，不需要手写实现类。
 */
public interface ImageCollectionPlanService {

    /**
     * 根据用户提示词分析需要收集的图片类型和参数
     * 学习提示：系统提示词会要求模型输出 ImageCollectionPlan 结构，后续并发节点按计划分别执行不同素材任务。
     */
    @SystemMessage(fromResource = "prompt/image-collection-plan-system-prompt.txt")
    ImageCollectionPlan planImageCollection(@UserMessage String userPrompt);
}
