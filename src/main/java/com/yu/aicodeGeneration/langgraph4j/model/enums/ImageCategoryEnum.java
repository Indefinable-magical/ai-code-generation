package com.yu.aicodeGeneration.langgraph4j.model.enums;

// 中文注释：LangGraph 数据模型：保存工作流中的图片资源、质量检查结果和规划信息。
// 中文注释：ImageCategoryEnum 是该模块中的一个核心类/接口，阅读时可先关注公开方法和被注入的依赖。


/**
 * 阅读提示：
 * 该文件属于：LangGraph4j 枚举模型：约束工作流里素材类别和分支判断使用的固定取值。
 * 主要关注：重点关注枚举值和数据库、前端展示、分支判断之间的映射。
 * 阅读顺序建议：先看类上的注解和成员依赖，再看核心 public 方法的调用链。
 */
import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 图片类型枚举
 * 学习提示：枚举值会写入 ImageResource.category，用于后续按素材类型聚合和描述。
 */
@Getter
public enum ImageCategoryEnum {

    // 内容图片：真实照片、产品图、背景图等。
    CONTENT("内容图片", "CONTENT"),
    // Logo 图片：品牌标识或应用图标。
    LOGO("LOGO图片", "LOGO"),
    // 插画图片：适合页面装饰、空状态、功能解释。
    ILLUSTRATION("插画图片", "ILLUSTRATION"),
    // 架构图片：Mermaid 等工具生成的流程图、架构图。
    ARCHITECTURE("架构图片", "ARCHITECTURE");


    // 中文展示文案。
    private final String text;

    // 稳定枚举值，适合存储、序列化和前端传参。
    private final String value;

    ImageCategoryEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static ImageCategoryEnum getEnumByValue(String value) {
        // 空值直接返回 null，避免后续 equals 空指针。
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历枚举，根据 value 字段反查对应枚举。
        for (ImageCategoryEnum anEnum : ImageCategoryEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        // 未匹配到说明传入值不属于当前定义的图片类别。
        return null;
    }
}
