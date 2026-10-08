package com.shiguang.user;

import com.shiguang.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 列表可见性：点赞 / 收藏 / 粉丝 / 关注 四项各自独立，默认公开。
 * 好友 = 互相关注（不新增好友关系表）。
 */
@Service
@RequiredArgsConstructor
public class UserPrivacyService {

    /** 四项列表的标识 */
    public enum Kind {
        LIKE("点赞"),
        FAVORITE("收藏"),
        FOLLOWER("粉丝"),
        FOLLOWING("关注");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final UserPrivacyMapper privacyMapper;
    private final FollowService followService;

    /** 自己看自己的设置：没有记录时按全公开返回 */
    public UserPrivacyVO get(Long userId) {
        UserPrivacy row = privacyMapper.selectById(userId);
        return UserPrivacyVO.builder()
                .like(levelOf(row, Kind.LIKE).name())
                .favorite(levelOf(row, Kind.FAVORITE).name())
                .follower(levelOf(row, Kind.FOLLOWER).name())
                .following(levelOf(row, Kind.FOLLOWING).name())
                .build();
    }

    /** 整份覆盖保存 */
    @Transactional
    public UserPrivacyVO save(Long userId, UpdatePrivacyRequest request) {
        UserPrivacy row = privacyMapper.selectById(userId);
        if (row == null) {
            row = new UserPrivacy();
            row.setUserId(userId);
        }
        row.setLikeVisibility(parse(request.getLike()).name());
        row.setFavoriteVisibility(parse(request.getFavorite()).name());
        row.setFollowerVisibility(parse(request.getFollower()).name());
        row.setFollowingVisibility(parse(request.getFollowing()).name());
        if (privacyMapper.selectById(userId) == null) {
            privacyMapper.insert(row);
        } else {
            privacyMapper.updateById(row);
        }
        return get(userId);
    }

    /** 四项一起算，他人主页一次查完 */
    public UserPrivacyVO.Access access(Long ownerId, Long viewerId) {
        UserPrivacy row = privacyMapper.selectById(ownerId);
        boolean friend = isFriend(ownerId, viewerId);
        return UserPrivacyVO.Access.builder()
                .like(visible(levelOf(row, Kind.LIKE), ownerId, viewerId, friend))
                .favorite(visible(levelOf(row, Kind.FAVORITE), ownerId, viewerId, friend))
                .follower(visible(levelOf(row, Kind.FOLLOWER), ownerId, viewerId, friend))
                .following(visible(levelOf(row, Kind.FOLLOWING), ownerId, viewerId, friend))
                .build();
    }

    /** 列表接口的准入校验：无权访问抛业务错误 */
    public void assertCanView(Long ownerId, Long viewerId, Kind kind) {
        if (!canView(ownerId, viewerId, kind)) {
            VisibilityLevel level = levelOf(privacyMapper.selectById(ownerId), kind);
            String scope = level == VisibilityLevel.FRIENDS ? "仅好友可见" : "仅自己可见";
            throw new BizException(403, "TA 的" + kind.label() + scope);
        }
    }

    public boolean canView(Long ownerId, Long viewerId, Kind kind) {
        return visible(levelOf(privacyMapper.selectById(ownerId), kind), ownerId, viewerId,
                isFriend(ownerId, viewerId));
    }

    /** 互相关注 = 好友 */
    public boolean isFriend(Long ownerId, Long viewerId) {
        if (ownerId == null || viewerId == null || ownerId.equals(viewerId)) {
            return false;
        }
        return followService.isFollowing(ownerId, viewerId) && followService.isFollowing(viewerId, ownerId);
    }

    private static boolean visible(VisibilityLevel level, Long ownerId, Long viewerId, boolean friend) {
        if (ownerId != null && ownerId.equals(viewerId)) {
            return true;
        }
        return switch (level) {
            case PRIVATE -> false;
            case FRIENDS -> friend;
            default -> true;
        };
    }

    private static VisibilityLevel levelOf(UserPrivacy row, Kind kind) {
        if (row == null) {
            return VisibilityLevel.PUBLIC;
        }
        String raw = switch (kind) {
            case LIKE -> row.getLikeVisibility();
            case FAVORITE -> row.getFavoriteVisibility();
            case FOLLOWER -> row.getFollowerVisibility();
            case FOLLOWING -> row.getFollowingVisibility();
        };
        return VisibilityLevel.parse(raw, VisibilityLevel.PUBLIC);
    }

    private static VisibilityLevel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(1, "可见性取值不能为空");
        }
        try {
            return VisibilityLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(1, "可见性取值不合法: " + raw);
        }
    }
}
