package com.shiguang.storage;

/**
 * 存储层统一异常：屏蔽具体实现（MinIO / OSS）的错误类型，
 * 让上层的 MediaController、TranscodeWorker 不依赖任何 SDK 异常。
 */
public class StorageException extends RuntimeException {

    /** 对象不存在（对应 404），其余一律视为存储侧故障（对应 502） */
    private final boolean notFound;

    private StorageException(String message, boolean notFound, Throwable cause) {
        super(message, cause);
        this.notFound = notFound;
    }

    public static StorageException notFound(String objectName) {
        return new StorageException("对象不存在: " + objectName, true, null);
    }

    public static StorageException failure(String message, Throwable cause) {
        return new StorageException(message, false, cause);
    }

    public boolean isNotFound() {
        return notFound;
    }
}
