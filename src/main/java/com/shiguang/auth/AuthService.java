package com.shiguang.auth;

import com.shiguang.auth.dto.AuthResponse;
import com.shiguang.common.BizException;
import com.shiguang.user.User;
import com.shiguang.user.UserService;
import com.shiguang.user.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SmsCodeService smsCodeService;
    private final UserService userService;
    private final JwtService jwtService;

    @Value("${jwt.access-expire-minutes:120}")
    private long accessExpireMinutes;

    public AuthResponse login(String phone, String code) {
        if (!smsCodeService.verifyCode(phone, code)) {
            throw new BizException("验证码错误或已过期");
        }
        User user = userService.findOrCreateByPhone(phone);
        return issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        JwtService.TokenPayload payload = jwtService.parse(refreshToken, JwtService.TokenType.REFRESH);
        User user = userService.getById(payload.userId());
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.createToken(user.getId(), JwtService.TokenType.ACCESS);
        String refresh = jwtService.createToken(user.getId(), JwtService.TokenType.REFRESH);
        return new AuthResponse(access, refresh, "Bearer", accessExpireMinutes * 60, UserVO.from(user));
    }
}