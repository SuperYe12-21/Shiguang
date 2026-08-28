package com.shiguang.content.transcode;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.transcode")
public record TranscodeProperties(
        String ffmpegPath,
        String queue,
        String exchange,
        String routingKey) {
}