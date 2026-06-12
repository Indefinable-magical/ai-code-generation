package com.yu.aicodeGeneration.monitor;

import lombok.extern.slf4j.Slf4j;

/**
 * 监控上下文持有者（同线程内共享）
 *
 * 学习重点：
 * 模型监听器需要知道当前请求属于哪个用户、哪个应用。
 * 这些业务信息不一定能从 LangChain4j 的回调里直接拿到，所以用 ThreadLocal 暂存。
 */
@Slf4j
public class MonitorContextHolder {

    // ThreadLocal 让同一个线程内的代码都能拿到当前请求的 MonitorContext。
    // 用完一定要 clear，避免线程复用导致数据串到下一个请求。
    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置监控上下文
     */
    public static void setContext(MonitorContext context) {
        // 在调用 AI 前设置上下文。
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取当前监控上下文
     */
    public static MonitorContext getContext() {
        // 在模型监听器里读取上下文。
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除监控上下文
     */
    public static void clearContext() {
        // 请求结束后清理，避免内存泄漏和上下文污染。
        CONTEXT_HOLDER.remove();
    }
}
