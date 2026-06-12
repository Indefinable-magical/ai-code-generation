package com.yu.aicodeGeneration.core.parser;

import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 * 根据代码生成类型执行相应的解析逻辑
 *
 * 学习重点：
 * 模型输出通常是一整段文本，里面可能带 markdown 代码块。
 * Parser 的职责是把这段文本解析成后端更好处理的结构化对象。
 */
public class CodeParserExecutor {

    // HTML 解析器：从模型输出中提取 HTML 内容，组装成 HtmlCodeResult。
    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    // 多文件解析器：从模型输出中提取多个文件内容，组装成 MultiFileCodeResult。
    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析
     *
     * @param codeContent     代码内容
     * @param codeGenTypeEnum 代码生成类型
     * @return 解析结果（HtmlCodeResult 或 MultiFileCodeResult）
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum) {
        // 根据代码生成类型分派给不同解析器。
        // 这里返回 Object，是为了让上层统一处理两种不同结果类型。
        return switch (codeGenTypeEnum) {
            // HTML 模式只需要解析出单个 HTML 文件内容。
            case HTML -> htmlCodeParser.parseCode(codeContent);
            // 多文件模式需要解析出 HTML、CSS、JS 等多个文件内容。
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            // Vue 工程模式不走这里，因为它通过工具直接写文件。
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }
}
