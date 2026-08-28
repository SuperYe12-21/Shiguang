package com.shiguang.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        String provider,
        String mockCode,
        int codeExpireMinutes,
        int cooldownSeconds,
        int hourlyLimit) {
}