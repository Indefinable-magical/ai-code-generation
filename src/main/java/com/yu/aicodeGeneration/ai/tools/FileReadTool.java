package com.yu.aicodeGeneration.ai.tools;

import cn.hutool.json.JSONObject;
import com.yu.aicodeGeneration.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件读取工具
 * 支持 AI 通过工具调用的方式读取文件内容
 *
 * 学习重点：
 * 当用户要求“在已有项目上继续修改”时，模型需要先读取旧文件内容，再决定怎么改。
 * 这个工具为模型提供读取能力，但读取范围仍限制在当前 app 的生成目录内。
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool {

    @Tool("读取指定路径的文件内容")
    public String readFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            // 模型传入的是相对路径或绝对路径，先统一转成 Path。
            Path path = Paths.get(relativeFilePath);
            // 相对路径统一解析到 vue_project_appId 目录，避免读取项目外文件。
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            // 读取前检查文件存在且确实是普通文件，目录不能当文件读。
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            // 把文件内容返回给模型，模型会基于内容继续修改。
            return Files.readString(path);
        } catch (IOException e) {
            // 出错时把错误信息返回给模型，而不是抛异常中断整个生成流程。
            String errorMessage = "读取文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 读取工具的执行结果不展示文件全文，避免聊天记录太长，只展示读了哪个文件。
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
