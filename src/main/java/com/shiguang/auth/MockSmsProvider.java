package com.shiguang.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsProvider implements SmsProvider {

    @Override
    public void sendCode(String phone, String code) {
        log.info("【拾光短信-Mock】手机号 {} 的验证码：{}（5分钟内有效）", phone, code);
    }
}