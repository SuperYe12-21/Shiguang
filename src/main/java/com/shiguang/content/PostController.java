package com.shiguang.content;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.feed.FeedService;
import com.shiguang.interaction.FavoriteService;
import com.shiguang.interaction.FavoriteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final FavoriteService favoriteService;

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

    /** 编辑文案（标题 / 简介），仅作者 */
    @PutMapping("/{id}")
    public R<PostVO> update(@PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        return R.ok(postService.updateText(id, request, SecurityUtils.getUserId()));
    }

    /** 切换可见性（PUBLIC / PRIVATE），仅作者 */
    @PutMapping("/{id}/visibility")
    public R<PostVO> updateVisibility(@PathVariable Long id, @Valid @RequestBody UpdateVisibilityRequest request) {
        return R.ok(postService.setVisibility(id, request.getVisibility(), SecurityUtils.getUserId()));
    }

    /** 收藏（重复收藏幂等） */
    @PostMapping("/{id}/favorite")
    public R<FavoriteVO> favorite(@PathVariable Long id) {
        return R.ok(favoriteService.favorite(id, SecurityUtils.getUserId()));
    }

    /** 取消收藏（未收藏时幂等） */
    @DeleteMapping("/{id}/favorite")
    public R<FavoriteVO> unfavorite(@PathVariable Long id) {
        return R.ok(favoriteService.unfavorite(id, SecurityUtils.getUserId()));
    }
}
