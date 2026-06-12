package com.yu.aicodeGeneration.controller;

import com.yu.aicodeGeneration.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

/**
 * 静态资源访问
 *
 * 学习重点：
 * AI 生成的应用代码会落到 tmp/code_output 下。
 * 前端预览 iframe 不是直接读本地文件，而是通过这个 Controller 把文件作为 HTTP 静态资源返回。
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 应用生成根目录（用于浏览），目录名通常是 html_appId、multi_file_appId、vue_project_appId。
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径。
            // 例如 /static/html_1/style.css 会提取出 /style.css。
            // 从请求属性中获取当前请求在 HandlerMapping 中匹配到的完整资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            // 去掉路径前缀 "/static/{deployKey}"，得到实际需要访问的静态资源相对路径
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的 URL。
            // 这样浏览器解析相对路径时更符合静态站点习惯。
            if (resourcePath.isEmpty()) {
                // 创建 HTTP 响应头对象
                HttpHeaders headers = new HttpHeaders();
                // 设置 Location 响应头，将请求重定向到当前 URI 末尾带 "/" 的地址
                headers.add("Location", request.getRequestURI() + "/");
                // 返回 301 永久重定向响应，引导客户端访问带斜杠的目录路径
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 访问目录根路径时默认返回 index.html。
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径。
            // deployKey 在预览场景下其实是生成目录名，例如 html_1。
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在，不存在返回 404。
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源，并根据扩展名设置 Content-Type。
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        // HTML/CSS/JS 显式加 UTF-8，保证中文内容在浏览器中不乱码。
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        // 图片不需要 charset。
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        // 其他未知类型使用二进制流。
        return "application/octet-stream";
    }
}
