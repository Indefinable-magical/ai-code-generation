package com.yu.aicodeGeneration.ai.tools;

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
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容
 *
 * 学习重点：
 * 这个工具采用“精确替换”策略：必须提供旧内容和新内容。
 * 这样比让模型重写整个文件更安全，也更容易在聊天记录里展示修改前后差异。
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    @Resource
    private GenerationTaskManager generationTaskManager;

    @Tool("修改文件内容，用新内容替换指定的旧内容")
    public String modifyFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        try {
            // 停止信号发出后，不再允许工具修改当前应用的已有文件。
            if (generationTaskManager.isStopRequested(appId)) {
                return "应用生成已停止，跳过修改文件: " + relativeFilePath;
            }
            // 把模型传入的路径转为 Path。
            Path path = Paths.get(relativeFilePath);
            // 相对路径限定在当前应用项目目录下，防止修改系统其他文件。
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            // 修改前必须确认目标存在且是普通文件。
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            // 读取原文件内容，后续用 oldContent 做精确匹配。
            String originalContent = Files.readString(path);
            // 如果旧内容不存在，说明模型的上下文可能过期，不应该盲目改文件。
            if (!originalContent.contains(oldContent)) {
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            // 执行字符串替换。
            String modifiedContent = originalContent.replace(oldContent, newContent);
            // 替换后如果没有变化，提示模型无需继续。
            if (originalContent.equals(modifiedContent)) {
                return "信息：替换后文件内容未发生变化 - " + relativeFilePath;
            }
            // 覆盖写回文件。
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件: {}", path.toAbsolutePath());
            return "文件修改成功: " + relativeFilePath;
        } catch (IOException e) {
            // 返回错误给模型，让模型可以选择重读文件后重新修改。
            String errorMessage = "修改文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 格式化修改记录，让前端/历史记录清楚看到“替换前”和“替换后”。
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
        // 显示对比内容
        return String.format("""
                [工具调用] %s %s
                
                替换前：
                ```
                %s
                ```
                
                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
    }
}
