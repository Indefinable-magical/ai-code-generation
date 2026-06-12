package com.yu.aicodeGeneration.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * HTML 代码结果
 * 学习提示：这个类是 LangChain4j 结构化输出的目标类型，模型返回会被解析成这里的字段。
 */
@Description("生成 HTML 代码文件的结果")
@Data
public class HtmlCodeResult {

    /**
     * HTML 代码
     * 学习提示：单文件模式下，CSS/JS 通常会内嵌在这一份 HTML 中。
     */
    @Description("HTML代码")
    private String htmlCode;

    /**
     * 描述
     * 学习提示：用于记录本次生成内容的摘要，方便保存、展示或调试生成结果。
     */
    @Description("生成代码的描述")
    private String description;
}
