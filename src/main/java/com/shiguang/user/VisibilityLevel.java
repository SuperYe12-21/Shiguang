package com.shiguang.user;

/** 列表可见性三档 */
public enum VisibilityLevel {

    /** 所有人可见 */
    PUBLIC,

    /** 仅好友（互相关注）可见 */
    FRIENDS,

    /** 仅自己可见 */
    PRIVATE;

    public static VisibilityLevel parse(String raw, VisibilityLevel fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return VisibilityLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
