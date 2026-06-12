package com.yu.aicodeGeneration.langgraph4j.node.concurrent;

import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.langgraph4j.tools.ImageSearchTool;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ContentImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 内容图片分支只处理 plan.contentImageTasks，不关心插画、Logo、架构图任务。
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> contentImages = new ArrayList<>();
            try {
                // 从规划节点写入的上下文中取出图片收集计划。
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getContentImageTasks() != null) {
                    // 从 Spring 容器获取工具，工具内部负责调用搜索 API 或返回资源列表。
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    log.info("开始并发收集内容图片，任务数: {}", plan.getContentImageTasks().size());
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        // 每个 task.query() 是模型规划出的搜索关键词。
                        List<ImageResource> images = imageSearchTool.searchContentImages(task.query());
                        if (images != null) {
                            contentImages.addAll(images);
                        }
                    }
                    log.info("内容图片收集完成，共收集到 {} 张图片", contentImages.size());
                }
            } catch (Exception e) {
                // 单个分支失败只影响内容图，不影响其他图片分支和后续代码生成。
                log.error("内容图片收集失败: {}", e.getMessage(), e);
            }
            // 将收集到的图片存储到上下文的中间字段中，等待 ImageAggregatorNode 统一合并。
            context.setContentImages(contentImages);
            context.setCurrentStep("内容图片收集");
            return WorkflowContext.saveContext(context);
        });
    }
}
