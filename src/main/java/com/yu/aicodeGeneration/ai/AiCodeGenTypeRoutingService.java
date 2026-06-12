package com.yu.aicodeGeneration.ai;

import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型智能路由服务
 * 使用结构化输出直接返回枚举类型
 *
 * 学习重点：
 * 这个接口也是由 LangChain4j 动态实现的。
 * 它只做一件事：读用户最初的需求，判断应该生成 HTML、多文件原生页面，还是 Vue 工程。
 *
 * @author yupi
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    // 系统提示词会要求模型在 CodeGenTypeEnum 的枚举范围内选择，避免返回随意文本。
    // 返回值直接是枚举，LangChain4j 会尝试把模型输出映射成 CodeGenTypeEnum。
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
