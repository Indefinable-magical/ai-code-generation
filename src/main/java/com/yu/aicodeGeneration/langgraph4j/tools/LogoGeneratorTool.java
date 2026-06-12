package com.yu.aicodeGeneration.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logo 图片生成工具
 * 学习提示：这是提供给 AI 的 Logo 生成工具，底层调用 DashScope 文生图模型生成品牌标识。
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    // DashScope API Key，默认为空字符串，未配置时调用会失败并返回空列表。
    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    // 文生图模型名称，默认使用较快的 wan2.2-t2i-flash。
    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        // 返回结构化 Logo 图片资源，失败时保持空列表。
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词：明确禁止文字，避免模型生成不可控、不可读或侵权风险较高的文字 Logo。
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            // 构造 DashScope 文生图请求参数。
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();
            // 发起文生图调用。
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);
            // 解析返回结果中的图片 URL。
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isNotBlank(imageUrl)) {
                        // 包装成 LOGO 类型资源，供后续 prompt 增强使用。
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(imageUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            // Logo 生成失败时返回空列表，页面生成仍可继续。
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }
}
