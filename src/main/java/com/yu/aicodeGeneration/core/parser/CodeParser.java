package com.yu.aicodeGeneration.core.parser;

/**
 * 代码解析器策略接口
 * 学习提示：不同生成类型会有不同解析逻辑，统一实现该接口后可交给 CodeParserExecutor 按类型分发。
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     * 
     * @param codeContent 原始代码内容
     * @return 解析后的结果对象
     */
    T parseCode(String codeContent);
}
