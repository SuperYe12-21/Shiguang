package com.shiguang.auth;

import com.shiguang.common.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    /** 固定验证码只能配合 mock 通道使用；生产误配了 SMS_MOCK_CODE 也一律忽略 */
    private static final String MOCK_PROVIDER = "mock";

    private static final String CODE_KEY = "sms:code:";
    private static final String COOLDOWN_KEY = "sms:cooldown:";
    private static final String HOUR_KEY = "sms:hour:";
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final SmsProvider smsProvider;
    private final SmsProperties props;

    @PostConstruct
    void warnOnRiskyConfig() {
        if (!props.mockProvider() && props.mockCode() != null && !props.mockCode().isBlank()) {
            log.warn("检测到 app.sms.provider={} 同时配置了 mock-code={}：真实短信通道下固定验证码已被忽略（这是防误配的保护）",
                    props.provider(), props.mockCode());
        }
    }

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

        String code = generateCode();
        redis.opsForValue().set(CODE_KEY + phone, code, Duration.ofMinutes(props.codeExpireMinutes()));
        if (props.cooldownSeconds() > 0) {
            redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(props.cooldownSeconds()));
        }
        try {
            smsProvider.sendCode(phone, code);
        } catch (RuntimeException e) {
            rollbackSend(phone, cooldownKey, hourKey);
            throw e instanceof BizException ? e : new BizException("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 短信没发出去就把这次发送的痕迹全部撤回：验证码、冷却、小时计数。
     * 否则用户既收不到码，还要被冷却 60 秒，等于白等一轮。
     */
    private void rollbackSend(String phone, String cooldownKey, String hourKey) {
        try {
            redis.delete(List.of(CODE_KEY + phone, cooldownKey));
            Long left = redis.opsForValue().decrement(hourKey);
            if (left != null && left < 0) {
                redis.opsForValue().set(hourKey, "0");
            }
        } catch (Exception e) {
            log.warn("短信失败回滚异常 phone={}: {}", phone, e.getMessage());
        }
    }

    private String generateCode() {
        if (MOCK_PROVIDER.equalsIgnoreCase(props.provider())
                && props.mockCode() != null && !props.mockCode().isBlank()) {
            return props.mockCode();
        }
        return String.format("%06d", RANDOM.nextInt(1_000_000));
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
