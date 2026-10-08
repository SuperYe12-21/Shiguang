package com.shiguang.feed;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 播放量服务：计数走 Redis Hash 缓冲（field = p:{postId}，value = 未落库增量），
 * 展示时用 数据库冗余计数 + pending 增量，定时任务把增量原子落库（模式同 LikeService）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {

    /** 计数缓冲 hash：field 为 p:{postId}，value 为未落库增量 */
    public static final String PENDING_KEY = "view:pending";

    private static final String DUP_PREFIX = "view:dup:";

    private static final DefaultRedisScript<List<Object>> POP_PENDING_SCRIPT = new DefaultRedisScript<>(
            "local f = redis.call('HGETALL', KEYS[1]); "
                    + "if #f == 0 then return {} end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "return f",
            (Class<List<Object>>) (Class<?>) List.class);

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;

    /**
     * 记一次有效播放：同一用户同一作品 24 小时内只计一次，避免重复观看刷量。
     * 任何异常都静默，不影响播放体验。
     */
    public void recordView(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return;
        }
        try {
            Boolean fresh = redis.opsForValue().setIfAbsent(DUP_PREFIX + postId + ":" + userId, "1",
                    java.time.Duration.ofHours(24));
            if (Boolean.TRUE.equals(fresh)) {
                redis.opsForHash().increment(PENDING_KEY, "p:" + postId, 1);
            }
        } catch (Exception e) {
            log.warn("播放量计数失败 postId={} userId={}: {}", postId, userId, e.getMessage());
        }
    }

    /** 批量取 pending 增量（一次 Redis 往返）；Redis 异常时返回空 map（展示降级为仅数据库值） */
    public Map<Long, Long> pendingDeltas(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = postIds.stream().distinct().toList();
        try {
            List<String> values = redis.<String, String>opsForHash().multiGet(PENDING_KEY,
                    distinct.stream().map(id -> "p:" + id).toList());
            Map<Long, Long> result = new HashMap<>();
            if (values == null) {
                return result;
            }
            for (int i = 0; i < distinct.size(); i++) {
                Object value = values.get(i);
                if (value != null) {
                    try {
                        result.put(distinct.get(i), Long.parseLong(value.toString()));
                    } catch (NumberFormatException ignored) {
                        // 非法值按 0 处理
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("读取播放量增量失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 把 pending 增量原子地搬到数据库（由定时任务调用） */
    public void flushPendingCounts() {
        List<Object> popped;
        try {
            popped = redis.execute(POP_PENDING_SCRIPT, List.of(PENDING_KEY));
        } catch (Exception e) {
            log.warn("播放量增量弹出失败: {}", e.getMessage());
            return;
        }
        if (popped == null || popped.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < popped.size(); i += 2) {
            String field = (String) popped.get(i);
            long delta;
            try {
                delta = Long.parseLong((String) popped.get(i + 1));
            } catch (Exception e) {
                log.warn("播放量增量非法: field={} value={}", field, popped.get(i + 1));
                continue;
            }
            try {
                applyDelta(field, delta);
            } catch (Exception e) {
                log.error("播放量落库失败，回滚 pending: field={}", field, e);
                redis.opsForHash().increment(PENDING_KEY, field, delta);
            }
        }
    }

    private void applyDelta(String field, long delta) {
        if (!field.startsWith("p:")) {
            return;
        }
        Long postId = Long.parseLong(field.substring(2));
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("view_count = view_count + " + delta));
    }
}
