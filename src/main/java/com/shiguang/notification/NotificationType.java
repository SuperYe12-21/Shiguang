package com.shiguang.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知类型。每种类型归属一个分类，分类对应消息页的一个 Tab。
 * LIKE_* 按作品/评论折叠，其余每一条独立成行。
 */
public enum NotificationType {

    LIKE_POST(Category.LIKE),
    LIKE_COMMENT(Category.LIKE),
    COMMENT_POST(Category.COMMENT),
    REPLY_COMMENT(Category.COMMENT),
    FOLLOW(Category.FOLLOW);

    private final String category;

    NotificationType(String category) {
        this.category = category;
    }

    public String category() {
        return category;
    }

    public static List<NotificationType> ofCategory(String category) {
        List<NotificationType> matched = new ArrayList<>();
        for (NotificationType type : values()) {
            if (type.category.equals(category)) {
                matched.add(type);
            }
        }
        return matched;
    }

    public static boolean isValidCategory(String category) {
        for (NotificationType type : values()) {
            if (type.category.equals(category)) {
                return true;
            }
        }
        return false;
    }

    /** 由枚举名反查分类；未知类型返回 null */
    public static String categoryOf(String typeName) {
        for (NotificationType type : values()) {
            if (type.name().equals(typeName)) {
                return type.category;
            }
        }
        return null;
    }

    public static final class Category {

        public static final String LIKE = "like";
        public static final String COMMENT = "comment";
        public static final String FOLLOW = "follow";

        private Category() {
        }
    }
}
