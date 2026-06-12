package com.yu.aicodeGeneration.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yu.aicodeGeneration.ai.guardrail.PromptSafetyInputGuardrail;
import com.yu.aicodeGeneration.ai.tools.*;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import com.yu.aicodeGeneration.service.ChatHistoryService;
import com.yu.aicodeGeneration.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 服务创建工厂。
 *
 * 学习重点：
 * 1. LangChain4j 的 AiServices 会根据接口和注解动态生成 AI 服务代理对象。
 * 2. 每个 appId 需要独立对话记忆，否则不同应用之间的上下文会串。
 * 3. Vue 工程生成需要工具调用，所以和 HTML / 多文件生成使用不同配置。
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    // 普通聊天模型：用于非流式结构化生成，也作为 AiServices 的基础 chatModel。
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    // Redis 聊天记忆存储：把 LangChain4j 的 memory 持久化到 Redis，避免服务重启就丢上下文。
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    // 对话历史业务服务：从数据库加载历史消息，补充到 LangChain4j 的记忆窗口里。
    @Resource
    private ChatHistoryService chatHistoryService;

    // 工具管理器：收集文件读写、目录读取、退出等工具，供 Vue 工程模式下的模型调用。
    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            // 最多缓存 1000 个 appId + 类型 组合，防止服务实例无限增长。
            .maximumSize(1000)
            // 写入 30 分钟后过期：长时间不用的服务实例会自动释放。
            .expireAfterWrite(Duration.ofMinutes(30))
            // 访问后 10 分钟过期：活跃应用会继续保留，不活跃应用会被清理。
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                // 只打 debug 日志，避免缓存正常淘汰时污染业务日志。
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（为了兼容老逻辑）
     *
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        // 老代码默认走 HTML 模式，保留这个重载可以减少历史调用方改动。
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 缓存键同时包含 appId 和生成类型。
        // 同一个应用如果生成类型不同，需要不同工具配置和 prompt，因此不能只按 appId 缓存。
        String cacheKey = buildCacheKey(appId, codeGenType);
        // Caffeine 的 get(key, mappingFunction)：缓存命中直接返回，未命中才创建新服务实例。
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆。
        // id(appId) 决定 Redis 里这段记忆属于哪个应用，不同应用之间互不影响。
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                // Redis 作为 memory store，负责持久化 LangChain4j 的消息窗口。
                .chatMemoryStore(redisChatMemoryStore)
                // 最多保留 20 条消息，控制上下文长度和 token 成本。
                .maxMessages(20)
                .build();
        // 从数据库中加载对话历史到记忆中。
        // 数据库是业务事实来源，Redis memory 是模型调用时的上下文缓存。
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenType) {
            // Vue 项目生成：需要模型能多步思考并调用文件工具，所以使用推理流式模型 + tools。
            case VUE_PROJECT -> {
                // 使用 prototype 模式的 StreamingChatModel 解决并发问题。
                // 流式模型对象通常有请求状态，不建议多个并发请求共享同一个实例。
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                // 使用 AiServices 构建器创建 AiCodeGeneratorService 服务实例
                yield AiServices.builder(AiCodeGeneratorService.class)
                        // chatModel 用于非流式能力或内部辅助调用。
                        .chatModel(chatModel)
                        // streamingChatModel 用于 TokenStream 流式输出。
                        .streamingChatModel(reasoningStreamingChatModel)
                        // chatMemoryProvider 按 memoryId 返回记忆。
                        // generateVueProjectCodeStream 方法上有 @MemoryId appId，因此这里能拿到对应记忆。
                        .chatMemoryProvider(memoryId -> chatMemory)
                        // 注册所有文件操作工具，模型可以通过工具直接创建/读取/修改项目文件。
                        .tools(toolManager.getAllTools())
                        // 处理工具调用幻觉问题。
                        // 如果模型调用了不存在的工具，不让请求直接崩掉，而是返回一条工具错误消息给模型。
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name())
                        )
                        // 最多连续调用 20 次工具，防止模型陷入无限工具调用循环。
                        .maxSequentialToolsInvocations(20)
                        // 输入护轨：在请求模型前检查用户 prompt 是否包含危险意图。
                        .inputGuardrails(new PromptSafetyInputGuardrail())
//                        .outputGuardrails(new RetryOutputGuardrail()) // 添加输出护轨，为了流式输出，这里不使用
                        .build();
            }
            // HTML 和多文件生成：模型只需要返回代码文本，不需要文件工具。
            case HTML, MULTI_FILE -> {
                // 同样使用 prototype 流式模型，避免并发请求共享流式状态。
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        // 普通模型负责结构化/非流式调用。
                        .chatModel(chatModel)
                        // 流式模型负责 Flux<String> 输出。
                        .streamingChatModel(openAiStreamingChatModel)
                        // HTML / MULTI_FILE 使用固定 chatMemory 即可，不需要根据 @MemoryId 动态选择。
                        .chatMemory(chatMemory)
                        // 输入护轨用于拦截明显危险或不允许的生成请求。
                        .inputGuardrails(new PromptSafetyInputGuardrail())
//                        .outputGuardrails(new RetryOutputGuardrail()) // 添加输出护轨，为了流式输出，这里不使用
                        .build();
            }
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    /**
     * 创建 AI 代码生成器服务
     *
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        // 注册一个默认 Bean，方便其他地方按类型注入 AiCodeGeneratorService。
        // 真实业务中更常用 getAiCodeGeneratorService(appId, type) 获取带应用上下文的实例。
        return getAiCodeGeneratorService(0);
    }

    /**
     * 构造缓存键
     *
     * @param appId
     * @param codeGenType
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        // 缓存键格式：应用ID_生成类型，例如 1001_vue_project。
        return appId + "_" + codeGenType.getValue();
    }
}
