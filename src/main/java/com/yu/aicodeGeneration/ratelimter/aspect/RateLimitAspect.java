package com.yu.aicodeGeneration.ratelimter.aspect;

import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.ratelimter.annotation.RateLimit;
import com.yu.aicodeGeneration.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 限流切面核心逻辑
 *
 * 学习重点：
 * 1. @RateLimit 标在哪个接口上，这个切面就会在接口执行前先检查令牌。
 * 2. Redisson 的 RRateLimiter 让限流在多实例部署时仍然共享状态。
 * 3. 支持 API、USER、IP 三种维度，适合不同接口按不同粒度限流。
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    // Redisson 客户端：底层连接 Redis，实现分布式限流器。
    @Resource
    private RedissonClient redissonClient;

    // 用户服务：USER 级限流需要拿当前登录用户 ID。
    @Resource
    private UserService userService;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        // 根据注解配置和当前请求信息生成限流 key。
        String key = generateRateLimitKey(point, rateLimit);
        // 使用 Redisson 的分布式限流器。
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 1 小时后过期，避免 Redis 中长期保留已经不用的限流 key。
        rateLimiter.expire(Duration.ofHours(1));
        // 设置限流器参数：每个时间窗口允许的请求数和时间窗口。
        // trySetRate 只会在首次设置时生效，已有配置不会被频繁覆盖。
        rateLimiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);
        // 尝试获取一个令牌，如果获取失败则说明超过频率限制。
        if (!rateLimiter.tryAcquire(1)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    /**
     * 生成限流key
     *
     * @param point
     * @param rateLimit
     * @return
     */
    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit) {
        // 所有限流 key 都用 rate_limit 前缀，方便 Redis 中排查。
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("rate_limit:");
        // 添加自定义前缀：注解里可以指定 key，用于区分同一维度下的不同业务。
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key()).append(":");
        }
        // 根据限流类型生成不同的 key。
        switch (rateLimit.limitType()) {
            case API:
                // 接口级别：按 Controller 方法限流，所有用户共享同一个桶。
                MethodSignature signature = (MethodSignature) point.getSignature();
                Method method = signature.getMethod();
                keyBuilder.append("api:").append(method.getDeclaringClass().getSimpleName())
                        .append(".").append(method.getName());
                break;
            case USER:
                // 用户级别：按登录用户 ID 限流，同一用户的请求进入同一个桶。
                try {
                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null) {
                        HttpServletRequest request = attributes.getRequest();
                        User loginUser = userService.getLoginUser(request);
                        keyBuilder.append("user:").append(loginUser.getId());
                    } else {
                        // 无法获取请求上下文时，退化为 IP 限流。
                        keyBuilder.append("ip:").append(getClientIP());
                    }
                } catch (BusinessException e) {
                    // 未登录用户没有 userId，退化为 IP 限流。
                    keyBuilder.append("ip:").append(getClientIP());
                }
                break;
            case IP:
                // IP 级别：适合未登录接口或防止单个来源刷接口。
                keyBuilder.append("ip:").append(getClientIP());
                break;
            default:
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的限流类型");
        }
        return keyBuilder.toString();
    }

    /**
     * 获取客户端IP
     *
     * @return
     */
    private String getClientIP() {
        // RequestContextHolder 可以在切面里拿到当前请求上下文。
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        // X-Forwarded-For 通常由代理服务器添加，可能包含多个 IP。
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // X-Real-IP 也是常见代理头。
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 如果没有代理头，退回 Servlet 容器看到的远端地址。
            ip = request.getRemoteAddr();
        }
        // 处理多级代理的情况，X-Forwarded-For 第一个通常是原始客户端 IP。
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
