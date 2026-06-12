package com.yu.aicodeGeneration.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 停止应用生成请求
 *
 * 用独立 DTO 承载 appId，避免复用 DeleteRequest 这类语义不匹配的请求对象。
 */
@Data
public class AppStopGenerationRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}
