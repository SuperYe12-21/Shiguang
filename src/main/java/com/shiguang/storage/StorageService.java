package com.shiguang.storage;

import io.minio.errors.ErrorResponseException;

import java.io.File;
import java.io.InputStream;

public interface StorageService {

    record ObjectStat(long size, String contentType) {
    }

    PresignResult presignPut(String objectName, String contentType);

    String presignedGetUrl(String objectName);

    void putObject(String objectName, File file, String contentType);

    InputStream getObject(String objectName);

    ObjectStat stat(String objectName) throws ErrorResponseException;

    InputStream open(String objectName, long offset, long length) throws ErrorResponseException;

    void deleteObject(String objectName);
}
