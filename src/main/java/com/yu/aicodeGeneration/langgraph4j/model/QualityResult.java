package com.yu.aicodeGeneration.langgraph4j.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码质量检查结果
 * 学习提示：CodeQualityCheckNode 把模型审查结果保存成该对象，工作流条件边会根据 isValid 决定下一步。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityResult implements Serializable {
    
    @Serial
    // 序列化版本号，保证状态对象在图执行、日志或持久化场景中的兼容性。
    private static final long serialVersionUID = 1L;
    
    /**
     * 是否通过质检
     * true 表示可以进入构建/结束；false 表示需要回到代码生成节点重试。
     */
    private Boolean isValid;
    
    /**
     * 错误列表
     * 学习提示：记录必须修复的问题，比如语法错误、缺失文件、严重逻辑问题。
     */
    private List<String> errors;
    
    /**
     * 改进建议
     * 学习提示：记录非阻塞建议，比如样式优化、可维护性提升等。
     */
    private List<String> suggestions;
}
