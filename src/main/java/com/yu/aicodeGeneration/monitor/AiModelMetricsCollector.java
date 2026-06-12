package com.yu.aicodeGeneration.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 指标收集器
 *
 * 学习重点：
 * Micrometer 是 Spring Boot 常用指标门面。
 * 这里创建 Counter 和 Timer，Actuator + Prometheus 会把它们暴露出去供 Grafana 展示。
 */
@Component
@Slf4j
public class AiModelMetricsCollector {

    // MeterRegistry 是 Micrometer 的指标注册中心，所有 Counter/Timer 都注册到这里。
    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免每次记录都重新 builder/register。
    // 注意：这些 key 带 userId/appId，维度过多时会造成指标基数偏高，生产要谨慎。
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();

    /**
     * 记录请求次数
     */
    public void recordRequest(String userId, String appId, String modelName, String status) {
        // 同一组标签复用同一个 Counter。
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, status);
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_requests_total")
                        .description("AI模型总请求次数")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("status", status)
                        .register(meterRegistry)
        );
        // 请求次数每发生一次加 1。
        counter.increment();
    }

    /**
     * 记录错误
     */
    public void recordError(String userId, String appId, String modelName, String errorMessage) {
        // 错误指标按错误消息区分，便于看具体哪类错误最多。
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, errorMessage);
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_errors_total")
                        .description("AI模型错误次数")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("error_message", errorMessage)
                        .register(meterRegistry)
        );
        // 每次错误加 1。
        counter.increment();
    }

    /**
     * 记录Token消耗
     */
    public void recordTokenUsage(String userId, String appId, String modelName,
                                 String tokenType, long tokenCount) {
        // tokenType 用于区分 input/output/total。
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, tokenType);
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_tokens_total")
                        .description("AI模型Token消耗总数")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("token_type", tokenType)
                        .register(meterRegistry)
        );
        // token 是累计值，每次按实际 token 数累加。
        counter.increment(tokenCount);
    }

    /**
     * 记录响应时间
     */
    public void recordResponseTime(String userId, String appId, String modelName, Duration duration) {
        // Timer 会记录耗时分布，Prometheus 可以看到 count/sum/max 等数据。
        String key = String.format("%s_%s_%s", userId, appId, modelName);
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                Timer.builder("ai_model_response_duration_seconds")
                        .description("AI模型响应时间")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .register(meterRegistry)
        );
        // 记录本次模型调用耗时。
        timer.record(duration);
    }
}
