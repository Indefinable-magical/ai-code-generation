package com.yu.aicodeGeneration.controller;

import com.yu.aicodeGeneration.common.BaseResponse;
import com.yu.aicodeGeneration.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck() {
        // 健康检查只返回一个轻量级 ok，通常给负载均衡、部署平台或监控系统探活使用。
        return ResultUtils.success("ok");
    }
}
