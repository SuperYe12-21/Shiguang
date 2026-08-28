package com.shiguang.interaction;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class InteractionController {

    private final LikeService likeService;
    private final CommentService commentService;

    @PostMapping("/posts/{id}/like")
    public R<LikeVO> likePost(@PathVariable Long id) {
        return R.ok(likeService.likePost(id, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/posts/{id}/like")
    public R<LikeVO> unlikePost(@PathVariable Long id) {
        return R.ok(likeService.unlikePost(id, SecurityUtils.getUserId()));
    }

    @GetMapping("/posts/{id}/comments")
    public R<PageVO<CommentVO>> comments(@PathVariable Long id,
                                         @RequestParam(required = false) Long cursor,
                                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(commentService.list(id, cursor, limit, SecurityUtils.getUserId()));
    }

    @PostMapping("/posts/{id}/comments")
    public R<CommentVO> createComment(@PathVariable Long id,
                                      @Valid @RequestBody CreateCommentRequest request) {
        return R.ok(commentService.create(id, SecurityUtils.getUserId(), request.getContent()));
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        commentService.delete(id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/comments/{id}/like")
    public R<LikeVO> likeComment(@PathVariable Long id) {
        return R.ok(likeService.likeComment(id, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/comments/{id}/like")
    public R<LikeVO> unlikeComment(@PathVariable Long id) {
        return R.ok(likeService.unlikeComment(id, SecurityUtils.getUserId()));
    }
}