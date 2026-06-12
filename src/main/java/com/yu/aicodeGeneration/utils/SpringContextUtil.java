package com.yu.aicodeGeneration.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring上下文工具类
 * 用于在静态方法中获取Spring Bean
 * 学习提示：正常业务代码优先使用依赖注入；只有静态工具类或框架回调不方便注入时，才通过这里兜底取 Bean。
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    // 保存 Spring 容器引用，后续静态方法可以通过它访问容器中的 Bean。
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Spring 启动时会回调该方法，把当前 ApplicationContext 注入进来。
        SpringContextUtil.applicationContext = applicationContext;
    }

    /**
     * 获取Spring Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        // 按类型取 Bean，适合一个接口只有一个实现或实现类本身作为 Bean 的场景。
        return applicationContext.getBean(clazz);
    }

    /**
     * 获取Spring Bean
     */
    public static Object getBean(String name) {
        // 按 Bean 名称取对象，返回 Object，调用方需要自己做类型转换。
        return applicationContext.getBean(name);
    }

    /**
     * 根据名称和类型获取Spring Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        // 同时限定名称和类型，可以避免同类型多 Bean 时拿错对象。
        return applicationContext.getBean(name, clazz);
    }
}
