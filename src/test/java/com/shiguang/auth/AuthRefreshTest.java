package com.shiguang.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.common.BizException;
import com.shiguang.common.GlobalExceptionHandler;
import com.shiguang.user.User;
import com.shiguang.user.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthRefreshTest {

    private static final String SECRET = "auth-refresh-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz";

    @Mock
    private SmsCodeService smsCodeService;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));
        configureMvc(jwtService);
    }

    private void configureMvc(JwtService tokenService) {
        AuthService authService = new AuthService(smsCodeService, userService, tokenService);
        ReflectionTestUtils.setField(authService, "accessExpireMinutes", 30L);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(smsCodeService, authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void expiredRefreshReturnsUnauthorized() throws Exception {
        JwtService expiredJwtService = new JwtService(SECRET, Duration.ofMinutes(30), Duration.ofDays(-1));
        String token = expiredJwtService.createToken(17L, JwtService.TokenType.REFRESH);

        refresh(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(userService, smsCodeService);
    }

    @Test
    void accessTokenCannotBeUsedAsRefresh() throws Exception {
        refresh(jwtService.createToken(17L, JwtService.TokenType.ACCESS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(userService, smsCodeService);
    }

    @Test
    void malformedRefreshReturnsUnauthorized() throws Exception {
        refresh("not-a-jwt").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(userService, smsCodeService);
    }

    @Test
    void incorrectSignatureReturnsUnauthorized() throws Exception {
        JwtService otherSigner = new JwtService("different-" + SECRET, Duration.ofMinutes(30), Duration.ofDays(14));
        refresh(otherSigner.createToken(17L, JwtService.TokenType.REFRESH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(userService, smsCodeService);
    }

    @Test
    void deletedUserRefreshReturnsUnauthorized() throws Exception {
        when(userService.getById(17L)).thenThrow(new BizException(404, "用户不存在"));

        refresh(jwtService.createToken(17L, JwtService.TokenType.REFRESH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(smsCodeService);
    }

    @Test
    void databaseFailureStillReturns500() throws Exception {
        when(userService.getById(17L)).thenThrow(new IllegalStateException("database unavailable"));

        refresh(jwtService.createToken(17L, JwtService.TokenType.REFRESH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void tokenIssuingJwtFailureStillReturns500() throws Exception {
        JwtService failingIssuer = spy(jwtService);
        doThrow(new JwtException("signing unavailable")).when(failingIssuer).createToken(17L, JwtService.TokenType.ACCESS);
        configureMvc(failingIssuer);
        when(userService.getById(17L)).thenReturn(user());

        refresh(jwtService.createToken(17L, JwtService.TokenType.REFRESH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void otherBusinessCode403RemainsHttp400() throws Exception {
        when(userService.getById(17L)).thenThrow(new BizException(403, "禁止操作"));

        refresh(jwtService.createToken(17L, JwtService.TokenType.REFRESH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void validRefreshIssuesAccessAndRefreshForTheSameUser() throws Exception {
        when(userService.getById(17L)).thenReturn(user());

        MvcResult result = refresh(jwtService.createToken(17L, JwtService.TokenType.REFRESH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(jsonPath("$.data.user.id").value(17))
                .andReturn();
        JsonNode data = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(jwtService.parse(data.path("accessToken").asText(), JwtService.TokenType.ACCESS).userId()).isEqualTo(17L);
        assertThat(jwtService.parse(data.path("refreshToken").asText(), JwtService.TokenType.REFRESH).userId()).isEqualTo(17L);
        verifyNoInteractions(smsCodeService);
    }

    @Test
    void loginWrongCodeRemainsHttp400() throws Exception {
        when(smsCodeService.verifyCode("13800000000", "999999")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\",\"code\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }

    private User user() {
        User user = new User();
        user.setId(17L);
        user.setNickname("拾光");
        return user;
    }
}
