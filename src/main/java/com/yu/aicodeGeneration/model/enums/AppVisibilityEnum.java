package com.yu.aicodeGeneration.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum AppVisibilityEnum {

    /**
     * 公开应用：普通用户也可以在公开/精选列表和详情页看到。
     */
    PUBLIC("公开", "public"),

    /**
     * 私有应用：仅创建者和管理员可见。
     */
    PRIVATE("私有", "private");

    /**
     * 前端展示文案
     */
    private final String text;

    /**
     * 数据库存储值
     */
    private final String value;

    AppVisibilityEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值
     */
    public static AppVisibilityEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppVisibilityEnum anEnum : AppVisibilityEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
