package com.yu.aicodeGeneration.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.yu.aicodeGeneration.constant.AppConstant;
import com.yu.aicodeGeneration.manager.GenerationTaskManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 *
 * 学习重点：
 * Vue 工程生成时，模型不是一次性返回所有代码，而是通过这个工具把文件写到项目目录。
 * @Tool 标记的方法会暴露给模型，@P 是给模型看的参数说明。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    @Resource
    private GenerationTaskManager generationTaskManager;

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            // 生成停止后，可能仍有延迟到达的工具调用；这里兜底阻止继续写文件。
            if (generationTaskManager.isStopRequested(appId)) {
                return "应用生成已停止，跳过写入文件: " + relativeFilePath;
            }
            // 先把模型传入的字符串路径转成 Path。
            Path path = Paths.get(relativeFilePath);
            // 如果模型给的是相对路径，就限制到当前 app 的 vue_project_appId 目录下。
            // 这样模型不能随便写到系统其他目录。
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            // 创建父目录（如果不存在），比如写 src/components/Button.vue 前先创建 src/components。
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容。
            // CREATE 表示文件不存在就创建，TRUNCATE_EXISTING 表示文件存在就覆盖。
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把服务器绝对路径泄露给用户。
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            // 工具异常不直接抛出，而是返回错误文本给模型，让模型有机会修正路径或重试。
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // JsonMessageStreamHandler 会调用这个方法，把工具执行参数格式化成聊天记录。
        String relativeFilePath = arguments.getStr("relativeFilePath");
        // 根据文件后缀给 markdown 代码块加语言标识，前端渲染时可以高亮。
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }
}
