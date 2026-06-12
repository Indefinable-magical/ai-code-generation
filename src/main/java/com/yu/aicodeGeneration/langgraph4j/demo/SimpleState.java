package com.yu.aicodeGeneration.langgraph4j.demo;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;

// SimpleState：LangGraph4j demo 使用的最小状态对象，继承 AgentState 后可通过 value 方法读取通道数据。
class SimpleState extends AgentState {
    // 状态字段名，节点读写 messages 时都使用这个 key。
    public static final String MESSAGES_KEY = "messages";

    // 定义状态 schema：MESSAGES_KEY 保存字符串列表，新消息会通过 appender 追加，而不是覆盖旧列表。
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new)
    );

    public SimpleState(Map<String, Object> initData) {
        // 把初始数据交给 AgentState，由父类根据 schema 管理状态通道。
        super(initData);
    }

    public List<String> messages() {
        // value("messages") 返回 Optional；为空时给出空列表，避免调用方处理 null。
        return this.<List<String>>value("messages")
                .orElse( List.of() );
    }
}
