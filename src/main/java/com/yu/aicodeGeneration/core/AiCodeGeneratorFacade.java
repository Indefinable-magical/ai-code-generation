package com.yu.aicodeGeneration.core;

import cn.hutool.json.JSONUtil;
import com.yu.aicodeGeneration.ai.AiCodeGeneratorService;
import com.yu.aicodeGeneration.ai.AiCodeGeneratorServiceFactory;
import com.yu.aicodeGeneration.ai.model.HtmlCodeResult;
import com.yu.aicodeGeneration.ai.model.MultiFileCodeResult;
import com.yu.aicodeGeneration.ai.model.message.AiResponseMessage;
import com.yu.aicodeGeneration.ai.model.message.ToolExecutedMessage;
import com.yu.aicodeGeneration.ai.model.message.ToolRequestMessage;
import com.yu.aicodeGeneration.constant.AppConstant;
import com.yu.aicodeGeneration.core.builder.VueProjectBuilder;
import com.yu.aicodeGeneration.core.parser.CodeParserExecutor;
import com.yu.aicodeGeneration.core.saver.CodeFileSaverExecutor;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成门面类，组合代码生成、流式输出、解析保存和项目构建功能。
 *
 * 学习重点：
 * 1. Facade（门面）把复杂子流程封装成一个统一入口，Service 层不用关心具体生成细节。
 * 2. HTML / MULTI_FILE 是“模型输出完整代码 -> 解析 -> 保存”的模式。
 * 3. VUE_PROJECT 是“模型通过工具直接写文件 -> 工具执行过程流式返回 -> 完成后构建”的模式。
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    // AI 服务工厂：按 appId 和代码生成类型创建/复用 LangChain4j 的 AiService 实例。
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    // Vue 项目构建器：Vue 工程生成完成后，需要构建出 dist 才能预览或部署。
    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // 生成类型决定后续调用哪个提示词、哪个返回结构、哪个解析保存器，因此不能为空。
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例。
        // 这里按 appId 绑定对话记忆，让同一个应用的多轮生成拥有上下文。
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // HTML 模式：AI 直接返回 HtmlCodeResult，通常只包含 index.html 内容。
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                // 解析后的结构交给保存执行器，保存到 html_appId 目录。
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                // 多文件模式：AI 返回多个文件片段，例如 html/css/js 分离。
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                // 保存执行器会根据文件类型写入对应文件。
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                // 非流式入口目前只支持 HTML 和多文件，Vue 工程依赖工具调用和流式过程。
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // 流式生成同样必须先确定类型，否则无法选择模型接口和后续处理器。
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 获取带有 app 专属记忆的 AI 服务实例。
        // 同一个 appId 的聊天会加载历史消息，从而支持“继续修改上一次生成的应用”。
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // HTML 流式接口会一段段吐出模型生成内容，前端可以实时看到回复。
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                // 流式返回给前端的同时，后端会在 complete 时解析并保存完整代码。
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                // 多文件流式接口同理，只是完整内容会被解析成多个文件。
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                // Vue 工程模式使用 TokenStream，因为需要监听工具调用请求和工具执行结果。
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                // TokenStream 不是 Reactor 类型，需要手动桥接成 Flux<String>。
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        // Flux.create 用于把回调式 API 转成响应式流。
        // TokenStream 通过多个回调通知：模型文本、工具调用、工具执行完成、整体完成、错误。
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        // 模型普通文本片段：包装成 AiResponseMessage，前端可按 type 区分展示。
                        // 检查订阅是否已取消，避免在客户端断开后继续处理数据
                        // 用户点击停止后，SSE 订阅会被取消，这里直接丢弃后续 token。
                        if (sink.isCancelled()) {
                            return;
                        }
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        // 模型准备调用工具时触发，例如要写文件、读目录、修改文件。
                        // 把工具请求也推给前端，用户能看到 AI 正在做什么操作。
                        // 已停止的生成不再把工具调用过程推给前端，避免界面继续滚动。
                        if (sink.isCancelled()) {
                            return;
                        }
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        // 工具执行结束后触发，例如文件已写入、目录已读取。
                        // 这类消息可以用于前端展示“工具执行结果”。
                        // 工具执行结果可能比停止信号晚到，停止后不再写入前端流。
                        if (sink.isCancelled()) {
                            return;
                        }
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 整体 AI 响应完成后，Vue 项目文件应该已经由工具写到磁盘。
                        // 此时同步执行构建，确保前端刷新预览时 dist/index.html 已经存在。
                        // 如果用户中途停止，跳过 Vue 构建，避免为半成品继续消耗资源。
                        if (sink.isCancelled()) {
                            return;
                        }
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        // complete 会触发上游 doFinally / 前端 done 流程。
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        // 错误向 Flux 下游传播，Controller/前端才能感知生成失败。
                        // 取消后的错误通常来自关闭连接或异步回调，忽略即可。
                        if (sink.isCancelled()) {
                            return;
                        }
                        error.printStackTrace();
                        sink.error(error);
                    })
                    // 注册完所有回调后必须 start，否则 TokenStream 不会真正开始请求模型。
                    .start();
        });
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // 字符串拼接器，用于当流式返回所有的代码之后，再保存代码。
        // 注意：这里既把 chunk 原样返回给前端，也把 chunk 收集起来供后端保存。
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // doOnNext 不改变流内容，只做副作用：收集模型输出片段。
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后，后端才拥有完整代码，此时再做解析和落盘。
            try {
                // completeCode 是模型本轮完整输出，通常包含 markdown 代码块或 JSON 化的代码结构。
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码。
                // CodeParserExecutor 会根据 codeGenType 分派到 HtmlCodeParser 或 MultiFileCodeParser。
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码。
                // CodeFileSaverExecutor 会根据 codeGenType 分派到对应保存模板。
                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                // 保存失败只记录日志，不在这里中断前端流，因为此时流已经完成。
                // 如果希望前端感知保存失败，可以在这里改造成业务错误事件。
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }
}
