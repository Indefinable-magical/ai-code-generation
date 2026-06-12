package com.yu.aicodeGeneration.ai.tools;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具管理器
 * 统一管理所有工具，提供根据名称获取工具的功能
 *
 * 学习重点：
 * Spring 会把所有 BaseTool 子类注入到 tools 数组。
 * ToolManager 再按工具英文名建立索引，方便流处理器根据模型返回的 toolName 找到具体工具。
 */
@Slf4j
@Component
public class ToolManager {

    /**
     * 工具名称到工具实例的映射
     */
    private final Map<String, BaseTool> toolMap = new HashMap<>();

    /**
     * 自动注入所有工具
     */
    @Resource
    private BaseTool[] tools;

    /**
     * 初始化工具映射
     */
    @PostConstruct
    public void initTools() {
        // @PostConstruct 在依赖注入完成后执行，此时 tools 数组已经包含所有工具 Bean。
        for (BaseTool tool : tools) {
            // getToolName 必须和 @Tool 方法名/模型返回的工具名保持一致。
            toolMap.put(tool.getToolName(), tool);
            log.info("注册工具: {} -> {}", tool.getToolName(), tool.getDisplayName());
        }
        log.info("工具管理器初始化完成，共注册 {} 个工具", toolMap.size());
    }


    /**
     * 根据工具名称获取工具实例
     *
     * @param toolName 工具英文名称
     * @return 工具实例
     */
    public BaseTool getTool(String toolName) {
        // 根据模型返回的工具名称找到对应工具，用来生成前端展示文案。
        return toolMap.get(toolName);
    }

    /**
     * 获取已注册的工具集合
     *
     * @return 工具实例集合
     */
    public BaseTool[] getAllTools() {
        // AiCodeGeneratorServiceFactory 会把这个数组注册给 LangChain4j。
        return tools;
    }
}
