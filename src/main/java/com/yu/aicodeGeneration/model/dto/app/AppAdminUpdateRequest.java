package com.yu.aicodeGeneration.model.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员更新应用请求
 */
@Data
public class AppAdminUpdateRequest implements Serializable {

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
     * 优先级
     */
    private Integer priority;

    /**
     * 应用可见范围：public/private
     */
    private String visibility;

    /**
     * 应用标签
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
} 
