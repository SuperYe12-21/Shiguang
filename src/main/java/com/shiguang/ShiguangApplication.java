package com.shiguang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShiguangApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangApplication.class, args);
    }

}
