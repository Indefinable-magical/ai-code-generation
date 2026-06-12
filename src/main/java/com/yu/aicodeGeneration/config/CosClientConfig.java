package com.yu.aicodeGeneration.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 腾讯云COS配置类
 *
 * 学习重点：
 * COS 用来保存生成应用的截图封面。
 * 配置来自 application.yml 的 cos.client 前缀，真正上传逻辑在 CosManager。
 * 
 * @author yupi
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

    /**
     * 域名
     */
    private String host;

    /**
     * secretId
     */
    private String secretId;

    /**
     * 密钥（注意不要泄露）
     */
    private String secretKey;

    /**
     * 区域
     */
    private String region;

    /**
     * 桶名
     */
    private String bucket;

    @Bean
    public COSClient cosClient() {
        // 初始化用户身份信息(secretId, secretKey)。
        // secretKey 属于敏感信息，真实项目中应使用环境变量或配置中心注入。
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置 bucket 的区域。region 必须和腾讯云控制台里的 bucket 所属地域一致。
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 生成 COS 客户端 Bean，后续 CosManager 可以直接注入使用。
        return new COSClient(cred, clientConfig);
    }
}
