package com.yu.aicodeGeneration.core.parser;

import com.yu.aicodeGeneration.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器
 *
 * 学习重点：
 * AI 可能返回 ```html ... ``` 代码块，也可能直接返回完整 HTML。
 * 这个解析器优先提取 markdown 代码块，提取不到时把整段内容当作 HTML。
 *
 * @author yupi
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    // 匹配 markdown 中的 html 代码块；[\\s\\S]*? 用于跨行非贪婪匹配。
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        // 解析结果对象，后续会被 HtmlCodeFileSaverTemplate 保存成 index.html。
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码。
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            // 如果提取到代码块，就去掉首尾空白后保存。
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 如果没有找到代码块，将整个内容作为 HTML，兼容模型不包 markdown 的情况。
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    /**
     * 提取 HTML 代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private String extractHtmlCode(String content) {
        // 用正则查找第一个 html 代码块。
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            // group(1) 是括号中捕获的代码内容，不包含 ```html 和 ```。
            return matcher.group(1);
        }
        return null;
    }
}
