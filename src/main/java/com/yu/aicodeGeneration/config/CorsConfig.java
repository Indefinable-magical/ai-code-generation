package com.yu.aicodeGeneration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域问题解决
 *
 * 学习重点：
 * 前端 Vite 开发服务器和后端 8123 端口不同源，所以浏览器会触发 CORS。
 * 这里允许跨域请求携带 Cookie，才能让登录态 session 正常传到后端。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求路径，包含 /api/user、/api/app、/api/static 等。
        registry.addMapping("/**")
                // 允许发送 Cookie：前端 axios/EventSource 都依赖 Cookie 传递登录态。
                .allowCredentials(true)
                // 放行哪些域名。
                // 注意：allowCredentials(true) 时不能直接 allowedOrigins("*")，所以使用 allowedOriginPatterns。
                .allowedOriginPatterns("*")
                // 允许常见 HTTP 方法。
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许前端携带任意请求头。
                .allowedHeaders("*")
                // 暴露响应头，方便前端读取下载文件名等信息。
                .exposedHeaders("*");
    }
}
