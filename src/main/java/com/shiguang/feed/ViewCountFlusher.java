package com.shiguang.feed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlusher {

    private final ViewCountService viewCountService;

    /** 定时把 Redis 里的播放量增量写回数据库 */
    @Scheduled(fixedDelayString = "${app.view.flush-interval-ms:30000}")
    public void flush() {
        viewCountService.flushPendingCounts();
    }
}
