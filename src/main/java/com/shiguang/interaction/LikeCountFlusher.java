package com.shiguang.interaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountFlusher {

    private final LikeService likeService;

    /** 定时把 Redis 里的点赞净增量写回数据库 */
    @Scheduled(fixedDelayString = "${app.like.flush-interval-ms:30000}")
    public void flush() {
        likeService.flushPendingCounts();
    }
}