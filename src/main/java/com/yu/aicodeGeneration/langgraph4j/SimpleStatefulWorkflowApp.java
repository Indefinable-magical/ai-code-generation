package com.yu.aicodeGeneration.langgraph4j;

import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 简化版带状态定义的工作流 - 只定义状态结构，不实现具体流转
 * 学习提示：相比 SimpleWorkflowApp，这里演示如何在每个节点里读取并修改 WorkflowContext。
 */
@Slf4j
public class SimpleStatefulWorkflowApp {

    /**
     * 创建带状态感知的工作节点
     */
    static AsyncNodeAction<MessagesState<String>> makeStatefulNode(String nodeName, String message) {
        return node_async(state -> {
            // 从 LangGraph4j 的 MessagesState 中取回业务上下文。
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: {} - {}", nodeName, message);
            // 只记录当前步骤，不做具体的状态流转；真实节点会在这里写入更多业务字段。
            if (context != null) {
                context.setCurrentStep(nodeName);
            }
            // 把修改后的上下文重新保存回图状态，供下一个节点读取。
            return WorkflowContext.saveContext(context);
        });
    }

    public static void main(String[] args) throws GraphStateException {
        // 创建工作流图：每个节点都能感知并更新 WorkflowContext。
        CompiledGraph<MessagesState<String>> workflow = new MessagesStateGraph<String>()
                // 添加节点 - 使用带状态感知的节点
                .addNode("image_collector", makeStatefulNode("image_collector", "获取图片素材"))
                .addNode("prompt_enhancer", makeStatefulNode("prompt_enhancer", "增强提示词"))
                .addNode("router", makeStatefulNode("router", "智能路由选择"))
                .addNode("code_generator", makeStatefulNode("code_generator", "网站代码生成"))
                .addNode("project_builder", makeStatefulNode("project_builder", "项目构建"))

                // 添加边
                .addEdge(START, "image_collector")
                .addEdge("image_collector", "prompt_enhancer")
                .addEdge("prompt_enhancer", "router")
                .addEdge("router", "code_generator")
                .addEdge("code_generator", "project_builder")
                .addEdge("project_builder", END)

                // 编译工作流
                .compile();

        // 初始化 WorkflowContext - 只设置基本信息，后续节点会不断更新 currentStep。
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt("创建一个鱼皮的个人博客网站")
                .currentStep("初始化")
                .build();

        log.info("初始输入: {}", initialContext.getOriginalPrompt());
        log.info("开始执行工作流");

        // 显示工作流图，便于对照日志理解节点执行顺序。
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图:\n{}", graph.content());

        // 执行工作流，并在每一步输出当前 WorkflowContext。
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            // 显示当前状态：观察 currentStep 是否被节点正确更新。
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("工作流执行完成！");
    }
}
