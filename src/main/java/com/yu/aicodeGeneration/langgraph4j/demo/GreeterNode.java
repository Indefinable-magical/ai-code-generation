package com.yu.aicodeGeneration.langgraph4j.demo;

import org.bsc.langgraph4j.action.NodeAction;
import java.util.List;
import java.util.Map;

// GreeterNode：示例里的第一个节点，负责向状态中追加一条问候消息。
class GreeterNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) {
        // 读取当前状态中的 messages，便于观察节点执行前状态是什么。
        System.out.println("GreeterNode executing. Current messages: " + state.messages());
        // 返回 Map 表示对状态的增量更新；SimpleState 的 schema 会把这条消息追加进列表。
        return Map.of(SimpleState.MESSAGES_KEY, "Hello from GreeterNode!");
    }
}

// ResponderNode：示例里的第二个节点，根据 GreeterNode 是否已经写入问候来决定回复内容。
class ResponderNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) {
        System.out.println("ResponderNode executing. Current messages: " + state.messages());
        // 取出当前消息列表，用于判断前一个节点是否已经成功执行。
        List<String> currentMessages = state.messages();
        if (currentMessages.contains("Hello from GreeterNode!")) {
            // 命中问候消息时，追加确认回复。
            return Map.of(SimpleState.MESSAGES_KEY, "Acknowledged greeting!");
        }
        // 没有问候消息时，追加兜底回复，说明状态不符合预期。
        return Map.of(SimpleState.MESSAGES_KEY, "No greeting found.");
    }
}
