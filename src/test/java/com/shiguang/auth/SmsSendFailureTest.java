package com.shiguang.auth;

import com.shiguang.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实短信通道（provider=aliyun）下的两条保护：
 *  1. 配了 mock-code 也不能让固定验证码生效；
 *  2. 短信服务调用失败时，验证码 / 冷却 / 小时计数全部回滚，用户可以立刻重试。
 */
@SpringBootTest(properties = {
        "app.sms.cooldown-seconds=60",
        "app.sms.hourly-limit=5",
        "app.sms.code-expire-minutes=5",
        "app.sms.provider=aliyun",
        "app.sms.mock-code=123456",
        "app.sms.aliyun.access-key-id=test-ak",
        "app.sms.aliyun.access-key-secret=test-sk",
        "app.sms.aliyun.sign-name=ShiguangTest",
        "app.sms.aliyun.template-code=SMS_TEST"
})
class SmsSendFailureTest {

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        RecordingSmsProvider recordingSmsProvider() {
            return new RecordingSmsProvider();
        }
    }

    /** 记录发出去的验证码，并可让下一次发送失败（模拟短信服务不可用） */
    static class RecordingSmsProvider implements SmsProvider {

        final List<String> codes = new ArrayList<>();
        final AtomicBoolean failNext = new AtomicBoolean(false);

        @Override
        public void sendCode(String phone, String code) {
            if (failNext.getAndSet(false)) {
                throw new IllegalStateException("provider down");
            }
            codes.add(code);
        }
    }

    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private RecordingSmsProvider provider;

    @Autowired
    private StringRedisTemplate redis;

    private final String phone = "13900002222";

    @BeforeEach
    void reset() {
        redis.keys("sms:*").forEach(redis::delete);
        provider.codes.clear();
        provider.failNext.set(false);
    }

    @Test
    void failedSendRollsBackCodeAndCooldown() {
        provider.failNext.set(true);
        assertThatThrownBy(() -> smsCodeService.sendCode(phone))
                .isInstanceOf(BizException.class);

        assertThat(redis.hasKey("sms:code:" + phone)).isFalse();
        assertThat(redis.hasKey("sms:cooldown:" + phone)).isFalse();

        // 回滚后可以立刻重试，不用等冷却
        smsCodeService.sendCode(phone);
        assertThat(provider.codes).hasSize(1);
    }

    @Test
    void mockCodeIsIgnoredOnRealProvider() {
        smsCodeService.sendCode(phone);
        assertThat(provider.codes).hasSize(1);
        assertThat(provider.codes.get(0)).isNotEqualTo("123456");
        assertThat(provider.codes.get(0)).matches("\\d{6}");
        assertThat(smsCodeService.verifyCode(phone, provider.codes.get(0))).isTrue();
    }

    @Test
    void hourlyCounterIsRolledBackOnFailure() {
        String hourKey = "sms:hour:" + phone + ":" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

        smsCodeService.sendCode(phone);
        assertThat(redis.opsForValue().get(hourKey)).isEqualTo("1");

        provider.failNext.set(true);
        assertThatThrownBy(() -> smsCodeService.sendCode(phone)).isInstanceOf(BizException.class);

        assertThat(redis.opsForValue().get(hourKey)).isEqualTo("1");
    }
}
