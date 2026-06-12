package com.yu.aicodeGeneration.langgraph4j.node.concurrent;

import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.langgraph4j.tools.MermaidDiagramTool;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class DiagramCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 架构图分支只处理 plan.diagramTasks。
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> diagrams = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getDiagramTasks() != null) {
                    // MermaidDiagramTool 负责把 Mermaid 代码转换成可用图片资源。
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    log.info("开始并发生成架构图，任务数: {}", plan.getDiagramTasks().size());
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        // 每个任务同时携带 Mermaid 代码和自然语言描述，便于生成后说明图片用途。
                        List<ImageResource> images = diagramTool.generateMermaidDiagram(
                                task.mermaidCode(), task.description());
                        if (images != null) {
                            diagrams.addAll(images);
                        }
                    }
                    log.info("架构图生成完成，共生成 {} 张图片", diagrams.size());
                }
            } catch (Exception e) {
                // 架构图生成失败时只记录日志，后续仍可基于其他素材继续生成页面。
                log.error("架构图生成失败: {}", e.getMessage(), e);
            }
            // 写入中间字段 diagrams，等待聚合节点汇总。
            context.setDiagrams(diagrams);
            context.setCurrentStep("架构图生成");
            return WorkflowContext.saveContext(context);
        });
    }
}
