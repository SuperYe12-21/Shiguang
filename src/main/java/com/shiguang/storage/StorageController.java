package com.shiguang.storage;

import com.shiguang.common.BizException;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class StorageController {

    private final UploadOwnershipService uploadOwnershipService;

    public record UploadPresignRequest(
            @NotBlank(message = "文件类型不能为空") String type,
            @NotBlank(message = "contentType 不能为空") String contentType,
            @Size(max = 10, message = "扩展名过长") String extension) {
    }

    @PostMapping("/presign")
    public R<PresignResult> presign(@Valid @RequestBody UploadPresignRequest request) {
        UploadPurpose purpose;
        try {
            purpose = UploadPurpose.valueOf(request.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BizException("不支持的资源类型: " + request.type());
        }
        return R.ok(uploadOwnershipService.presign(SecurityUtils.getUserId(), purpose, request.contentType(), request.extension()));
    }
}
