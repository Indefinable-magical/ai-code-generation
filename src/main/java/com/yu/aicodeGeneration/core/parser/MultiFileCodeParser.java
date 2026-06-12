package com.yu.aicodeGeneration.core.parser;

import com.yu.aicodeGeneration.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器（HTML + CSS + JS）
 *
 * 学习重点：
 * 多文件模式要求模型分别输出 html/css/js 代码块。
 * 解析器会按代码块语言类型拆分，最后交给保存器写成 index.html、style.css、script.js。
 *
 * @author yupi
 */
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    // 分别匹配 HTML、CSS、JS 代码块。
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    // JS 同时兼容 ```js 和 ```javascript 两种写法。
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        // 解析结果对象，包含 htmlCode/cssCode/jsCode 三个字段。
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取各类代码。
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
        // 设置 HTML 代码；HTML 是多文件应用能运行的最低要求。
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        // 设置 CSS 代码；没有 CSS 时可以为空，保存器会跳过空内容。
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        // 设置 JS 代码；没有 JS 时同样可以为空。
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 根据正则模式提取代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        // 根据传入的正则查找对应语言代码块。
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            // 返回捕获到的代码内容。
            return matcher.group(1);
        }
        return null;
    }
}
