package com.yu.aicodeGeneration.langgraph4j.node.concurrent;

import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.langgraph4j.tools.LogoGeneratorTool;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class LogoCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // Logo 分支只处理 plan.logoTasks。
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> logos = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getLogoTasks() != null) {
                    // LogoGeneratorTool 根据自然语言描述生成或检索品牌标识资源。
                    LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    log.info("开始并发生成Logo，任务数: {}", plan.getLogoTasks().size());
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        // description 通常包含品牌名、行业、风格关键词等。
                        List<ImageResource> images = logoTool.generateLogos(task.description());
                        if (images != null) {
                            logos.addAll(images);
                        }
                    }
                    log.info("Logo生成完成，共生成 {} 张图片", logos.size());
                }
            } catch (Exception e) {
                // Logo 失败不阻断后续生成；页面可退化为文字标题或默认样式。
                log.error("Logo生成失败: {}", e.getMessage(), e);
            }
            // 写入中间字段 logos，等待 ImageAggregatorNode 汇总。
            context.setLogos(logos);
            context.setCurrentStep("Logo生成");
            return WorkflowContext.saveContext(context);
        });
    }
}
