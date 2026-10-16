package com.shiguang.content.transcode;

import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TranscodeRetryController {

    private final TranscodeTaskService taskService;

    @PostMapping("/api/posts/{id}/retry-transcode")
    public R<Void> retry(@PathVariable Long id) {
        taskService.retry(id, SecurityUtils.getUserId());
        return R.ok();
    }
}
