package com.shiguang.user;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.content.PostVO;
import com.shiguang.feed.FeedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FeedService feedService;
    private final FollowService followService;

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.toVO(userService.getById(SecurityUtils.getUserId())));
    }

    @GetMapping("/{id}")
    public R<UserProfileVO> profile(@PathVariable Long id) {
        return R.ok(userService.getProfile(id, SecurityUtils.getUserId()));
    }

    @GetMapping("/{id}/posts")
    public R<PageVO<PostVO>> posts(@PathVariable Long id,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(defaultValue = "10") int limit) {
        return R.ok(feedService.userPosts(id, SecurityUtils.getUserId(), cursor, limit));
    }

    @GetMapping("/{id}/followers")
    public R<PageVO<UserPublicVO>> followers(@PathVariable Long id,
                                             @RequestParam(required = false) Long cursor,
                                             @RequestParam(defaultValue = "20") int limit) {
        return R.ok(followService.followers(id, cursor, limit));
    }

    @GetMapping("/{id}/following")
    public R<PageVO<UserPublicVO>> following(@PathVariable Long id,
                                             @RequestParam(required = false) Long cursor,
                                             @RequestParam(defaultValue = "20") int limit) {
        return R.ok(followService.following(id, cursor, limit));
    }

    @PutMapping("/me")
    public R<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        User updated = userService.updateProfile(
                SecurityUtils.getUserId(),
                request.nickname(),
                request.avatarUrl() == null ? "" : request.avatarUrl(),
                request.bio() == null ? "" : request.bio());
        return R.ok(userService.toVO(updated));
    }
}