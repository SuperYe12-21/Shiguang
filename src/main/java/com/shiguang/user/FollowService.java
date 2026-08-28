package com.shiguang.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    /** 关注（重复关注幂等） */
    public FollowVO follow(Long followerId, Long followeeId) {
        validate(followerId, followeeId);
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        try {
            followMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            log.debug("重复关注，忽略: follower={} followee={}", followerId, followeeId);
        }
        return state(followeeId, followerId);
    }

    /** 取消关注（未关注时幂等） */
    public FollowVO unfollow(Long followerId, Long followeeId) {
        validate(followerId, followeeId);
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        return state(followeeId, followerId);
    }

    /** 粉丝列表：按关注时间倒序游标分页 */
    public PageVO<UserPublicVO> followers(Long userId, Long cursorId, int limit) {
        requireUser(userId);
        List<Follow> rows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFolloweeId, userId)
                .lt(cursorId != null && cursorId > 0, Follow::getId, cursorId)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + (normalizeLimit(limit) + 1)));
        return toUserPage(rows, Follow::getFollowerId, normalizeLimit(limit));
    }

    /** 关注列表：按关注时间倒序游标分页 */
    public PageVO<UserPublicVO> following(Long userId, Long cursorId, int limit) {
        requireUser(userId);
        List<Follow> rows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .lt(cursorId != null && cursorId > 0, Follow::getId, cursorId)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + (normalizeLimit(limit) + 1)));
        return toUserPage(rows, Follow::getFolloweeId, normalizeLimit(limit));
    }

    public long followerCount(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId));
    }

    public long followingCount(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
    }

    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            return false;
        }
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId)) > 0;
    }

    /** 批量查询 follower 是否关注了这些用户（信息流/详情用） */
    public Map<Long, Boolean> followingMap(Long followerId, Collection<Long> followeeIds) {
        if (followerId == null || followeeIds == null || followeeIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = followeeIds.stream().distinct().toList();
        Set<Long> following = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, followerId)
                        .in(Follow::getFolloweeId, distinct))
                .stream().map(Follow::getFolloweeId).collect(Collectors.toSet());
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : distinct) {
            result.put(id, following.contains(id));
        }
        return result;
    }

    public User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private void validate(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BizException(1, "不能关注自己");
        }
        requireUser(followeeId);
    }

    private FollowVO state(Long targetUserId, Long viewerId) {
        return FollowVO.builder()
                .following(isFollowing(viewerId, targetUserId))
                .followerCount(followerCount(targetUserId))
                .build();
    }

    private PageVO<UserPublicVO> toUserPage(List<Follow> rows, Function<Follow, Long> userIdExtractor, int limit) {
        boolean hasMore = rows.size() > limit;
        List<Follow> page = hasMore ? rows.subList(0, limit) : rows;
        List<UserPublicVO> items = List.of();
        String nextCursor = null;
        if (!page.isEmpty()) {
            List<Long> userIds = page.stream().map(userIdExtractor).distinct().toList();
            Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            items = page.stream()
                    .map(userIdExtractor)
                    .map(users::get)
                    .filter(java.util.Objects::nonNull)
                    .map(UserPublicVO::from)
                    .toList();
            nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        }
        return PageVO.<UserPublicVO>builder().items(items).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}