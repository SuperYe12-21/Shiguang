package com.shiguang.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storage_upload")
public class StorageUpload {

    @TableId(type = IdType.INPUT)
    private String objectName;
    private Long uploaderId;
    private UploadPurpose purpose;
    private String contentType;
    private Long maxSize;
    private Boolean bound;
    private Boolean deleted;
    private LocalDateTime createdAt;
}
