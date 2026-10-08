package com.shiguang.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收藏服务：直接落库（收藏量小、无常驻热点），计数按 post_id 分组统计，
 * 不走 Redis 缓冲，避免再引入一处计数漂移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final PostFavoriteMapper favoriteMapper;
    private final LikeService likeService;

    /** 收藏（重复收藏幂等） */
    @Transactional
    public FavoriteVO favorite(Long postId, Long userId) {
        likeService.requireAccessiblePost(postId, userId);
        try {
            favoriteMapper.insert(new PostFavorite(postId, userId));
        } catch (DuplicateKeyException e) {
            log.debug("重复收藏，忽略: post={} user={}", postId, userId);
        }
        return state(postId, userId);
    }

    /** 取消收藏（未收藏时幂等） */
    @Transactional
    public FavoriteVO unfavorite(Long postId, Long userId) {
        likeService.requireAccessiblePost(postId, userId);
        favoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getPostId, postId)
                .eq(PostFavorite::getUserId, userId));
        return state(postId, userId);
    }

    public boolean isFavorited(Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getPostId, postId)
                .eq(PostFavorite::getUserId, userId)) > 0;
    }

    /** 批量：viewer 是否收藏了这些作品 */
    public Map<Long, Boolean> favoritedMap(Collection<Long> postIds, Long userId) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = postIds.stream().distinct().toList();
        Set<Long> favorited = favoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>()
                        .select(PostFavorite::getPostId)
                        .eq(PostFavorite::getUserId, userId)
                        .in(PostFavorite::getPostId, distinct))
                .stream().map(PostFavorite::getPostId).collect(Collectors.toSet());
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : distinct) {
            result.put(id, favorited.contains(id));
        }
        return result;
    }

    /** 批量计数：一页一次 GROUP BY */
    public Map<Long, Long> countMap(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = postIds.stream().distinct().toList();
        List<Map<String, Object>> rows = favoriteMapper.selectMaps(new QueryWrapper<PostFavorite>()
                .select("post_id", "COUNT(*) AS cnt")
                .in("post_id", distinct)
                .groupBy("post_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object idValue = row.get("post_id");
            Object countValue = row.get("cnt");
            if (idValue instanceof Number id && countValue instanceof Number count) {
                result.put(id.longValue(), count.longValue());
            }
        }
        return result;
    }

    /** 作品删除后的清理 */
    public void cleanupPost(Long postId) {
        favoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getPostId, postId));
    }

    private FavoriteVO state(Long postId, Long userId) {
        return FavoriteVO.builder()
                .favorited(isFavorited(postId, userId))
                .favoriteCount(countMap(List.of(postId)).getOrDefault(postId, 0L))
                .build();
    }
}
