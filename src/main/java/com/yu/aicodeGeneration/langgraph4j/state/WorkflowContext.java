package com.yu.aicodeGeneration.langgraph4j.state;

import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.model.QualityResult;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文 - 存储所有状态信息
 *
 * 学习重点：
 * LangGraph4j 的每个节点都通过这个对象交换数据。
 * 你可以把它理解成一次工作流执行中的“共享黑板”：前一个节点写，后一个节点读。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    // MessagesState 本质上是一个 Map，这个 key 用来存取 WorkflowContext。
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 当前执行步骤
     */
    // 用于日志和 SSE 进度展示。
    private String currentStep;

    /**
     * 用户原始输入的提示词
     */
    // 用户最开始输入的需求，路由、图片收集、提示词增强都会基于它工作。
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    // 图片资源的文本形式，常用于拼入增强提示词。
    private String imageListStr;

    /**
     * 图片资源列表
     */
    // 结构化图片资源列表，供图片收集和聚合节点使用。
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    // 在原始 prompt 基础上加入图片、风格、结构等信息后的 prompt。
    private String enhancedPrompt;

    /**
     * 代码生成类型
     */
    // 路由节点输出的生成类型，决定代码生成节点调用哪条生成链路。
    private CodeGenTypeEnum generationType;

    /**
     * 生成的代码目录
     */
    // AI 生成并保存后的源码目录。
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    // Vue 项目构建后的 dist 目录，HTML/MULTI_FILE 通常不需要。
    private String buildResultDir;

    /**
     * 质量检查结果
     */
    // 质量检查节点写入，条件边会根据它决定是否重新生成。
    private QualityResult qualityResult;

    /**
     * 错误信息
     */
    // 工作流异常或节点错误时可写入这里。
    private String errorMessage;

    /**
     * 图片收集计划
     */
    // 并发图片收集工作流会先生成计划，再分派给不同图片工具。
    private ImageCollectionPlan imageCollectionPlan;

    /**
     * 并发图片收集的中间结果字段
     */
    // 以下字段分别保存不同并发图片收集节点的中间产物，最终由聚合节点合并。
    private List<ImageResource> contentImages;
    private List<ImageResource> illustrations;
    private List<ImageResource> diagrams;
    private List<ImageResource> logos;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        // 从 MessagesState 的 data Map 中取出上下文对象。
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        // 返回 Map 给 LangGraph4j，框架会把它合并回 state。
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}
