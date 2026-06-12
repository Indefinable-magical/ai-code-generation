package com.yu.aicodeGeneration.langgraph4j.node.concurrent;

import com.yu.aicodeGeneration.langgraph4j.ai.ImageCollectionPlanService;
import com.yu.aicodeGeneration.langgraph4j.model.ImageCollectionPlan;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片规划节点
 * 学习提示：这是图片并发流程的起点，负责把用户需求拆成内容图、插画、架构图、Logo 四类任务。
 */
@Slf4j
public class ImagePlanNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 读取工作流上下文，原始 prompt 是规划模型的输入。
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            try {
                // 获取图片收集计划服务：由 LangChain4j 代理调用大模型生成结构化计划。
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("生成图片收集计划，准备启动并发分支");
                // 将计划存储到上下文中，后续四个并发收集节点都会从这里读取各自任务列表。
                context.setImageCollectionPlan(plan);
                context.setCurrentStep("图片计划");
            } catch (Exception e) {
                // 规划失败时不终止工作流，后续聚合会得到空图片列表，生成仍可继续。
                log.error("图片计划生成失败: {}", e.getMessage(), e);
            }
            return WorkflowContext.saveContext(context);
        });
    }
}
