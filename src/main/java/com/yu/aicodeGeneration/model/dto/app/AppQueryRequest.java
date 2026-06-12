package com.yu.aicodeGeneration.model.dto.app;

import com.yu.aicodeGeneration.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    private String codeGenType;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 应用可见范围：public/private
     */
    private String visibility;

    /**
     * 单个应用标签筛选
     */
    private String tag;

    /**
     * 多个应用标签筛选
     */
    private List<String> tags;

    /**
     * 创建用户id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
} 
