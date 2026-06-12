package com.yu.aicodeGeneration.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片收集工具（插画图片）
 * 学习提示：该工具从 unDraw 搜索插画，适合给生成的网站补充说明性、装饰性视觉素材。
 */
@Slf4j
@Component
public class UndrawIllustrationTool {

    // unDraw 搜索接口模板，两个 %s 都会填入搜索关键词。
    private static final String UNDRAW_API_URL = "https://undraw.co/_next/data/mMWmJSt23qpgo8cLTD_pB/search/%s.json?term=%s";

    @Tool("搜索插画图片，用于网站美化和装饰")
    public List<ImageResource> searchIllustrations(@P("搜索关键词") String query) {
        // 返回结构化图片资源，失败时保持空列表。
        List<ImageResource> imageList = new ArrayList<>();
        // 限制最多 12 张，避免后续 prompt 太长。
        int searchCount = 12;
        // 根据关键词拼出 unDraw 搜索接口 URL。
        String apiUrl = String.format(UNDRAW_API_URL, query, query);

        // 使用 try-with-resources 自动释放 HTTP 资源
        try (HttpResponse response = HttpRequest.get(apiUrl).timeout(10000).execute()) {
            if (!response.isOk()) {
                return imageList;
            }
            // unDraw 返回的数据嵌在 pageProps.initialResults 中。
            JSONObject result = JSONUtil.parseObj(response.body());
            JSONObject pageProps = result.getJSONObject("pageProps");
            if (pageProps == null) {
                return imageList;
            }
            JSONArray initialResults = pageProps.getJSONArray("initialResults");
            if (initialResults == null || initialResults.isEmpty()) {
                return imageList;
            }
            int actualCount = Math.min(searchCount, initialResults.size());
            for (int i = 0; i < actualCount; i++) {
                JSONObject illustration = initialResults.getJSONObject(i);
                // title 作为图片描述，media 是图片地址。
                String title = illustration.getStr("title", "插画");
                String media = illustration.getStr("media", "");
                if (StrUtil.isNotBlank(media)) {
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.ILLUSTRATION)
                            .description(title)
                            .url(media)
                            .build());
                }
            }
        } catch (Exception e) {
            // 搜索失败时记录日志并返回空列表，避免素材服务影响主工作流。
            log.error("搜索插画失败：{}", e.getMessage(), e);
        }
        return imageList;
    }
}
