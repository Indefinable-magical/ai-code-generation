package com.yu.aicodeGeneration.langgraph4j.node;

import com.yu.aicodeGeneration.ai.AiCodeGenTypeRoutingService;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由工作节点
 *
 * 学习重点：
 * 工作流中的路由节点用于决定后续采用哪种代码生成模式。
 * 它读取 originalPrompt，写入 generationType，后续 CodeGeneratorNode 会按这个类型生成代码。
 */
@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        // node_async 把 lambda 包装成 LangGraph4j 可执行的异步节点。
        return node_async(state -> {
            // 从图状态中取出业务上下文。
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType;
            try {
                // 获取 AI 路由服务。
                // 这里用 SpringContextUtil 是因为节点本身不是 Spring Bean，不能直接 @Resource 注入。
                AiCodeGenTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                // 根据原始提示词进行智能路由，返回 HTML / MULTI_FILE / VUE_PROJECT。
                generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
            } catch (Exception e) {
                // 路由失败时降级到 HTML，保证工作流还能继续跑。
                log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                generationType = CodeGenTypeEnum.HTML;
            }

            // 更新状态：写入当前步骤和生成类型。
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            // 返回 Map，LangGraph4j 会把这些字段合并回 MessagesState。
            return WorkflowContext.saveContext(context);
        });
    }
}
