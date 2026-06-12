package com.yu.aicodeGeneration.core.saver;

import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.ai.model.MultiFileCodeResult;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;

/**
 * 多文件代码保存器
 *
 * 学习重点：
 * 这个类对应 MULTI_FILE 模式，把解析后的 HTML/CSS/JS 分别写成三个静态文件。
 * 目录结构仍由父类统一创建，子类只关心文件名和文件内容。
 *
 * @author yupi
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        // 告诉父类目录名前缀使用 multi_file，例如 multi_file_123。
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        // 保存 HTML 文件：浏览器访问目录时会优先加载 index.html。
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        // 保存 CSS 文件：如果内容为空，父类 writeToFile 会自动跳过。
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        // 保存 JavaScript 文件：如果内容为空，也不会生成无意义文件。
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        // 先执行父类通用校验：result 不能为空。
        super.validateInput(result);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空。
        // 没有 HTML，静态页面就没有入口文件。
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}
