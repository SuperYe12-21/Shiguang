package com.shiguang.admin;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import com.shiguang.content.PostVO;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 入口显隐判断：非管理员返回 admin=false，不算错误 */
    @GetMapping("/me")
    public R<AdminMeVO> me() {
        return R.ok(new AdminMeVO(adminService.isAdmin(SecurityUtils.getUserId())));
    }

    /** 全量作品列表，含处理中 / 失败 / 仅自己可见 / 已下架 */
    @GetMapping("/posts")
    public R<PageVO<AdminPostVO>> posts(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String visibility,
                                        @RequestParam(required = false) String authorKeyword,
                                        @RequestParam(required = false) Long postId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") int limit) {
        adminService.requireAdmin(SecurityUtils.getUserId());
        return R.ok(adminService.listPosts(status, visibility, authorKeyword, postId, cursor, limit));
    }

    /** 下架作品（可填原因，作者可见），重复下架幂等 */
    @PostMapping("/posts/{id}/block")
    public R<PostVO> block(@PathVariable Long id, @Valid @RequestBody BlockPostRequest request) {
        adminService.requireAdmin(SecurityUtils.getUserId());
        return R.ok(adminService.block(id, request.getReason()));
    }

    /** 恢复被下架的作品 */
    @PostMapping("/posts/{id}/unblock")
    public R<PostVO> unblock(@PathVariable Long id) {
        adminService.requireAdmin(SecurityUtils.getUserId());
        return R.ok(adminService.unblock(id));
    }

    /** 硬删作品（不可逆）：同时清理存储对象、点赞 / 收藏 / 评论 */
    @DeleteMapping("/posts/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminService.requireAdmin(SecurityUtils.getUserId());
        adminService.delete(id);
        return R.ok();
    }
}
