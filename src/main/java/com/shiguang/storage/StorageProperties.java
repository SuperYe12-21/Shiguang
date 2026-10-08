package com.shiguang.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, Minio minio, Oss oss, int presignExpiryMinutes) {

    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {
    }

    /**
     * @param endpoint          公网 endpoint（oss-cn-beijing.aliyuncs.com）：生成上传预签名地址用，浏览器要能直连
     * @param internalEndpoint  内网 endpoint（oss-cn-beijing-internal.aliyuncs.com）：服务端读写走内网，免流量费；留空则用 endpoint
     * @param publicBaseUrl     浏览器访问媒体的前缀，默认 https://{bucket}.{endpoint}；以后接 CDN 只改这一项
     */
    public record Oss(String endpoint,
                      String internalEndpoint,
                      String publicBaseUrl,
                      String accessKeyId,
                      String accessKeySecret,
                      String bucket) {

        public String resolvedInternalEndpoint() {
            return internalEndpoint == null || internalEndpoint.isBlank() ? endpoint : internalEndpoint;
        }

        /** 预签名地址一律走 https：生产前端是 https，http 直传会被浏览器按混合内容拦掉 */
        public String resolvedPresignEndpoint() {
            return "https://" + stripScheme(endpoint);
        }

        public String resolvedDataEndpoint() {
            return "https://" + stripScheme(resolvedInternalEndpoint());
        }

        public String resolvedPublicBaseUrl() {
            if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
                return publicBaseUrl.endsWith("/")
                        ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                        : publicBaseUrl;
            }
            return "https://" + bucket + "." + stripScheme(endpoint);
        }

        private static String stripScheme(String value) {
            return value == null ? "" : value.replaceFirst("^https?://", "");
        }
    }
}
