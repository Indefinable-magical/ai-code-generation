package com.yu.aicodeGeneration.langgraph4j.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片收集计划
 * 学习提示：这是图片规划模型的结构化输出，ImagePlanNode 生成它，后续并发收集节点按任务列表执行。
 */
@Data
public class ImageCollectionPlan implements Serializable {
    
    /**
     * 内容图片搜索任务列表
     * 学习提示：适合真实照片、产品图、背景图等需要关键词搜索的素材。
     */
    private List<ImageSearchTask> contentImageTasks;
    
    /**
     * 插画图片搜索任务列表
     * 学习提示：适合空状态、功能说明、装饰性插画等轻量视觉素材。
     */
    private List<IllustrationTask> illustrationTasks;
    
    /**
     * 架构图生成任务列表
     * 学习提示：适合技术类页面，需要根据 Mermaid 代码生成流程图或架构图。
     */
    private List<DiagramTask> diagramTasks;
    
    /**
     * Logo生成任务列表
     * 学习提示：适合品牌页、产品页，需要生成或搜索与主题相关的标识图。
     */
    private List<LogoTask> logoTasks;
    
    /**
     * 内容图片搜索任务
     * 对应 ImageSearchTool.searchContentImages(String query)
     * @param query 搜索关键词，由规划模型从用户需求中提取。
     */
    public record ImageSearchTask(String query) implements Serializable {}
    
    /**
     * 插画图片搜索任务
     * 对应 UndrawIllustrationTool.searchIllustrations(String query)
     * @param query 插画检索关键词。
     */
    public record IllustrationTask(String query) implements Serializable {}
    
    /**
     * 架构图生成任务
     * 对应 MermaidDiagramTool.generateMermaidDiagram(String mermaidCode, String description)
     * @param mermaidCode Mermaid 图定义代码。
     * @param description 图的文字说明，便于聚合后给 prompt_enhancer 使用。
     */
    public record DiagramTask(String mermaidCode, String description) implements Serializable {}
    
    /**
     * Logo生成任务
     * 对应 LogoGeneratorTool.generateLogos(String description)
     * @param description Logo 风格和主题描述。
     */
    public record LogoTask(String description) implements Serializable {}
}
