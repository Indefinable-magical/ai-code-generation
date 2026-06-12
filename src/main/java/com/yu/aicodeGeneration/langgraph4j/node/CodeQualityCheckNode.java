package com.yu.aicodeGeneration.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.langgraph4j.ai.CodeQualityCheckService;
import com.yu.aicodeGeneration.langgraph4j.model.QualityResult;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码质量检查节点
 * 学习提示：该节点读取生成目录里的代码，交给 AI 做结构化质检，结果会影响工作流是否重试代码生成。
 */
@Slf4j
public class CodeQualityCheckNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 从图状态中取出业务上下文。
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码质量检查");
            // 生成代码目录由 CodeGeneratorNode 写入。
            String generatedCodeDir = context.getGeneratedCodeDir();
            QualityResult qualityResult;
            try {
                // 1. 读取并拼接代码文件内容，构造成适合 AI 审查的长文本。
                String codeContent = readAndConcatenateCodeFiles(generatedCodeDir);
                if (StrUtil.isBlank(codeContent)) {
                    log.warn("未找到可检查的代码文件");
                    // 没有代码可检查时，明确标记为不通过，提示工作流重新生成。
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的代码文件"))
                            .suggestions(List.of("请确保代码生成成功"))
                            .build();
                } else {
                    // 2. 调用 AI 进行代码质量检查，返回 QualityResult 结构化结果。
                    CodeQualityCheckService qualityCheckService = SpringContextUtil.getBean(CodeQualityCheckService.class);
                    qualityResult = qualityCheckService.checkCodeQuality(codeContent);
                    log.info("代码质量检查完成 - 是否通过: {}", qualityResult.getIsValid());
                }
            } catch (Exception e) {
                log.error("代码质量检查异常: {}", e.getMessage(), e);
                // 质检服务异常时选择放行，避免质检不可用导致所有生成任务不可用。
                qualityResult = QualityResult.builder()
                        .isValid(true) // 异常直接跳到下一个步骤
                        .build();
            }
            // 3. 更新状态：条件边会读取 qualityResult 判断下一步。
            context.setCurrentStep("代码质量检查");
            context.setQualityResult(qualityResult);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 需要检查的文件扩展名
     * 学习提示：这里只检查前端项目相关源码，跳过图片、锁文件、构建产物等非核心内容。
     */
    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
            ".html", ".htm", ".css", ".js", ".json", ".vue", ".ts", ".jsx", ".tsx"
    );

    /**
     * 读取并拼接代码目录下的所有代码文件
     */
    private static String readAndConcatenateCodeFiles(String codeDir) {
        // 目录为空时直接返回空串，让上层构造“不通过”的质检结果。
        if (StrUtil.isBlank(codeDir)) {
            return "";
        }
        File directory = new File(codeDir);
        // 目录不存在或不是目录，说明前面的保存节点可能失败。
        if (!directory.exists() || !directory.isDirectory()) {
            log.error("代码目录不存在或不是目录: {}", codeDir);
            return "";
        }
        // 给 AI 的文本先包含总标题，再逐个追加文件路径和内容。
        StringBuilder codeContent = new StringBuilder();
        codeContent.append("# 项目文件结构和代码内容\n\n");
        // 使用 Hutool 的 walkFiles 方法遍历所有文件。
        FileUtil.walkFiles(directory, file -> {
            // 过滤条件：跳过隐藏文件、特定目录下的文件、非代码文件。
            if (shouldSkipFile(file, directory)) {
                return;
            }
            if (isCodeFile(file)) {
                // 使用相对路径写入标题，AI 可以知道每段代码来自哪个文件。
                String relativePath = FileUtil.subPath(directory.getAbsolutePath(), file.getAbsolutePath());
                codeContent.append("## 文件: ").append(relativePath).append("\n\n");
                // 读取 UTF-8 文本并追加到质检上下文。
                String fileContent = FileUtil.readUtf8String(file);
                codeContent.append(fileContent).append("\n\n");
            }
        });
        return codeContent.toString();
    }

    /**
     * 判断是否应该跳过此文件
     */
    private static boolean shouldSkipFile(File file, File rootDir) {
        // 计算相对路径，后续通过路径片段判断是否处于 node_modules、dist 等目录。
        String relativePath = FileUtil.subPath(rootDir.getAbsolutePath(), file.getAbsolutePath());
        // 跳过隐藏文件。
        if (file.getName().startsWith(".")) {
            return true;
        }
        // 跳过特定目录下的文件，避免把依赖、构建产物、Git 元数据送给模型审查。
        return relativePath.contains("node_modules" + File.separator) ||
                relativePath.contains("dist" + File.separator) ||
                relativePath.contains("target" + File.separator) ||
                relativePath.contains(".git" + File.separator);
    }

    /**
     * 判断是否是需要检查的代码文件
     */
    private static boolean isCodeFile(File file) {
        // 按文件后缀判断是否是需要质检的源码文件。
        String fileName = file.getName().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
