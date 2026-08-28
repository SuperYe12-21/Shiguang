package com.shiguang.storage;

import java.io.File;
import java.io.InputStream;

public interface StorageService {

    PresignResult presignPut(String objectName, String contentType);

    String presignedGetUrl(String objectName);

    void putObject(String objectName, File file, String contentType);

    InputStream getObject(String objectName);

    void deleteObject(String objectName);
}
