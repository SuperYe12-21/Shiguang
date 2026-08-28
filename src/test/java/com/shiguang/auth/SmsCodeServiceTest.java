package com.shiguang.auth;

import com.shiguang.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.sms.cooldown-seconds=60",
        "app.sms.hourly-limit=5",
        "app.sms.code-expire-minutes=5"
})
class SmsCodeServiceTest {

    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private StringRedisTemplate redis;

    private final String phone = "13900001111";

    @BeforeEach
    void cleanRedis() {
        redis.keys("sms:*").forEach(redis::delete);
    }

    @Test
    void sendCodeStoresCodeAndVerifyPasses() {
        smsCodeService.sendCode(phone);
        assertThat(smsCodeService.verifyCode(phone, "123456")).isTrue();
    }

    @Test
    void verifyWithWrongCodeFails() {
        smsCodeService.sendCode(phone);
        assertThat(smsCodeService.verifyCode(phone, "999999")).isFalse();
    }

    @Test
    void verifyConsumesCodeOnSuccess() {
        smsCodeService.sendCode(phone);
        assertThat(smsCodeService.verifyCode(phone, "123456")).isTrue();
        assertThat(smsCodeService.verifyCode(phone, "123456")).isFalse();
    }

    @Test
    void resendWithinCooldownIsRejected() {
        smsCodeService.sendCode(phone);
        assertThatThrownBy(() -> smsCodeService.sendCode(phone))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }
}