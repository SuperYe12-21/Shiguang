package com.shiguang.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        String provider,
        String mockCode,
        int codeExpireMinutes,
        int cooldownSeconds,
        int hourlyLimit,
        Aliyun aliyun) {

    /** 阿里云短信配置；accessKey 用 RAM 子账号，只授短信发送权限 */
    public record Aliyun(String accessKeyId,
                         String accessKeySecret,
                         String signName,
                         String templateCode,
                         String endpoint) {

        public String resolvedEndpoint() {
            return endpoint == null || endpoint.isBlank() ? "dysmsapi.aliyuncs.com" : endpoint;
        }
    }

    public boolean mockProvider() {
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider);
    }
}
