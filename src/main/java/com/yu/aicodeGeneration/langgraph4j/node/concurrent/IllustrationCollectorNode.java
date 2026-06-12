package com.yu.aicodeGeneration.langgraph4j.node.concurrent;

import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.langgraph4j.tools.UndrawIllustrationTool;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class IllustrationCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 插画分支只处理 plan.illustrationTasks。
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> illustrations = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getIllustrationTasks() != null) {
                    // 插画工具通常返回风格统一、适合网页说明区块使用的图片资源。
                    UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    log.info("开始并发收集插画图片，任务数: {}", plan.getIllustrationTasks().size());
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        // 使用规划模型给出的 query 搜索插画。
                        List<ImageResource> images = illustrationTool.searchIllustrations(task.query());
                        if (images != null) {
                            illustrations.addAll(images);
                        }
                    }
                    log.info("插画图片收集完成，共收集到 {} 张图片", illustrations.size());
                }
            } catch (Exception e) {
                // 插画失败不影响其他素材分支，聚合时会自然跳过空列表。
                log.error("插画图片收集失败: {}", e.getMessage(), e);
            }
            // 写入中间字段 illustrations，后续由聚合节点合并到 imageList。
            context.setIllustrations(illustrations);
            context.setCurrentStep("插画图片收集");
            return WorkflowContext.saveContext(context);
        });
    }
}
