package com.shiguang.auth;

import com.shiguang.auth.dto.AuthResponse;
import com.shiguang.auth.dto.LoginRequest;
import com.shiguang.auth.dto.RefreshRequest;
import com.shiguang.auth.dto.SendSmsRequest;
import com.shiguang.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SmsCodeService smsCodeService;
    private final AuthService authService;

    @PostMapping("/sms-code")
    public R<Void> sendSmsCode(@Valid @RequestBody SendSmsRequest request) {
        smsCodeService.sendCode(request.phone());
        return R.ok();
    }

    @PostMapping("/login")
    public R<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request.phone(), request.code()));
    }

    @PostMapping("/refresh")
    public R<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return R.ok(authService.refresh(request.refreshToken()));
    }
}