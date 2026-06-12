package com.yu.aicodeGeneration.langgraph4j.node;

import com.yu.aicodeGeneration.constant.AppConstant;
import com.yu.aicodeGeneration.core.AiCodeGeneratorFacade;
import com.yu.aicodeGeneration.langgraph4j.model.QualityResult;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 网站代码生成节点
 *
 * 学习重点：
 * 这个节点是真正调用 AI 代码生成门面的地方。
 * 如果上一轮质检失败，它会把质检错误转换成修复提示词，让模型重新生成。
 */
@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        // 创建异步节点动作。
        return node_async(state -> {
            // 读取当前工作流上下文。
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");
            // 构造用户消息（包含增强后的提示词，或质检失败后的修复提示词）。
            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            // 获取 AI 代码生成门面服务。
            // 节点不是 Spring Bean，所以通过 SpringContextUtil 拿 Bean。
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            // 先使用固定的 appId（工作流 demo 还没有接入真实应用 ID）。
            Long appId = 0L;
            // 调用流式代码生成。
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            // 工作流节点需要等生成完成后才能进入质检，所以这里阻塞等待流结束。
            codeStream.blockLast(Duration.ofMinutes(10)); // 最多等待 10 分钟
            // 根据类型设置生成目录，后续质量检查和构建节点会使用这个目录。
            String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            // 更新状态。
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
     */
    private static String buildUserMessage(WorkflowContext context) {
        // 默认使用提示词增强节点生成的 enhancedPrompt。
        String userMessage = context.getEnhancedPrompt();
        // 检查是否存在质检失败结果。
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 如果质检失败，就把错误和建议拼成新的修复提示词。
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        // 只有明确 invalid 且 errors 非空，才认为需要重新生成。
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        // 把质检错误和建议转成人类/模型都容易理解的 markdown 提示词。
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 添加错误列表。
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 添加修复建议（如果有）。
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }
}
