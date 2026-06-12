package com.yu.aicodeGeneration.model.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新应用请求
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

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
