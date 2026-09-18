package com.shiguang.feed;

import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedSeenService feedSeenService;

    @PostMapping("/seen/{postId}")
    public R<Void> markSeen(@PathVariable Long postId) {
        feedSeenService.markSeen(SecurityUtils.getUserId(), postId);
        return R.ok();
    }
}
