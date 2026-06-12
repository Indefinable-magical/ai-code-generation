package com.yu.aicodeGeneration.langgraph4j.node.concurrent;

import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片汇总节点
 * 学习提示：四个图片分支各写一个中间列表，本节点负责合并成最终 imageList，供 PromptEnhancerNode 使用。
 */
@Slf4j
public class ImageAggregatorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 读取包含各类图片中间结果的上下文。
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> allImages = new ArrayList<>();
            log.info("开始聚合并发收集的图片");
            // 从各个中间字段聚合图片；为空说明对应分支没有任务或执行失败。
            if (context.getContentImages() != null) {
                allImages.addAll(context.getContentImages());
            }
            if (context.getIllustrations() != null) {
                allImages.addAll(context.getIllustrations());
            }
            if (context.getDiagrams() != null) {
                allImages.addAll(context.getDiagrams());
            }
            if (context.getLogos() != null) {
                allImages.addAll(context.getLogos());
            }
            log.info("图片聚合完成，总共 {} 张图片", allImages.size());
            // 更新最终的图片列表：后面的提示词增强节点只读取 imageList，不需要关心图片来源分支。
            context.setImageList(allImages);
            context.setCurrentStep("图片聚合");
            return WorkflowContext.saveContext(context);
        });
    }
}
