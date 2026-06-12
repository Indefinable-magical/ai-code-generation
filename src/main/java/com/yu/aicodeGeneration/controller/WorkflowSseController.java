package com.yu.aicodeGeneration.controller;

import com.yu.aicodeGeneration.langgraph4j.CodeGenWorkflow;
import com.yu.aicodeGeneration.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 工作流 SSE 控制器
 * 演示 LangGraph4j 工作流的流式输出功能
 *
 * 学习重点：
 * 这是工作流 demo/测试入口，不是前端主业务生成入口。
 * 主业务生成入口在 AppController.chatToGenCode。
 */
@RestController
@RequestMapping("/workflow")
@Slf4j
public class WorkflowSseController {

    /**
     * 同步执行工作流
     */
    @PostMapping("/execute")
    public WorkflowContext executeWorkflow(@RequestParam String prompt) {
        log.info("收到同步工作流执行请求: {}", prompt);
        // 同步执行会等待整个工作流完成后，一次性返回最终上下文。
        return new CodeGenWorkflow().executeWorkflow(prompt);
    }

    /**
     * Flux 流式执行工作流
     */
    // 定义 GET 接口 /execute-flux，并指定响应类型为 text/event-stream，用于通过 SSE 流式返回数据
    @GetMapping(value = "/execute-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeWorkflowWithFlux(@RequestParam String prompt) {
        log.info("收到 Flux 工作流执行请求: {}", prompt);
        // WebFlux 风格的流式输出，适合直接返回 Flux<String>。
        return new CodeGenWorkflow().executeWorkflowWithFlux(prompt);
    }

    /**
     * SSE 流式执行工作流
     */
    @GetMapping(value = "/execute-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflowWithSse(@RequestParam String prompt) {
        log.info("收到 SSE 工作流执行请求: {}", prompt);
        // Spring MVC 风格的 SSE 输出，手动通过 SseEmitter 发送事件。
        return new CodeGenWorkflow().executeWorkflowWithSse(prompt);
    }
}
