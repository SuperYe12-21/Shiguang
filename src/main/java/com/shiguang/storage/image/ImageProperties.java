package com.shiguang.storage.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 图片后处理配置。
 *
 * @param heifConvertPath libheif 的 heif-convert 可执行文件（HEIC/HEIF → JPEG），服务器上是 apt 的 libheif-examples
 */
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(@DefaultValue("heif-convert") String heifConvertPath) {
}
