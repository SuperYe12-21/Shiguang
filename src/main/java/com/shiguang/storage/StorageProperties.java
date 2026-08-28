package com.shiguang.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(Minio minio, int presignExpiryMinutes) {

    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {
    }
}