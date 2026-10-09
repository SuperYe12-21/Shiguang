package com.shiguang.auth;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.shiguang.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云号码认证服务·短信认证实现（app.sms.provider=pnvs）。
 *
 * 和标准短信服务（AliyunSmsProvider）的区别：签名和模板用阿里云「赠送」的，
 * 不需要申请资质、不走人工审核；代价是短信里的签名由阿里云指定，不是自己的品牌。
 *
 * 验证码仍然由我们生成、存 Redis、自己校验，这里只负责把码发出去，
 * 所以登录 / 注册 / 限流这些逻辑一行都不用改。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "pnvs")
public class PnvsSmsProvider implements SmsProvider {

    /** 短信是外部依赖，超时压到 3 秒，不能让它把登录接口的线程拖死 */
    private static final int TIMEOUT_MILLIS = 3000;
    private static final String SUCCESS_CODE = "OK";

    private final Client client;
    private final SmsProperties props;
    private final SmsProperties.Pnvs pnvs;

    public PnvsSmsProvider(SmsProperties props) {
        this.props = props;
        SmsProperties.Pnvs config = props.pnvs();
        if (config == null || isBlank(config.accessKeyId()) || isBlank(config.accessKeySecret())
                || isBlank(config.signName()) || isBlank(config.templateCode())) {
            throw new IllegalStateException("app.sms.provider=pnvs 时必须配置 access-key-id / access-key-secret / sign-name / template-code"
                    + "（sign-name 和 template-code 在号码认证服务控制台的「赠送签名配置」「赠送模板配置」里选）");
        }
        Config sdkConfig = new Config()
                .setAccessKeyId(config.accessKeyId())
                .setAccessKeySecret(config.accessKeySecret())
                .setEndpoint(config.resolvedEndpoint())
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setReadTimeout(TIMEOUT_MILLIS);
        try {
            this.client = new Client(sdkConfig);
        } catch (Exception e) {
            throw new IllegalStateException("初始化号码认证服务客户端失败", e);
        }
        this.pnvs = config;
        log.info("阿里云短信认证已启用: endpoint={} sign={} template={}",
                config.resolvedEndpoint(), config.signName(), config.templateCode());
    }

    @Override
    public void sendCode(String phone, String code) {
        // 验证码传的是我们自己生成的那一个（接口支持直接传具体验证码值），
        // 有效期和频控由我们自己的 Redis 管；Interval 只是把两边频率对齐，避免阿里云侧先拒。
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone)
                .setSignName(pnvs.signName())
                .setTemplateCode(pnvs.templateCode())
                .setTemplateParam(pnvs.resolvedTemplateParam(code, props.codeExpireMinutes()));
        if (!isBlank(pnvs.schemeName())) {
            request.setSchemeName(pnvs.schemeName());
        }
        if (props.cooldownSeconds() > 0) {
            request.setInterval((long) props.cooldownSeconds());
        }
        try {
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponseBody body = response == null ? null : response.getBody();
            boolean ok = body != null
                    && SUCCESS_CODE.equalsIgnoreCase(body.getCode())
                    && !Boolean.FALSE.equals(body.getSuccess());
            if (!ok) {
                String errorCode = body == null ? "EmptyResponse" : body.getCode();
                String message = body == null ? "短信服务无响应" : body.getMessage();
                log.warn("短信认证发送失败 phone={} code={} message={}", mask(phone), errorCode, message);
                throw new BizException(friendlyMessage(errorCode));
            }
            log.info("短信认证已发送 phone={} bizId={}", mask(phone),
                    body.getModel() == null ? null : body.getModel().getBizId());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("短信认证调用异常 phone={}: {}", mask(phone), e.toString());
            throw new BizException("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 把错误码翻译成用户能看懂的话，避免把 isv.xxx 原样丢给前端。
     * 没覆盖到的码走兜底文案，原始码只进日志（等真实遇到再补映射）。
     */
    private static String friendlyMessage(String errorCode) {
        if (errorCode == null) {
            return "验证码发送失败，请稍后重试";
        }
        return switch (errorCode) {
            case "isv.BUSINESS_LIMIT_CONTROL" -> "发送太频繁，请稍后再试";
            case "isv.MOBILE_NUMBER_ILLEGAL" -> "手机号格式不正确";
            case "isv.SMS_SIGNATURE_ILLEGAL", "isv.SMS_TEMPLATE_ILLEGAL" -> "短信签名或模板不可用，请联系管理员";
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
