package com.shiguang.common.counter;

import com.shiguang.feed.ViewCountService;
import com.shiguang.interaction.LikeService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@EnableAutoConfiguration
@MapperScan(basePackages = {"com.shiguang.content", "com.shiguang.interaction"}, annotationClass = Mapper.class)
@Import({LikeService.class, ViewCountService.class, RecoverableCounterBuffer.class})
public class CounterTestApplication {
}
