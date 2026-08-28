package com.shiguang.auth;

public interface SmsProvider {

    void sendCode(String phone, String code);
}