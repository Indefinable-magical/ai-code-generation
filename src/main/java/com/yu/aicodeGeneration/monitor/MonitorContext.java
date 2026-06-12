package com.yu.aicodeGeneration.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 监控上下文（需要传递的数据）
 *
 * 学习重点：
 * 这个对象只保存监控维度，不参与业务逻辑。
 * userId 和 appId 会作为指标标签写入 Micrometer，方便按用户/应用观察 AI 调用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorContext implements Serializable {

    // 当前发起 AI 调用的用户 ID。
    private String userId;

    // 当前被生成/修改的应用 ID。
    private String appId;

    @Serial
    private static final long serialVersionUID = 1L;
}
