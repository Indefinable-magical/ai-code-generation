package com.yu.aicodeGeneration.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * AI 模型监听器
 *
 * 学习重点：
 * LangChain4j 在模型请求开始、成功响应、失败时会回调这个监听器。
 * 这里把模型调用转换成 Micrometer 指标，最终可被 Prometheus / Grafana 采集展示。
 */
@Component
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录请求开始时间，后续 onResponse/onError 用它计算耗时。
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        // 从 ThreadLocal 监控上下文中获取业务维度信息。
        // AppServiceImpl.chatToGenCode 会在调用 AI 前写入 userId 和 appId。
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        String userId = monitorContext.getUserId();
        String appId = monitorContext.getAppId();
        // 把上下文存入 LangChain4j request attributes。
        // 因为请求和响应回调可能不在同一线程，不能只依赖 ThreadLocal。
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, monitorContext);
        // 获取模型名称，用于区分 deepseek-chat、deepseek-reasoner 等模型指标。
        String modelName = requestContext.chatRequest().modelName();
        // 记录请求开始指标。
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 从 attributes 中获取 onRequest 保存的监控信息。
        Map<Object, Object> attributes = responseContext.attributes();
        // 从监控上下文中获取 userId/appId。
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        String userId = context.getUserId();
        String appId = context.getAppId();
        // 获取模型名称。
        String modelName = responseContext.chatResponse().modelName();
        // 记录成功请求。
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success");
        // 记录响应时间。
        recordResponseTime(attributes, userId, appId, modelName);
        // 记录 Token 使用情况。
        recordTokenUsage(responseContext, userId, appId, modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 错误回调里优先从 ThreadLocal 获取上下文。
        MonitorContext context = MonitorContextHolder.getContext();
        String userId = context.getUserId();
        String appId = context.getAppId();
        // 获取模型名称和错误信息。
        String modelName = errorContext.chatRequest().modelName();
        String errorMessage = errorContext.error().getMessage();
        // 记录失败请求和具体错误。
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, appId, modelName, errorMessage);
        // 记录响应时间（即使是错误响应），便于观察失败前耗时。
        Map<Object, Object> attributes = errorContext.attributes();
        recordResponseTime(attributes, userId, appId, modelName);
    }

    /**
     * 记录响应时间
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        // 从 onRequest 存入的开始时间计算 Duration。
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        Duration responseTime = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
    }

    /**
     * 记录Token使用情况
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        // tokenUsage 由模型响应元数据提供，不是所有模型都会返回。
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            // 分别记录输入、输出和总 token，方便后续做成本分析。
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }
}
