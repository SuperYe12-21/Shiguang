package com.shiguang.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        String provider,
        String mockCode,
        int codeExpireMinutes,
        int cooldownSeconds,
        int hourlyLimit,
        Aliyun aliyun,
        Pnvs pnvs) {

    /** 阿里云短信（标准短信服务）：签名和模板要自己申请，并且要过人工审核 */
    public record Aliyun(String accessKeyId,
                         String accessKeySecret,
                         String signName,
                         String templateCode,
                         String endpoint) {

        public String resolvedEndpoint() {
            return endpoint == null || endpoint.isBlank() ? "dysmsapi.aliyuncs.com" : endpoint;
        }
    }

    /**
     * 阿里云号码认证服务·短信认证（app.sms.provider=pnvs）。
     * 用阿里云「赠送」的签名和模板，不需要申请资质、不走人工审核；
     * 代价是短信里的签名由阿里云指定，不是自己的品牌。
     */
    public record Pnvs(String accessKeyId,
                       String accessKeySecret,
                       String signName,
                       String templateCode,
                       String templateParam,
                       String schemeName,
                       String endpoint) {

        /** 默认变量组合，对应赠送模板「您的验证码是 ${code}，有效期 ${min} 分钟」这类文案 */
        private static final String DEFAULT_TEMPLATE_PARAM = "{\"code\":\"{code}\",\"min\":\"{min}\"}";

        public String resolvedEndpoint() {
            return endpoint == null || endpoint.isBlank() ? "dypnsapi.aliyuncs.com" : endpoint;
        }

        /**
         * 赠送模板的变量个数不固定，所以做成可配置；{code} / {min} 会被替换成实际值。
         * 模板原文可以在控制台「赠送模板配置」里看到，变量对不上时会报模板参数错误。
         */
        public String resolvedTemplateParam(String code, int expireMinutes) {
            String pattern = templateParam == null || templateParam.isBlank()
                    ? DEFAULT_TEMPLATE_PARAM
                    : templateParam;
            return pattern.replace("{code}", code).replace("{min}", String.valueOf(expireMinutes));
        }
    }

    public boolean mockProvider() {
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider);
    }
}
