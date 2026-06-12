package com.yu.aicodeGeneration.langgraph4j.model;

import com.yu.aicodeGeneration.langgraph4j.model.enums.ImageCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片资源对象
 * 学习提示：所有图片工具最终都会归一化成 ImageResource，方便 prompt 增强节点统一读取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResource implements Serializable {

    /**
     * 图片类别
     * 学习提示：区分内容图、Logo、插画、架构图，后续组装 prompt 时可以按类别描述用途。
     */
    private ImageCategoryEnum category;

    /**
     * 图片描述
     * 学习提示：给 AI 说明这张图的语义，不只是一个 URL。
     */
    private String description;

    /**
     * 图片地址
     * 学习提示：可以是外部图片链接，也可以是生成工具返回的可访问资源地址。
     */
    private String url;

    @Serial
    // 序列化版本号，工作流状态可能被保存或跨线程传递，显式声明可避免默认值变化。
    private static final long serialVersionUID = 1L;
}
