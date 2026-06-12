package com.yu.aicodeGeneration.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.exception.ThrowUtils;
import com.yu.aicodeGeneration.manager.CosManager;
import com.yu.aicodeGeneration.service.ScreenshotService;
import com.yu.aicodeGeneration.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    // COS 管理器：负责把本地截图文件上传到腾讯云对象存储。
    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        // 参数校验：截图必须有可访问的网页 URL。
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "截图的网址不能为空");
        log.info("开始生成网页截图，URL：{}", webUrl);
        // 本地截图：WebScreenshotUtils 会启动浏览器打开网页并保存压缩后的图片。
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "生成网页截图失败");
        // 上传图片到 COS。
        try {
            // 上传成功后返回可访问的图片 URL。
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "上传截图到对象存储失败");
            log.info("截图上传成功，URL：{}", cosUrl);
            return cosUrl;
        } finally {
            // 无论上传成功还是失败，都清理本地临时截图，避免磁盘堆积。
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 上传截图到对象存储
     *
     * @param localScreenshotPath 本地截图路径
     * @return 对象存储访问URL，失败返回null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        // 本地路径为空时直接返回 null，由上层统一抛业务错误。
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        // 确认截图文件真的存在。
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键。
        // 文件名前缀用 UUID 避免不同应用截图重名覆盖。
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        // 交给 CosManager 上传，返回公网或自定义域名 URL。
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键
     * 格式：/screenshots/2025/07/31/filename.jpg
     */
    private String generateScreenshotKey(String fileName) {
        // 按日期分目录，方便对象存储中浏览和清理历史截图。
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        // 只在文件存在时删除，避免 Hutool 对不存在文件做无意义操作。
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            FileUtil.del(localFile);
            log.info("清理本地文件成功: {}", localFilePath);
        }
    }
}
