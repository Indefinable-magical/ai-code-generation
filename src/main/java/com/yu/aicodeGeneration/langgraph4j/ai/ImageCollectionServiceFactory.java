package com.yu.aicodeGeneration.langgraph4j.ai;

import com.yu.aicodeGeneration.langgraph4j.tools.ImageSearchTool;
import com.yu.aicodeGeneration.langgraph4j.tools.LogoGeneratorTool;
import com.yu.aicodeGeneration.langgraph4j.tools.MermaidDiagramTool;
import com.yu.aicodeGeneration.langgraph4j.tools.UndrawIllustrationTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集服务工厂
 * 学习提示：这里把多个图片工具挂到同一个 AI Service 上，模型可以按需求调用其中任意工具。
 */
@Slf4j
@Configuration
public class ImageCollectionServiceFactory {

    // 负责理解用户需求并决定调用哪个工具。
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    // 内容图片搜索工具。
    @Resource
    private ImageSearchTool imageSearchTool;

    // unDraw 插画搜索工具。
    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    // Mermaid 图生成工具。
    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    // Logo 生成工具。
    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    /**
     * 创建图片收集 AI 服务
     */
    @Bean
    public ImageCollectionService createImageCollectionService() {
        // AiServices 生成代理时会把 tools 注册给模型，模型输出 tool call 后由 LangChain4j 自动执行。
        return AiServices.builder(ImageCollectionService.class)
                .chatModel(chatModel)
                .tools(
                        imageSearchTool,
                        undrawIllustrationTool,
                        mermaidDiagramTool,
                        logoGeneratorTool
                )
                .build();
    }
}
