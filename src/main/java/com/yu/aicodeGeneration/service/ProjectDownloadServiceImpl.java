package com.yu.aicodeGeneration.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.exception.ThrowUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 需要过滤的文件和目录名称
     * 学习提示：下载给用户的是源码项目，不应该带 node_modules、构建产物、IDE 配置、环境变量等无关或敏感内容。
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     * 学习提示：日志、临时文件、缓存文件通常体积大且无学习价值，打包时统一排除。
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );


    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 基础校验：下载接口直接操作文件系统，必须先保证路径和文件名有效。
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");
        File projectDir = new File(projectPath);
        // 确保传入路径真实存在，并且必须是目录，不能把单个文件当项目根目录压缩。
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.PARAMS_ERROR, "项目路径不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "项目路径不是一个目录");
        log.info("开始打包下载项目: {} -> {}.zip", projectPath, downloadFileName);
        // 设置 HTTP 响应头：告诉浏览器这是一个 zip 附件，需要以 downloadFileName.zip 作为下载文件名。
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.addHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        // 定义文件过滤器：ZipUtil 遍历目录时，每个文件/目录都会先经过 isPathAllowed 判断。
        FileFilter filter = file -> isPathAllowed(projectDir.toPath(), file.toPath());
        // 压缩：边压缩边写入 response 输出流，避免先生成临时 zip 再二次读取。
        try {
            // 使用 Hutool 的 ZipUtil 直接将过滤后的目录压缩到响应输出流
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, filter, projectDir);
            log.info("打包下载项目成功: {} -> {}.zip", projectPath, downloadFileName);
        } catch (IOException e) {
            log.error("打包下载项目失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "打包下载项目失败");
        }
    }

    /**
     * 校验路径是否允许包含在压缩包中
     *
     * @param projectRoot 项目根目录
     * @param fullPath    完整路径
     * @return 是否允许
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 获取相对路径：只检查项目内部路径片段，不依赖机器上的绝对路径前缀。
        Path relativePath = projectRoot.relativize(fullPath);
        // 检查路径中的每一部分是否符合要求；只要某一级目录命中忽略规则，整棵子树都会被排除。
        for (Path part : relativePath) {
            String partName = part.toString();
            // 检查是否在忽略名称列表中
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }
            // 检查是否以忽略扩展名结尾
            if (IGNORED_EXTENSIONS.stream().anyMatch(ext -> partName.toLowerCase().endsWith(ext))) {
                return false;
            }
        }
        // 所有路径片段都没有命中过滤规则，说明该文件/目录可以进入压缩包。
        return true;
    }
}
