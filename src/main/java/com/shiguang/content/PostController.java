package com.shiguang.content;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.feed.FeedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FeedService feedService;

    @PostMapping
    public R<PostVO> create(@Valid @RequestBody CreatePostRequest request) {
        return R.ok(postService.create(request, SecurityUtils.getUserId()));
    }

    @GetMapping("/feed")
    public R<PageVO<PostVO>> feed(@RequestParam(required = false) String cursor,
                          @RequestParam(defaultValue = "10") int limit) {
        return R.ok(feedService.feed(cursor, limit, SecurityUtils.getUserId()));
    }

    @GetMapping("/{id}")
    public R<PostVO> detail(@PathVariable Long id) {
        return R.ok(postService.getDetail(id, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postService.delete(id, SecurityUtils.getUserId());
        return R.ok();
    }
}