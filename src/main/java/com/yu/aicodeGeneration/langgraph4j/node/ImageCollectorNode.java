package com.yu.aicodeGeneration.langgraph4j.node;

import com.yu.aicodeGeneration.langgraph4j.ai.ImageCollectionPlanService;
import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.langgraph4j.tools.ImageSearchTool;
import com.yu.aicodeGeneration.langgraph4j.tools.LogoGeneratorTool;
import com.yu.aicodeGeneration.langgraph4j.tools.MermaidDiagramTool;
import com.yu.aicodeGeneration.langgraph4j.tools.UndrawIllustrationTool;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点（并发）
 * 学习提示：这个节点先让 AI 规划需要哪些素材，再用 CompletableFuture 并发调用不同图片工具。
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 从图状态中取出业务上下文，原始 prompt 是图片规划的输入。
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            // 用于汇总所有工具返回的图片资源，最终写回 WorkflowContext。
            List<ImageResource> collectedImages = new ArrayList<>();

            try {
                // 第一步：获取图片收集计划，由 AI 判断需要内容图、插画、架构图、Logo 中的哪些任务。
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("获取到图片收集计划，开始并发执行");

                // 第二步：并发执行各种图片收集任务，每个 future 返回一组 ImageResource。
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                // 并发执行内容图片搜索：每个关键词启动一个异步任务。
                if (plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                imageSearchTool.searchContentImages(task.query())));
                    }
                }
                // 并发执行插画图片搜索：适合找 unDraw 等轻量插画资源。
                if (plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                illustrationTool.searchIllustrations(task.query())));
                    }
                }
                // 并发执行架构图生成：Mermaid 代码和描述一起交给工具生成图片资源。
                if (plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                    }
                }
                // 并发执行 Logo 生成：根据描述生成与站点主题匹配的 Logo 资源。
                if (plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoTool.generateLogos(task.description())));
                    }
                }

                // 等待所有任务完成并收集结果；join 会阻塞到全部 future 完成。
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));
                allTasks.join();
                // 收集所有结果：单个工具返回 null 时跳过，不影响其他工具的结果。
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.get();
                    if (images != null) {
                        collectedImages.addAll(images);
                    }
                }
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception e) {
                // 图片收集失败不让整个工作流终止；后续代码生成仍可基于原始 prompt 继续。
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            // 更新状态：记录当前步骤，并把收集到的图片资源交给后续提示词增强节点。
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            return WorkflowContext.saveContext(context);
        });
    }
}
