package com.yu.aicodeGeneration.ratelimter.enums;

// 中文注释：限流模块：基于注解和切面限制用户、IP 或接口维度的高频请求。
// 中文注释：RateLimitType 是该模块中的一个核心类/接口，阅读时可先关注公开方法和被注入的依赖。


/**
 * 阅读提示：
 * 该文件属于：限流枚举层：定义限流维度，例如按用户或 IP 统计。
 * 主要关注：重点关注 public 方法的入参、返回值和被哪些上层流程调用。
 * 阅读顺序建议：先看类上的注解和成员依赖，再看核心 public 方法的调用链。
 */
public enum RateLimitType {
    
    /**
     * 接口级别限流
     */
    API,
    
    /**
     * 用户级别限流
     */
    USER,
    
    /**
     * IP级别限流
     */
    IP
}