package com.yu.aicodeGeneration.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 告诉 AI 要退出的工具
 *
 * 学习重点：
 * 工具调用模型有时会陷入“继续调用工具”的循环。
 * 提供 exit 工具，相当于给模型一个明确的结束动作，让它知道可以输出最终回复。
 */
@Slf4j
@Component
public class  ExitTool extends BaseTool {

    @Override
    public String getToolName() {
        return "exit";
    }

    @Override
    public String getDisplayName() {
        return "退出工具调用";
    }

    /**
     * 退出工具调用
     * 当任务完成或无需继续使用工具时调用此方法
     *
     * @return 退出确认信息
     */
    @Tool("当任务已完成或无需继续调用工具时，使用此工具退出操作，防止循环")
    public String exit() {
        // 这里不做真实业务操作，只返回提示文本给模型。
        log.info("AI 请求退出工具调用");
        return "不要继续调用工具，可以输出最终结果了";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 展示给前端/历史记录的结束标记。
        return "\n\n[执行结束]\n\n";
    }
}
