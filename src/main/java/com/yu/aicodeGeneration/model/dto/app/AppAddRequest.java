package com.yu.aicodeGeneration.model.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 应用创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

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
