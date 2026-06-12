package com.yu.aicodeGeneration.manager;

import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 生成任务管理器
 */
@Component
public class GenerationTaskManager {

    // appId -> 当前生成任务。用 ConcurrentHashMap 是为了支持多个用户同时生成不同应用。
    private final ConcurrentHashMap<Long, GenerationTask> taskMap = new ConcurrentHashMap<>();

    // 已请求停止的应用集合。生成流结束后可能还有工具回调进来，这里用于拦截后续文件写入。
    private final Set<Long> stoppedAppIds = ConcurrentHashMap.newKeySet();

    /**
     * 开始生成任务
     *
     * @param appId 应用 id
     * @return 生成任务
     */
    public GenerationTask startGeneration(Long appId) {
        // 新一轮生成开始时，清理上一轮遗留的停止标记。
        stoppedAppIds.remove(appId);
        GenerationTask task = new GenerationTask();
        // 同一个应用同一时间只允许一个生成任务，避免多个 AI 流同时写同一份代码。
        GenerationTask oldTask = taskMap.putIfAbsent(appId, task);
        if (oldTask != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前应用正在生成中，请稍后再试");
        }
        return task;
    }

    /**
     * 请求停止生成任务
     *
     * @param appId 应用 id
     * @return 是否存在正在生成的任务
     */
    public boolean requestStop(Long appId) {
        // 即使当前任务已经结束，也先记录停止标记，防止稍晚到达的工具回调继续写文件。
        stoppedAppIds.add(appId);
        GenerationTask task = taskMap.get(appId);
        if (task == null) {
            return false;
        }
        task.stop();
        return true;
    }

    /**
     * 生成任务停止信号
     *
     * @param task 生成任务
     * @return 停止信号
     */
    public Mono<Void> stopSignal(GenerationTask task) {
        // Reactor 的 takeUntilOther 会订阅这个 Mono；一旦 emit empty，下游 SSE 流就会结束。
        return task.stopSignal.asMono();
    }

    /**
     * 完成生成任务
     *
     * @param appId 应用 id
     * @param task  生成任务
     */
    public void finishGeneration(Long appId, GenerationTask task) {
        // 只移除当前任务实例，避免误删同一 appId 后续新创建的任务。
        taskMap.remove(appId, task);
        if (!task.isStopped()) {
            // 正常完成的任务可以清理停止标记；手动停止的任务需要保留，用于拦截延迟回调。
            stoppedAppIds.remove(appId);
        }
    }

    /**
     * 判断应用是否已请求停止生成
     *
     * @param appId 应用 id
     * @return 是否已请求停止
     */
    public boolean isStopRequested(Long appId) {
        GenerationTask task = taskMap.get(appId);
        // 同时检查任务状态和停止集合，覆盖“任务仍在运行”和“任务已结束但回调未完全收尾”两种情况。
        return (task != null && task.isStopped()) || stoppedAppIds.contains(appId);
    }

    /**
     * 生成任务
     */
    public static class GenerationTask {

        // 空信号只关心“发生了停止”这件事，不需要携带额外数据。
        private final Sinks.Empty<Void> stopSignal = Sinks.empty();

        // 使用原子布尔值保证多线程重复点击停止时状态仍然一致。
        private final AtomicBoolean stopped = new AtomicBoolean(false);

        private void stop() {
            stopped.set(true);
            // tryEmitEmpty 是幂等友好的，重复停止时不会抛出异常影响主流程。
            stopSignal.tryEmitEmpty();
        }

        public boolean isStopped() {
            return stopped.get();
        }
    }
}
