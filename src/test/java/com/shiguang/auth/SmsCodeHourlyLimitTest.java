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
        "app.sms.cooldown-seconds=0",
        "app.sms.hourly-limit=3",
        "app.sms.code-expire-minutes=5"
})
class SmsCodeHourlyLimitTest {

    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private StringRedisTemplate redis;

    private final String phone = "13900002222";

    @BeforeEach
    void cleanRedis() {
        redis.keys("sms:*").forEach(redis::delete);
    }

    @Test
    void fourthSendWithinHourIsRejected() {
        smsCodeService.sendCode(phone);
        smsCodeService.sendCode(phone);
        smsCodeService.sendCode(phone);
        assertThatThrownBy(() -> smsCodeService.sendCode(phone))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("次数");
    }
}