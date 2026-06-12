package com.yu.aicodeGeneration.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 构建 Vue 项目
 *
 * 学习重点：
 * Vue 工程生成后只是源码，必须执行 npm install 和 npm run build 才能得到可部署的 dist。
 * 这个类把构建过程封装起来，供生成完成和部署时复用。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath
     */
    public void buildProjectAsync(String projectPath) {
        // 使用虚拟线程异步构建，避免调用方等待 npm install/build 的长耗时。
        // 创建并启动一个虚拟线程，线程名包含当前时间戳，便于区分和排查构建任务
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        // 在线程中复用同步构建逻辑，异步执行 Vue 项目构建
                        buildProject(projectPath);
                    } catch (Exception e) {
                        // 捕获并记录异步构建过程中的异常，避免线程异常退出后问题无日志可查
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        // 构建前先确认项目目录存在。
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在：{}", projectPath);
            return false;
        }
        // 检查是否有 package.json 文件。
        // 没有 package.json 就不是可构建的 Node/Vue 项目。
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            log.error("项目目录中没有 package.json 文件：{}", projectPath);
            return false;
        }
        log.info("开始构建 Vue 项目：{}", projectPath);
        // 执行 npm install，安装 package.json 中声明的依赖。
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败：{}", projectPath);
            return false;
        }
        // 执行 npm run build，生成 dist 静态产物。
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败：{}", projectPath);
            return false;
        }
        // 验证 dist 目录是否生成，构建成功但没有 dist 仍然不能部署。
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("构建完成但 dist 目录未生成：{}", projectPath);
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
        return true;
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        // Windows 下实际命令是 npm.cmd，Linux/macOS 下是 npm。
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        // build 脚本来自 package.json 的 scripts.build。
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }

    /**
     * 根据操作系统构造命令
     *
     * @param baseCommand
     * @return
     */
    private String buildCommand(String baseCommand) {
        // Windows 需要调用 npm.cmd，否则 Java Process 可能找不到 npm。
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 操作系统检测
     *
     * @return
     */
    private boolean isWindows() {
        // 根据系统名判断是否 Windows。
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            // RuntimeUtil.exec 会在指定工作目录启动子进程。
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    // 命令分割为数组，例如 "npm.cmd run build" -> ["npm.cmd","run","build"]。
                    command.split("\\s+")
            );
            // 等待进程完成，设置超时，避免 npm 卡死一直占用线程。
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            // 退出码 0 表示命令成功。
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                // 非 0 退出码表示构建脚本失败，例如依赖安装失败或代码编译错误。
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            // Java 启动命令失败、等待被中断等异常都会走到这里。
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

}
