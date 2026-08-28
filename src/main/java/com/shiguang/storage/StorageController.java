package com.shiguang.storage;

import com.shiguang.common.BizException;
import com.shiguang.common.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class StorageController {

    private static final Set<String> ALLOWED_TYPES = Set.of("VIDEO", "IMAGE", "COVER");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/webm",
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final StorageService storageService;

    public record UploadPresignRequest(
            @NotBlank(message = "文件类型不能为空") String type,
            @NotBlank(message = "contentType 不能为空") String contentType,
            @Size(max = 10, message = "扩展名过长") String extension) {
    }

    @PostMapping("/presign")
    public R<PresignResult> presign(@Valid @RequestBody UploadPresignRequest request) {
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BizException("不支持的资源类型: " + request.type());
        }
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BizException("不支持的文件格式: " + request.contentType());
        }
        String dir = switch (type) {
            case "VIDEO" -> "videos";
            case "IMAGE" -> "images";
            default -> "covers";
        };
        String objectName = dir + "/" + LocalDate.now() + "/" + UUID.randomUUID() + normalizeExtension(request.extension(), contentType);
        return R.ok(storageService.presignPut(objectName, contentType));
    }

    private String normalizeExtension(String extension, String contentType) {
        if (extension != null && !extension.isBlank()) {
            String ext = extension.trim().toLowerCase(Locale.ROOT);
            return ext.startsWith(".") ? ext : "." + ext;
        }
        return switch (contentType) {
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "video/x-msvideo" -> ".avi";
            case "video/webm" -> ".webm";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }
}