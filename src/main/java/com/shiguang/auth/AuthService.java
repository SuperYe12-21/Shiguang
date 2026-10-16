package com.shiguang.auth;

import com.shiguang.auth.dto.AuthResponse;
import com.shiguang.common.BizException;
import com.shiguang.user.User;
import com.shiguang.user.UserService;
import com.shiguang.user.UserVO;
import io.jsonwebtoken.JwtException;
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
        JwtService.TokenPayload payload;
        try {
            payload = jwtService.parse(refreshToken, JwtService.TokenType.REFRESH);
        } catch (JwtException ex) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        User user;
        try {
            user = userService.getById(payload.userId());
        } catch (BizException ex) {
            if (ex.getCode() == 404) {
                throw new BizException(401, "登录已过期，请重新登录");
            }
            throw ex;
        }
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.createToken(user.getId(), JwtService.TokenType.ACCESS);
        String refresh = jwtService.createToken(user.getId(), JwtService.TokenType.REFRESH);
        return new AuthResponse(access, refresh, "Bearer", accessExpireMinutes * 60, UserVO.from(user));
    }
}
