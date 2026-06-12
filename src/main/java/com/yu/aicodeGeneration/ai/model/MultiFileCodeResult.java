package com.yu.aicodeGeneration.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 多文件代码结果
 * 学习提示：多文件模式把 HTML、CSS、JS 拆成独立字段，后续 saver 会分别写成不同文件。
 */
@Description("生成多个代码文件的结果")
@Data
public class MultiFileCodeResult {

    /**
     * HTML 代码
     * 学习提示：通常只保留页面结构，并通过外链方式引用生成的 CSS/JS。
     */
    @Description("HTML代码")
    private String htmlCode;

    /**
     * CSS 代码
     * 学习提示：页面样式会保存成独立 css 文件，便于用户学习和后续修改。
     */
    @Description("CSS代码")
    private String cssCode;

    /**
     * JS 代码
     * 学习提示：页面交互逻辑会保存成独立 js 文件，和结构、样式分层。
     */
    @Description("JS代码")
    private String jsCode;

    /**
     * 描述
     * 学习提示：对生成结果做自然语言说明，方便在聊天记录或日志中理解模型输出。
     */
    @Description("生成代码的描述")
    private String description;
}
