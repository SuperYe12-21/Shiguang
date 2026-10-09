package com.shiguang.admin;

/** 当前登录人是否管理员，供前端决定管理入口显隐 */
public record AdminMeVO(boolean admin) {
}
