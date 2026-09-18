package com.shiguang.feed;

import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 首页播放列表的观看记录：Redis ZSET，member=postId，score=最后观看时间 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedSeenService {

    private static final String KEY_PREFIX = "feed:seen:";
    private static final Duration RETENTION = Duration.ofDays(90);

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;

    /** 记录一次有效观看，重复观看只更新时间；任何异常都静默，不影响播放 */
    public void markSeen(Long userId, Long postId) {
        if (userId == null || postId == null) {
            return;
        }
        try {
            Post post = postMapper.selectById(postId);
            if (post == null) {
                return;
            }
            String key = key(userId);
            long now = System.currentTimeMillis();
            redis.opsForZSet().add(key, String.valueOf(postId), now);
            redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now - RETENTION.toMillis());
            redis.expire(key, RETENTION);
        } catch (Exception e) {
            log.warn("记录观看失败 userId={} postId={}: {}", userId, postId, e.getMessage());
        }
    }

    /** 已看过的作品 ID 集合；Redis 异常时返回空集合（降级为不过滤） */
    public Set<Long> seenIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        try {
            Set<String> members = redis.opsForZSet().range(key(userId), 0, -1);
            if (members == null || members.isEmpty()) {
                return Set.of();
            }
            Set<Long> ids = new LinkedHashSet<>(members.size());
            for (String member : members) {
                try {
                    ids.add(Long.parseLong(member));
                } catch (NumberFormatException ignored) {
                    // 脏数据忽略
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("读取观看记录失败 userId={}: {}", userId, e.getMessage());
            return Set.of();
        }
    }

    /** 按“最久没看”的顺序（score 升序）取一段已看作品 ID */
    public List<Long> pageSeen(Long userId, long offset, int limit) {
        if (userId == null || limit <= 0 || offset < 0) {
            return List.of();
        }
        try {
            Set<String> members = redis.opsForZSet().range(key(userId), offset, offset + limit - 1);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>(members.size());
            for (String member : members) {
                try {
                    ids.add(Long.parseLong(member));
                } catch (NumberFormatException ignored) {
                    // 脏数据忽略
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("读取观看记录分页失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public long seenCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            Long size = redis.opsForZSet().zCard(key(userId));
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.warn("读取观看记录数量失败 userId={}: {}", userId, e.getMessage());
            return 0L;
        }
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
