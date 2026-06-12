package com.yu.aicodeGeneration.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.model.enums.ImageCategoryEnum;
import com.yu.aicodeGeneration.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mermaid 架构图生成工具
 * 学习提示：该工具把 Mermaid 文本渲染成 SVG 文件，再上传到 COS，最后返回可访问图片地址。
 */
@Slf4j
@Component
public class MermaidDiagramTool {

    // 负责把本地生成的 SVG 上传到对象存储。
    @Resource
    private CosManager cosManager;
    
    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateMermaidDiagram(@P("Mermaid 图表代码") String mermaidCode,
                                                      @P("架构图描述") String description) {
        // Mermaid 代码为空时没有可渲染内容，直接返回空列表。
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        try {
            // 转换为 SVG 图片：依赖本机/容器中可执行的 mermaid-cli(mmdc)。
            File diagramFile = convertMermaidToSvg(mermaidCode);
            // 上传到 COS：用随机目录隔离不同生成任务，避免文件名冲突。
            String keyName = String.format("/mermaid/%s/%s",
                    RandomUtil.randomString(5), diagramFile.getName());
            String cosUrl = cosManager.uploadFile(keyName, diagramFile);
            // 清理临时文件：上传成功或失败后，本地 SVG 都不需要长期保留。
            FileUtil.del(diagramFile);
            if (StrUtil.isNotBlank(cosUrl)) {
                // 上传成功后包装成架构图类型资源，供 prompt 增强节点使用。
                return Collections.singletonList(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(cosUrl)
                        .build());
            }
        } catch (Exception e) {
            // 生成失败时返回空列表，工作流可继续使用其他素材。
            log.error("生成架构图失败: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * 将Mermaid代码转换为SVG图片
     */
    private File convertMermaidToSvg(String mermaidCode) {
        // 创建临时输入文件：mmdc 通过 -i 参数读取 .mmd 文件。
        File tempInputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, tempInputFile);
        // 创建临时输出文件：mmdc 会把渲染结果写到这个 SVG 路径。
        File tempOutputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        // 根据操作系统选择命令，Windows 下通常是 mmdc.cmd。
        String command = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
        // 构建命令：透明背景更适合嵌入到不同页面背景中。
        String cmdLine = String.format("%s -i %s -o %s -b transparent",
                command,
                tempInputFile.getAbsolutePath(),
                tempOutputFile.getAbsolutePath()
        );
        // 执行命令并等待完成；如果 mmdc 未安装或 Mermaid 语法错误，后续文件检查会失败。
        RuntimeUtil.execForStr(cmdLine);
        // 检查输出文件，确保 CLI 真的生成了非空 SVG。
        if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
        }
        // 清理输入文件，保留输出文件供上传使用。
        FileUtil.del(tempInputFile);
        return tempOutputFile;
    }
}
