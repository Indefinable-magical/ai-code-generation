package com.yu.aicodeGeneration.langgraph4j.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yu.aicodeGeneration.langgraph4j.model.ImageResource;
import com.yu.aicodeGeneration.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片搜索工具（根据关键词搜索内容图片）
 * 学习提示：这是提供给 LangChain4j 的 @Tool，模型可以通过它调用 Pexels 搜索真实内容图片。
 */
@Slf4j
@Component
public class ImageSearchTool {

    // Pexels 图片搜索接口地址。
    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    // Pexels API Key，从配置文件注入。
    @Value("${pexels.api-key}")
    private String pexelsApiKey;

    @Tool("搜索内容相关的图片，用于网站内容展示")
    public List<ImageResource> searchContentImages(@P("搜索关键词") String query) {
        // 统一返回 ImageResource 列表，即使失败也返回空列表，方便调用方聚合。
        List<ImageResource> imageList = new ArrayList<>();
        // 每次最多取 12 张，避免 prompt 里塞入过多图片资源。
        int searchCount = 12;
        // 调用 API，注意释放资源；try-with-resources 会自动关闭 HttpResponse。
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", searchCount)
                .form("page", 1)
                .execute()) {
            if (response.isOk()) {
                // 解析 Pexels 返回的 JSON，photos 数组里包含图片和不同尺寸地址。
                JSONObject result = JSONUtil.parseObj(response.body());
                JSONArray photos = result.getJSONArray("photos");
                for (int i = 0; i < photos.size(); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    JSONObject src = photo.getJSONObject("src");
                    // 只取 medium 尺寸，体积和清晰度比较平衡，适合页面展示。
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .description(photo.getStr("alt", query))
                            .url(src.getStr("medium"))
                            .build());
                }
            }
        } catch (Exception e) {
            // 工具失败不抛出异常给模型，返回空列表让工作流自然降级。
            log.error("Pexels API 调用失败: {}", e.getMessage(), e);
        }
        return imageList;
    }
}
