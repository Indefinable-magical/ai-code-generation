package com.yu.aicodeGeneration.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 提示词增强工作节点
 * 学习提示：这个节点把“用户原始需求 + 可用图片资源”合成更完整的 prompt，提升后续代码生成质量。
 */
@Slf4j
public class PromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 读取上一节点写入的 WorkflowContext。
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            // 获取原始提示词和图片列表；imageList 是结构化资源，imageListStr 是旧版字符串资源。
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();
            // 构建增强后的提示词：先保留用户真实需求，再追加素材说明。
            StringBuilder enhancedPromptBuilder = new StringBuilder();
            enhancedPromptBuilder.append(originalPrompt);
            // 如果有图片资源，则添加图片信息；没有素材时保持原 prompt 不变。
            if (CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)) {
                enhancedPromptBuilder.append("\n\n## 可用素材资源\n");
                enhancedPromptBuilder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
                if (CollUtil.isNotEmpty(imageList)) {
                    // 结构化图片资源可以展示类别、描述、URL，后续模型更容易理解每张图的用途。
                    for (ImageResource image : imageList) {
                        enhancedPromptBuilder.append("- ")
                                .append(image.getCategory().getText())
                                .append("：")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                } else {
                    // 兼容老流程：如果只有字符串形式的图片列表，直接追加原文本。
                    enhancedPromptBuilder.append(imageListStr);
                }
            }
            String enhancedPrompt = enhancedPromptBuilder.toString();
            // 更新状态：后续 RouterNode 和 CodeGeneratorNode 都应该使用增强后的 prompt。
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}
