package com.shiguang.notification;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 未读数：总数给底部导航徽标，分项给消息页的 Tab 徽标 */
    @GetMapping("/unread")
    public R<UnreadCountVO> unread() {
        return R.ok(notificationService.unread(SecurityUtils.getUserId()));
    }

    @GetMapping
    public R<PageVO<NotificationVO>> list(@RequestParam String category,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "20") int limit) {
        return R.ok(notificationService.list(SecurityUtils.getUserId(), category, cursor, limit));
    }

    /** 把某个分类下的未读全部标记为已读 */
    @PostMapping("/read")
    public R<Void> markRead(@RequestParam String category) {
        notificationService.markRead(SecurityUtils.getUserId(), category);
        return R.ok();
    }
}
