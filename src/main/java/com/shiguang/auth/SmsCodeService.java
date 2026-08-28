package com.shiguang.auth;

import com.shiguang.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final String CODE_KEY = "sms:code:";
    private static final String COOLDOWN_KEY = "sms:cooldown:";
    private static final String HOUR_KEY = "sms:hour:";
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final SmsProvider smsProvider;
    private final SmsProperties props;

    public void sendCode(String phone) {
        String cooldownKey = COOLDOWN_KEY + phone;
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new BizException("发送太频繁，请稍后再试");
        }

        String hourKey = HOUR_KEY + phone + ":" + LocalDateTime.now().format(HOUR_FORMAT);
        Long count = redis.opsForValue().increment(hourKey);
        if (count != null && count == 1L) {
            redis.expire(hourKey, Duration.ofHours(1));
        }
        if (count != null && count > props.hourlyLimit()) {
            throw new BizException("该手机号发送次数已达上限，请明天再试");
        }

        String code = props.mockCode() == null || props.mockCode().isBlank()
                ? String.format("%06d", RANDOM.nextInt(1_000_000))
                : props.mockCode();
        redis.opsForValue().set(CODE_KEY + phone, code, Duration.ofMinutes(props.codeExpireMinutes()));
        if (props.cooldownSeconds() > 0) {
            redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(props.cooldownSeconds()));
        }
        smsProvider.sendCode(phone, code);
    }

    public boolean verifyCode(String phone, String code) {
        String key = CODE_KEY + phone;
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            return false;
        }
        redis.delete(key);
        return true;
    }
}