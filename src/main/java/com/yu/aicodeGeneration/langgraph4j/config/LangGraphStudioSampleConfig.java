package com.yu.aicodeGeneration.langgraph4j.config;

import com.yu.aicodeGeneration.langgraph4j.CodeGenWorkflow;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.context.annotation.Configuration;

/**
 * 可视化调试配置（实际上很难使用）
 * 学习提示：LangGraph Studio 需要拿到一份 stateGraph，才能把工作流节点和边可视化出来。
 */
@Configuration
public class LangGraphStudioSampleConfig extends AbstractLangGraphStudioConfig {

    // Studio 需要暴露的流程定义。
    final LangGraphFlow flow;

    public LangGraphStudioSampleConfig() throws GraphStateException {
        // 复用真实代码生成工作流的底层 stateGraph，而不是重新写一份示例图。
        var workflow = new CodeGenWorkflow().createWorkflow().stateGraph;
        // 定义 Studio 页面里展示的工作流标题和图结构。
        this.flow = LangGraphFlow.builder()
                .title("LangGraph Studio")
                .stateGraph(workflow)
                .build();
    }

    @Override
    public LangGraphFlow getFlow() {
        // Spring Boot Studio 集成会调用该方法获取需要展示的 LangGraph 流程。
        return this.flow;
    }
}
