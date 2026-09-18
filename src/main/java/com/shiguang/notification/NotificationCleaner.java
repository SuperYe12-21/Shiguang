package com.shiguang.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleaner {

    private static final int RETENTION_DAYS = 90;

    private final NotificationMapper notificationMapper;

    /** 通知保留 90 天，超期清理，避免表无限增长 */
    @Scheduled(cron = "${app.notification.clean-cron:0 30 3 * * ?}")
    public void clean() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int removed = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .lt(Notification::getUpdatedAt, deadline));
        if (removed > 0) {
            log.info("清理过期通知 {} 条（早于 {}）", removed, deadline);
        }
    }
}
