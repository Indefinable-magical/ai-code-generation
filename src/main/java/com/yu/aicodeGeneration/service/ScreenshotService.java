package com.yu.aicodeGeneration.service;

/**
 * 截图服务
 * 学习提示：这个接口把“本地生成截图”和“上传到对象存储”封装成一个业务能力，调用方只关心最终可访问地址。
 */
public interface ScreenshotService {


    /**
     * 通用的截图服务，可以得到访问地址
     *
     * @param webUrl 网址
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);

}
