package com.yu.aicodeGeneration.core.saver;

import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.ai.model.HtmlCodeResult;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;

/**
 * HTML代码文件保存器
 *
 * 学习重点：
 * 这个类只负责 HTML 单文件模式。
 * 父类 CodeFileSaverTemplate 已经处理了校验、创建目录、返回目录，这里只实现“写哪些文件”。
 *
 * @author yupi
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        // 告诉父类目录名前缀使用 html，例如 html_123。
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        // HTML 单文件模式只写 index.html。
        // 静态预览访问目录时，浏览器默认会加载 index.html。
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    @Override
    protected void validateInput(HtmlCodeResult result) {
        // 先执行父类通用校验：result 不能为空。
        super.validateInput(result);
        // HTML 代码不能为空，否则保存出 index.html 也无法预览。
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空");
        }
    }
}
