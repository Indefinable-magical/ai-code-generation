package com.yu.aicodeGeneration.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存 key 生成工具类
 * 学习提示：这里统一把复杂对象转成稳定字符串后再做 MD5，避免不同业务自己拼接缓存 key 时出现格式不一致。
 *
 * @author yupi
 */
public class CacheKeyUtils {

    /**
     * 根据对象生成缓存key (JSON + MD5)
     *
     * @param obj 要生成key的对象
     * @return MD5哈希后的缓存key
     */
    public static String generateKey(Object obj) {
        // null 也要生成固定 key，否则调用方还要额外判断空值，缓存逻辑会变复杂。
        if (obj == null) {
            return DigestUtil.md5Hex("null");
        }
        // 先转 JSON：把对象结构、字段值转换成字符串，保证“内容相同”的对象能得到相同原文。
        String jsonStr = JSONUtil.toJsonStr(obj);
        // 再转 MD5：把可能很长的 JSON 压成短 key，适合放到 Redis / Caffeine 等缓存系统里。
        return DigestUtil.md5Hex(jsonStr);
    }
}
