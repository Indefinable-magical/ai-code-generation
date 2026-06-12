package com.yu.aicodeGeneration.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * 截图工具类
 * 学习提示：这个类用 Selenium 启动无头 Chrome，把线上页面渲染完成后截图，再压缩成更适合上传/展示的图片。
 */
@Slf4j
public class WebScreenshotUtils {

    // 复用一个全局 WebDriver，避免每次截图都重新下载/启动 ChromeDriver，提升截图性能。
    private static final WebDriver webDriver;

    // 全局静态初始化，避免重复初始化驱动程序：
    static {
        // 1600x900 是常见桌面尺寸，能让生成的应用封面尽量展示完整首屏。
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 退出时销毁
     */
    @PreDestroy
    public void destroy() {
        // 应用关闭时释放浏览器进程，避免后台残留 Chrome / chromedriver 进程。
        webDriver.quit();
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 要截图的网址
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        // 非空校验：无 URL 时直接返回 null，让上层服务决定是否保留原封面。
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页截图失败，url为空");
            return null;
        }
        // 创建临时目录：每次截图使用随机目录，避免并发截图时文件名冲突。
        try {
            String rootPath = System.getProperty("user.dir") + "/tmp/screenshots/" + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始图片保存路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页：Selenium 会让无头 Chrome 像真实浏览器一样请求并渲染页面。
            webDriver.get(webUrl);
            // 等待网页加载：至少等到 document.readyState=complete，再额外留时间给前端异步渲染。
            waitForPageLoad(webDriver);
            // 截图：ChromeDriver 实现了 TakesScreenshot，可以直接拿到 PNG 字节数组。
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);
            // 压缩图片：原始 PNG 往往较大，压成 JPG 后再上传能降低存储和网络成本。
            final String COMPRESS_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESS_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);
            // 删除原始图片：最终只保留压缩结果，减少 tmp 目录占用。
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            // 截图失败不抛出业务异常，避免封面生成影响主流程；调用方收到 null 后可降级处理。
            log.error("网页截图失败：{}", webUrl, e);
            return null;
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 自动管理 ChromeDriver：根据本机 Chrome 版本匹配驱动，减少手工安装成本。
            WebDriverManager.chromedriver().setup();
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            // 无头模式：服务端没有显示器，也可以在后台完成页面渲染。
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动：后续所有截图都复用这个 driver 实例。
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时：避免目标页面长时间无响应导致截图线程一直卡住。
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待：查找页面元素时最多等待 10 秒，虽然当前截图逻辑主要依赖整页加载。
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 保存图片到文件
     *
     * @param imageBytes
     * @param imagePath
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            // Hutool 封装了文件写入，直接把截图字节落盘。
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     *
     * @param originImagePath
     * @param compressedImagePath
     */
    private static void compressImage(String originImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）：0.3 在清晰度和体积之间做折中。
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            // ImgUtil.compress 会读取原图并输出压缩图，输出后缀决定了这里生成 jpg。
            ImgUtil.compress(
                    FileUtil.file(originImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败：{} -> {}", originImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     *
     * @param webDriver
     */
    private static void waitForPageLoad(WebDriver webDriver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            // 等待 document.readyState 为 complete：表示 HTML、同步资源等基础加载已完成。
            wait.until(driver -> ((JavascriptExecutor) driver)
                    .executeScript("return document.readyState").
                    equals("complete")
            );
            // 额外等待一段时间，确保 Vue/React 等前端异步接口、动画和图片有机会渲染完成。
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            // 等待失败时继续截图：有些页面 readyState 或异步资源不稳定，但仍可能已经渲染出可用画面。
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }
}
