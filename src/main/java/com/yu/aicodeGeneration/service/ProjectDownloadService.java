package com.yu.aicodeGeneration.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目下载服务。
 * 学习提示：生成应用后，用户需要把代码打包下载；这里把 ZIP 输出细节从 Controller 中抽出来。
 */
public interface ProjectDownloadService {

    /**
     * 下载项目为压缩包
     *
     * @param projectPath
     * @param downloadFileName
     * @param response
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
