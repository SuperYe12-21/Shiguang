package com.shiguang.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shiguang.common.BizException;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostStatus;
import com.shiguang.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final FollowService followService;
    private final PostMapper postMapper;
    private final StorageService storageService;

    @Transactional
    public User findOrCreateByPhone(String phone) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user != null) {
            return user;
        }
        user = new User();
        user.setPhone(phone);
        user.setNickname("拾光用户" + phone.substring(phone.length() - 4));
        userMapper.insert(user);
        return user;
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    /** 他人主页聚合：资料 + 关注/粉丝/作品统计 + 当前登录人是否已关注 */
    public UserProfileVO getProfile(Long userId, Long viewerId) {
        User user = getById(userId);
        return UserProfileVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(toAvatarUrl(user.getAvatarUrl()))
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .followerCount(followService.followerCount(userId))
                .followingCount(followService.followingCount(userId))
                .postCount(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, PostStatus.PUBLISHED)))
                .followedByMe(followService.isFollowing(viewerId, userId))
                .build();
    }

    /** 用户资料 VO：头像对象名实时转预签名 URL */
    public UserVO toVO(User user) {
        return new UserVO(
                user.getId(),
                user.getPhone(),
                user.getNickname(),
                toAvatarUrl(user.getAvatarUrl()),
                user.getBio(),
                user.getCreatedAt());
    }

    private String toAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return avatarUrl;
        }
        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            return avatarUrl;
        }
        return storageService.presignedGetUrl(avatarUrl);
    }

    @Transactional
    public User updateProfile(Long userId, String nickname, String avatarUrl, String bio) {
        User user = getById(userId);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setBio(bio);
        userMapper.updateById(user);
        return user;
    }
}