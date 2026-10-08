package com.shiguang.search;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.content.PostVO;
import com.shiguang.feed.FeedService;
import com.shiguang.user.FollowService;
import com.shiguang.user.UserPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FollowService followService;
    private final FeedService feedService;

    /** 搜索用户（昵称模糊匹配，id 倒序游标分页） */
    @GetMapping("/users")
    public R<PageVO<UserPublicVO>> users(@RequestParam String keyword,
                                         @RequestParam(required = false) Long cursor,
                                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(followService.searchUsers(keyword, SecurityUtils.getUserId(), cursor, limit));
    }

    /** 搜索公开作品（标题/简介模糊匹配，时间倒序游标分页） */
    @GetMapping("/posts")
    public R<PageVO<PostVO>> posts(@RequestParam String keyword,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(defaultValue = "12") int limit) {
        return R.ok(feedService.searchPosts(keyword, cursor, limit, SecurityUtils.getUserId()));
    }
}
