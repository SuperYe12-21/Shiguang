package com.shiguang.auth;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.shiguang.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信实现（app.sms.provider=aliyun）。
 *
 * 连接/读取超时都压到 3 秒：短信接口是外部依赖，不能让它把登录接口的线程拖死。
 * 验证码只在日志里脱敏输出，绝不落盘明文验证码。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "aliyun")
public class AliyunSmsProvider implements SmsProvider {

    private static final int TIMEOUT_MILLIS = 3000;
    private static final String SUCCESS_CODE = "OK";

    private final Client client;
    private final String signName;
    private final String templateCode;

    public AliyunSmsProvider(SmsProperties props) {
        SmsProperties.Aliyun aliyun = props.aliyun();
        if (aliyun == null || isBlank(aliyun.accessKeyId()) || isBlank(aliyun.accessKeySecret())
                || isBlank(aliyun.signName()) || isBlank(aliyun.templateCode())) {
            throw new IllegalStateException(
                    "app.sms.provider=aliyun 时必须配置 access-key-id / access-key-secret / sign-name / template-code");
        }
        Config config = new Config()
                .setAccessKeyId(aliyun.accessKeyId())
                .setAccessKeySecret(aliyun.accessKeySecret())
                .setEndpoint(aliyun.resolvedEndpoint())
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setReadTimeout(TIMEOUT_MILLIS);
        try {
            this.client = new Client(config);
        } catch (Exception e) {
            throw new IllegalStateException("初始化阿里云短信客户端失败", e);
        }
        this.signName = aliyun.signName();
        this.templateCode = aliyun.templateCode();
        log.info("阿里云短信已启用: endpoint={} sign={} template={}", aliyun.resolvedEndpoint(), signName, templateCode);
    }

    @Override
    public void sendCode(String phone, String code) {
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        try {
            SendSmsResponse response = client.sendSms(request);
            SendSmsResponseBody body = response == null ? null : response.getBody();
            if (body == null || !SUCCESS_CODE.equalsIgnoreCase(body.getCode())) {
                String errorCode = body == null ? "EmptyResponse" : body.getCode();
                String message = body == null ? "短信服务无响应" : body.getMessage();
                log.warn("阿里云短信发送失败 phone={} code={} message={}", mask(phone), errorCode, message);
                throw new BizException(friendlyMessage(errorCode));
            }
            log.info("阿里云短信已发送 phone={} bizId={}", mask(phone), body.getBizId());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("阿里云短信调用异常 phone={}: {}", mask(phone), e.toString());
            throw new BizException("验证码发送失败，请稍后重试");
        }
    }

    /** 把阿里云的错误码翻译成用户能看懂的话，避免把 isv.xxx 这种原样丢给前端 */
    private static String friendlyMessage(String errorCode) {
        if (errorCode == null) {
            return "验证码发送失败，请稍后重试";
        }
        return switch (errorCode) {
            case "isv.BUSINESS_LIMIT_CONTROL" -> "发送太频繁，请稍后再试";
            case "isv.MOBILE_NUMBER_ILLEGAL" -> "手机号格式不正确";
            case "isv.SMS_SIGNATURE_ILLEGAL", "isv.SMS_TEMPLATE_ILLEGAL" -> "短信服务未配置好，请联系管理员";
            case "isv.AMOUNT_NOT_ENOUGH", "isv.ACCOUNT_NOT_ENOUGH" -> "短信余额不足，请联系管理员";
            default -> "验证码发送失败，请稍后重试";
        };
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
