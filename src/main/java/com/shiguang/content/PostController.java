package com.shiguang.content;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public R<PostVO> create(@Valid @RequestBody CreatePostRequest request) {
        return R.ok(postService.create(request, SecurityUtils.getUserId()));
    }

    @GetMapping("/{id}")
    public R<PostVO> detail(@PathVariable Long id) {
        return R.ok(postService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postService.delete(id, SecurityUtils.getUserId());
        return R.ok();
    }
}