package com.yu.aicodeGeneration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC Json 配置
 *
 * 学习重点：
 * Java 的 Long 最大值可能超过 JavaScript Number 的安全整数范围。
 * 这里把 Long 序列化成字符串，避免前端拿到大 id 后精度丢失。
 */
@JsonComponent
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // createXmlMapper(false) 表示创建普通 JSON ObjectMapper，不启用 XML 映射。
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // SimpleModule 可以注册自定义序列化规则。
        SimpleModule module = new SimpleModule();
        // 包装类型 Long 转字符串，例如 9007199254740993L 不会被 JS 四舍五入。
        module.addSerializer(Long.class, ToStringSerializer.instance);
        // 基本类型 long 也转字符串。
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // 注册模块后，Spring MVC 返回 JSON 时会自动应用这些序列化规则。
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
