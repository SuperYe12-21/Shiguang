package com.shiguang.common;

/** 搜索关键词处理：归一化 + LIKE 通配符转义 */
public final class SearchText {

    private static final int MAX_LEN = 50;

    private SearchText() {
    }

    /** 归一化：null 安全、trim、限长；空串表示无有效关键词 */
    public static String normalize(String keyword) {
        if (keyword == null) {
            return "";
        }
        String k = keyword.trim();
        if (k.length() > MAX_LEN) {
            k = k.substring(0, MAX_LEN);
        }
        return k;
    }

    /** 转义 LIKE 通配符（MySQL 默认反斜杠转义） */
    public static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
