package com.yu.aicodeGeneration.ai;

import com.yu.aicodeGeneration.ai.model.HtmlCodeResult;
import com.yu.aicodeGeneration.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI 代码生成服务接口。
 *
 * 学习重点：
 * 1. 这个接口没有手写实现类，实现是 LangChain4j 的 AiServices 动态生成的。
 * 2. @SystemMessage(fromResource = "...") 会把 resources/prompt 下的系统提示词加载给模型。
 * 3. 返回类型不同，代表不同的模型调用方式：结构化对象、Flux 流、TokenStream 工具流。
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    // 非流式 HTML 生成：模型一次性返回 HtmlCodeResult，适合测试或不需要实时展示的场景。
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    // 非流式多文件生成：模型一次性返回 MultiFileCodeResult，里面包含多个文件内容。
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    // 流式 HTML 生成：模型边生成边返回字符串片段，前端可以通过 SSE 实时看到输出。
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    // 流式多文件生成：返回文本流，后端在流结束后再解析成多个文件并保存。
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    // Vue 工程生成：TokenStream 能监听普通文本、工具调用请求、工具执行结果等复杂事件。
    // @MemoryId 标记 appId 是对话记忆 ID，同一个应用的多轮修改会共享上下文。
    // @UserMessage 明确 userMessage 是用户输入内容，其余参数不进入用户消息正文。
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
}
