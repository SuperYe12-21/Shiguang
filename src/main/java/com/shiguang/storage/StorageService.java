package com.shiguang.storage;

import java.io.File;
import java.io.InputStream;

public interface StorageService {

    record ObjectStat(long size, String contentType) {
    }

    PresignResult presignPut(String objectName, String contentType);

    /** 对象名 -> 浏览器可直接访问的地址（MinIO 走 /api/media 代理，OSS 走公网域名） */
    String publicUrl(String objectName);

    void putObject(String objectName, File file, String contentType);

    InputStream getObject(String objectName);

    ObjectStat stat(String objectName);

    InputStream open(String objectName, long offset, long length);

    void deleteObject(String objectName);
}
