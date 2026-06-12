package com.yu.aicodeGeneration.langgraph4j;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.langgraph4j.model.QualityResult;
import com.yu.aicodeGeneration.langgraph4j.node.*;
import com.yu.aicodeGeneration.langgraph4j.node.concurrent.*;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 并发执行的代码生成工作流
 * 学习提示：这个版本把图片素材收集拆成多个并发节点，图片准备完成后再进入提示词增强、路由、代码生成和构建。
 */
@Slf4j
public class CodeGenConcurrentWorkflow {

    /**
     * 创建并发工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            // MessagesStateGraph 是 LangGraph4j 提供的状态图；每个节点读写同一份 WorkflowContext。
            return new MessagesStateGraph<String>()
                    // 添加主流程节点：图片计划 -> 提示词增强 -> 类型路由 -> 代码生成 -> 质检 -> 项目构建。
                    .addNode("image_plan", ImagePlanNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    // 添加并发图片收集节点：不同类型素材互不依赖，可以同时跑。
                    .addNode("content_image_collector", ContentImageCollectorNode.create())
                    .addNode("illustration_collector", IllustrationCollectorNode.create())
                    .addNode("diagram_collector", DiagramCollectorNode.create())
                    .addNode("logo_collector", LogoCollectorNode.create())
                    .addNode("image_aggregator", ImageAggregatorNode.create())

                    // 添加边：START 是图的虚拟起点，先进入图片计划节点。
                    .addEdge(START, "image_plan")

                    // 并发分支：从计划节点分发到各个收集节点，LangGraph4j 会让这些节点并行推进。
                    .addEdge("image_plan", "content_image_collector")
                    .addEdge("image_plan", "illustration_collector")
                    .addEdge("image_plan", "diagram_collector")
                    .addEdge("image_plan", "logo_collector")

                    // 汇聚：所有收集节点都汇聚到聚合器，由聚合器统一整理到上下文中。
                    .addEdge("content_image_collector", "image_aggregator")
                    .addEdge("illustration_collector", "image_aggregator")
                    .addEdge("diagram_collector", "image_aggregator")
                    .addEdge("logo_collector", "image_aggregator")

                    // 继续串行流程：素材准备好后，增强 prompt，再选择生成类型并生成代码。
                    .addEdge("image_aggregator", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "code_quality_check")

                    // 质检条件边：根据质检结果动态决定是构建、结束，还是回到代码生成重试。
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END,
                                    "fail", "code_generator"
                            ))
                    .addEdge("project_builder", END)
                    .compile();
        } catch (GraphStateException e) {
            // 图结构配置错误会在 compile 阶段暴露，统一转成业务异常，避免上层处理底层框架异常。
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "并发工作流创建失败");
        }
    }

    /**
     * 执行并发工作流
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        // 每次执行都创建一份编译后的工作流，保证本次运行拥有独立的初始上下文。
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("并发工作流图:\n{}", graph.content());
        log.info("开始执行并发代码生成工作流");
        WorkflowContext finalContext = null;
        int stepCounter = 1;
        // 配置并发执行线程池：图片收集节点可能包含网络请求，单独线程池可以提升整体吞吐。
        ExecutorService pool = ExecutorBuilder.create()
                .setCorePoolSize(10)
                .setMaxPoolSize(20)
                .setWorkQueue(new LinkedBlockingQueue<>(100))
                .setThreadFactory(ThreadFactoryBuilder.create().setNamePrefix("Parallel-Image-Collect").build())
                .build();
        // 把线程池绑定给 image_plan 的并行分支执行，控制并发节点的执行资源。
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor("image_plan", pool)
                .build();
        // stream 会逐步产出每个节点执行后的状态快照，适合调试和日志追踪。
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext),
                runnableConfig)) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                // 持续更新 finalContext，循环结束后它就是最后一个节点写出的上下文。
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("并发代码生成工作流执行完成！");
        return finalContext;
    }

    /**
     * 路由函数：根据质检结果决定下一步
     */
    private String routeAfterQualityCheck(MessagesState<String> state) {
        // 从图状态中取出业务上下文，读取质检结果和生成类型。
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();

        // 质检失败或没有质检结果，都回到 code_generator 重新生成。
        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质检失败，需要重新生成代码");
            return "fail";
        }
        log.info("代码质检通过，继续后续流程");
        CodeGenTypeEnum generationType = context.getGenerationType();
        // 只有 Vue 项目需要额外 npm install / build；HTML 或多文件静态项目可直接结束。
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return "build";
        } else {
            return "skip_build";
        }
    }
}
