package com.yu.aicodeGeneration.core.saver;

import com.yu.aicodeGeneration.ai.model.HtmlCodeResult;
import com.yu.aicodeGeneration.ai.model.MultiFileCodeResult;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码文件保存执行器
 * 根据代码生成类型执行相应的保存逻辑
 *
 * 学习重点：
 * Parser 负责“把文本解析成对象”，Saver 负责“把对象写成真实文件”。
 * 这个执行器是分发层，具体保存细节在 HtmlCodeFileSaverTemplate 和 MultiFileCodeFileSaverTemplate。
 *
 * @author yupi
 */
public class CodeFileSaverExecutor {

    // HTML 保存器：把 HtmlCodeResult 写入 index.html。
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();

    // 多文件保存器：把 MultiFileCodeResult 写入多个独立文件。
    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /**
     * 执行代码保存
     *
     * @param codeResult  代码结果对象
     * @param codeGenType 代码生成类型
     * @param appId 应用 ID
     * @return 保存的目录
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        // 根据生成类型选择保存模板。
        // 这里需要强制类型转换，因为上游为了统一入口把解析结果声明成了 Object。
        return switch (codeGenType) {
            // HTML 结果只能交给 HTML 保存器。
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult, appId);
            // 多文件结果只能交给多文件保存器。
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult, appId);
            // Vue 工程模式由工具直接写文件，不走这个保存器。
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
