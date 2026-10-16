package com.shiguang.storage;

import com.shiguang.common.BizException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum UploadPurpose {
    VIDEO("videos", 500L * 1024 * 1024),
    IMAGE("images", 20L * 1024 * 1024),
    COVER("covers", 20L * 1024 * 1024);

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("video/mp4", "mp4"), Map.entry("video/quicktime", "mov"),
            Map.entry("video/x-msvideo", "avi"), Map.entry("video/webm", "webm"),
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"), Map.entry("image/gif", "gif"),
            Map.entry("image/heic", "heic"), Map.entry("image/heif", "heif"));

    private final String directory;
    private final long maxSize;

    UploadPurpose(String directory, long maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;
    }

    public String directory() {
        return directory;
    }

    public long maxSize() {
        return maxSize;
    }

    public boolean accepts(String contentType) {
        return contentType != null && EXTENSIONS.containsKey(contentType)
                && contentType.startsWith(this == VIDEO ? "video/" : "image/");
    }

    public String extension(String contentType, String extension) {
        if (!accepts(contentType)) {
            throw new BizException("文件格式与上传用途不匹配");
        }
        String canonical = EXTENSIONS.get(contentType);
        if (extension == null || extension.isBlank()) {
            return "." + canonical;
        }
        String supplied = extension.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        Set<String> aliases = switch (contentType) {
            case "image/jpeg" -> Set.of("jpg", "jpeg");
            case "image/heic", "image/heif" -> Set.of("heic", "heif");
            default -> Set.of(canonical);
        };
        if (!aliases.contains(supplied)) {
            throw new BizException("扩展名与文件格式不匹配");
        }
        return "." + supplied;
    }
}
