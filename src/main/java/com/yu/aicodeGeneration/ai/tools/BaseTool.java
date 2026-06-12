package com.yu.aicodeGeneration.ai.tools;

import cn.hutool.json.JSONObject;

/**
 * 工具基类
 * 定义所有工具的通用接口
 *
 * 学习重点：
 * 这些工具会注册给 LangChain4j，模型在 Vue 工程生成时可以主动调用它们。
 * BaseTool 额外定义了展示文案方法，用于把工具调用过程转换成用户能看懂的聊天内容。
 */
public abstract class BaseTool {

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        // 工具“准备调用”时展示给前端。
        // 这里只展示工具名称，不展示参数，避免还没完整收到参数时输出半截内容。
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
