package com.shiguang.user;

import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.common.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /** 互关好友列表（朋友页） */
    @GetMapping("/friends")
    public R<PageVO<UserPublicVO>> friends(@RequestParam(required = false) Long cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        return R.ok(followService.friends(SecurityUtils.getUserId(), cursor, limit));
    }

    @PostMapping("/{userId}")
    public R<FollowVO> follow(@PathVariable Long userId) {
        return R.ok(followService.follow(SecurityUtils.getUserId(), userId));
    }

    @DeleteMapping("/{userId}")
    public R<FollowVO> unfollow(@PathVariable Long userId) {
        return R.ok(followService.unfollow(SecurityUtils.getUserId(), userId));
    }
}
