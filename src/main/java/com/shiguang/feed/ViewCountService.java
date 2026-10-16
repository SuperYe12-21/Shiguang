package com.shiguang.feed;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.counter.RecoverableCounterBuffer;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;
    private final RecoverableCounterBuffer counterBuffer;

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

    /** 批量取未提交增量，按数据库收据排除已提交批次；异常时展示降级为仅数据库值 */
    public Map<Long, Long> pendingDeltas(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = postIds.stream().distinct().toList();
        try {
            Map<String, Long> values = counterBuffer.pendingDeltas("view", PENDING_KEY,
                    distinct.stream().map(id -> "p:" + id).toList());
            Map<Long, Long> result = new HashMap<>();
            for (Long postId : distinct) {
                result.put(postId, values.getOrDefault("p:" + postId, 0L));
            }
            return result;
        } catch (Exception e) {
            log.warn("读取播放量增量失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 恢复或暂存一批增量，事务落库后确认（由定时任务调用） */
    public void flushPendingCounts() {
        counterBuffer.flush("view", PENDING_KEY, this::applyDelta);
    }

    private void applyDelta(String field, long delta) {
        if (!field.startsWith("p:")) {
            throw new IllegalArgumentException("Invalid view counter field: " + field);
        }
        Long postId = Long.parseLong(field.substring(2));
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("view_count = view_count + " + delta));
    }
}
