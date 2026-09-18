package com.shiguang.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;
    private final Duration presignExpiry;

    public MinioStorageService(StorageProperties props) {
        StorageProperties.Minio minio = props.minio();
        this.client = MinioClient.builder()
                .endpoint(minio.endpoint())
                .credentials(minio.accessKey(), minio.secretKey())
                .build();
        this.bucket = minio.bucket();
        this.presignExpiry = Duration.ofMinutes(props.presignExpiryMinutes());
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket init failed (will retry on first use): {}", e.getMessage());
        }
    }

    @Override
    public PresignResult presignPut(String objectName, String contentType) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry((int) presignExpiry.toSeconds())
                    .build());
            return new PresignResult(objectName, url);
        } catch (Exception e) {
            throw new IllegalStateException("生成上传地址失败", e);
        }
    }

    @Override
    public String presignedGetUrl(String objectName) {
        return "/api/media/" + Arrays.stream(objectName.split("/"))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    @Override
    public void putObject(String objectName, File file, String contentType) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(in, file.length(), -1)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("上传文件失败: " + objectName, e);
        }
    }

    @Override
    public InputStream getObject(String objectName) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            throw new IllegalStateException("读取文件失败: " + objectName, e);
        }
    }

    @Override
    public ObjectStat stat(String objectName) throws ErrorResponseException {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return new ObjectStat(stat.size(), stat.contentType());
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取文件信息失败: " + objectName, e);
        }
    }

    @Override
    public InputStream open(String objectName, long offset, long length) throws ErrorResponseException {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .offset(offset)
                    .length(length)
                    .build());
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取文件失败: " + objectName, e);
        }
    }

    @Override
    public void deleteObject(String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (ErrorResponseException e) {
            log.debug("对象不存在，跳过删除: {}", objectName);
        } catch (Exception e) {
            throw new IllegalStateException("删除文件失败: " + objectName, e);
        }
    }
}
