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

/**
 * 文件删除工具
 * 支持 AI 通过工具调用的方式删除文件
 *
 * 学习重点：
 * 删除操作风险最高，所以这个工具只允许删除普通文件，并额外保护 package.json、index.html 等关键文件。
 */
@Slf4j
@Component
public class FileDeleteTool extends BaseTool {

    @Resource
    private GenerationTaskManager generationTaskManager;

    @Tool("删除指定路径的文件")
    public String deleteFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            // 用户停止生成后，延迟工具调用不能再删除应用目录里的文件。
            if (generationTaskManager.isStopRequested(appId)) {
                return "应用生成已停止，跳过删除文件: " + relativeFilePath;
            }
            // 模型传入相对路径，先转换成 Path。
            Path path = Paths.get(relativeFilePath);
            // 相对路径限定在当前应用的 vue_project_appId 目录内。
            if (!path.isAbsolute()) {
                // 根据应用 ID 拼接生成项目目录名
                String projectDirName = "vue_project_" + appId;
                // 基于代码输出根目录和项目目录名，构建项目根路径
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                // 在项目根路径下解析相对文件路径，得到目标文件的完整路径
                path = projectRoot.resolve(relativeFilePath);
            }
            // 文件不存在时不算严重错误，告诉模型无需删除即可。
            if (!Files.exists(path)) {
                return "警告：文件不存在，无需删除 - " + relativeFilePath;
            }
            // 只允许删除普通文件，不允许删除目录，避免误删整个项目。
            if (!Files.isRegularFile(path)) {
                return "错误：指定路径不是文件，无法删除 - " + relativeFilePath;
            }
            // 安全检查：避免删除重要文件。
            String fileName = path.getFileName().toString();
            if (isImportantFile(fileName)) {
                return "错误：不允许删除重要文件 - " + fileName;
            }
            // 真正执行删除。
            Files.delete(path);
            log.info("成功删除文件: {}", path.toAbsolutePath());
            return "文件删除成功: " + relativeFilePath;
        } catch (IOException e) {
            // 删除失败时返回错误文本给模型，模型可以选择停止或换一种修改方式。
            String errorMessage = "删除文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 判断是否是重要文件，不允许删除
     */
    private boolean isImportantFile(String fileName) {
        // 这些文件对 Vue 项目启动和构建很关键，删除后项目可能无法运行。
        String[] importantFiles = {
                "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
                "vite.config.js", "vite.config.ts", "vue.config.js",
                "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json",
                "index.html", "main.js", "main.ts", "App.vue", ".gitignore", "README.md"
        };
        // 忽略大小写比较，避免模型用不同大小写绕过保护。
        for (String important : importantFiles) {
            if (important.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getToolName() {
        return "deleteFile";
    }

    @Override
    public String getDisplayName() {
        return "删除文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 删除结果只需要展示删除了哪个文件。
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format(" [工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
