package com.shiguang.user;

import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(UserVO.from(userService.getById(SecurityUtils.getUserId())));
    }

    @PutMapping("/me")
    public R<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        User updated = userService.updateProfile(
                SecurityUtils.getUserId(),
                request.nickname(),
                request.avatarUrl() == null ? "" : request.avatarUrl(),
                request.bio() == null ? "" : request.bio());
        return R.ok(UserVO.from(updated));
    }
}