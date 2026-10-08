package com.shiguang.feed;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.content.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedSeenService feedSeenService;
    private final FeedService feedService;

    /** 朋友动态：互关好友的作品流 */
    @GetMapping("/friends")
    public R<PageVO<PostVO>> friendsFeed(@RequestParam(required = false) String cursor,
                                         @RequestParam(defaultValue = "10") int limit) {
        return R.ok(feedService.friendsFeed(SecurityUtils.getUserId(), cursor, limit));
    }

    /** 朋友动态红点：是否有未查看的好友新作品 */
    @GetMapping("/friends/unread")
    public R<FriendsUnreadVO> friendsUnread() {
        return R.ok(feedService.friendsUnread(SecurityUtils.getUserId()));
    }

    /** 标记朋友动态已读（进入朋友页时调用） */
    @PostMapping("/friends/seen")
    public R<Void> markFriendsSeen() {
        feedService.markFriendsSeen(SecurityUtils.getUserId());
        return R.ok();
    }

    /** 观看历史：按最近观看时间倒序 */
    @GetMapping("/history")
    public R<PageVO<PostVO>> history(@RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int limit) {
        return R.ok(feedService.history(SecurityUtils.getUserId(), cursor, limit));
    }

    /** 清空观看历史 */
    @DeleteMapping("/history")
    public R<Void> clearHistory() {
        feedService.clearHistory(SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/seen/{postId}")
    public R<Void> markSeen(@PathVariable Long postId) {
        feedSeenService.markSeen(SecurityUtils.getUserId(), postId);
        return R.ok();
    }
}
