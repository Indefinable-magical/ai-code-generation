package com.yu.aicodeGeneration.ratelimter.annotation;

import com.yu.aicodeGeneration.ratelimter.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key前缀
     * 学习提示：最终 Redis key 会由这个前缀、限流类型、用户/IP/接口等信息拼出来。
     */
    String key() default "";

    /**
     * 每个时间窗口允许的请求数
     * 例如 rate=5、rateInterval=60 表示 60 秒最多允许 5 次。
     */
    int rate() default 10;

    /**
     * 时间窗口（秒）
     */
    int rateInterval() default 1;

    /**
     * 限流类型
     * USER 表示按登录用户限流，IP 表示按请求 IP 限流，具体拼 key 逻辑在 RateLimitAspect。
     */
    RateLimitType limitType() default RateLimitType.USER;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
