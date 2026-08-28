package com.shiguang.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shiguangOpenApi() {
        return new OpenAPI().info(new Info()
                .title("拾光 Shiguang API")
                .description("短视频/图文分享社交平台接口文档")
                .version("v0.1"));
    }
}