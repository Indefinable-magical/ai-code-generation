package com.yu.aicodeGeneration.langgraph4j.node;

import com.yu.aicodeGeneration.core.builder.VueProjectBuilder;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点
 * 学习提示：代码生成完成后，只有 Vue 项目需要进入该节点执行 npm install 和 npm run build。
 */
@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 读取上下文中的生成目录和生成类型。
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            // 获取必要的参数：generatedCodeDir 是前面保存代码节点写入的项目目录。
            String generatedCodeDir = context.getGeneratedCodeDir();
            // generationType 目前主要用于语义说明，进入该节点前条件边已经保证应当是 Vue 项目。
            CodeGenTypeEnum generationType = context.getGenerationType();
            String buildResultDir;
            // 一定是 Vue 项目类型：使用 VueProjectBuilder 进行构建。
            try {
                // 从 Spring 容器取构建器，避免静态 new 导致依赖、配置无法注入。
                VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                // 执行 Vue 项目构建（npm install + npm run build）。
                boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                if (buildSuccess) {
                    // 构建成功，返回 dist 目录路径；部署静态资源时应优先使用这个目录。
                    buildResultDir = generatedCodeDir + File.separator + "dist";
                    log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                } else {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                }
            } catch (Exception e) {
                log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                // 异常时返回原路径：让后续流程仍有目录可用，避免整个工作流完全中断。
                buildResultDir = generatedCodeDir;
            }
            // 更新状态：保存最终可用于预览/部署的目录。
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
