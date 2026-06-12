package com.yu.aicodeGeneration.langgraph4j.demo;

import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.GraphStateException;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;

import java.util.Map;

public class SimpleGraphApp {

    public static void main(String[] args) throws GraphStateException {
        // 初始化两个最简单的节点：一个负责打招呼，一个负责根据消息回应。
        GreeterNode greeterNode = new GreeterNode();
        ResponderNode responderNode = new ResponderNode();

        // 定义图结构：StateGraph 需要状态 schema 和状态构造函数。
       var stateGraph = new StateGraph<>(SimpleState.SCHEMA, initData -> new SimpleState(initData))
            .addNode("greeter", node_async(greeterNode))
            .addNode("responder", node_async(responderNode))
            // 定义边：START -> greeter -> responder -> END。
            .addEdge(START, "greeter") // 从 greeter 节点开始。
            .addEdge("greeter", "responder")
            .addEdge("responder", END)   // responder 执行完后结束。
             ;
        // 编译图：编译后才能运行 stream。
        var compiledGraph = stateGraph.compile();

        // 打印 Mermaid 图，方便直观看到节点连接关系。
        GraphRepresentation graph = stateGraph.getGraph(GraphRepresentation.Type.MERMAID, "demo");
        System.out.println(graph.toString());

        // 运行图：初始 messages 写入一条文本，后续节点会继续追加消息。
        for (var item : compiledGraph.stream( Map.of( SimpleState.MESSAGES_KEY, "Let's, begin!" ) ) ) {
            System.out.println( item );
        }

    }
}
