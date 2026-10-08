package com.shiguang.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 阿里云 OSS 实现（app.storage.type=oss）。
 *
 * 分成两个客户端（都走 https）：
 *  - presignClient：公网 endpoint，只用来生成浏览器直传的预签名地址（内网地址浏览器访问不到）
 *  - dataClient：内网 endpoint（没配就用公网），服务端读写对象走这条，同地域免流量费
 *
 * 媒体读取不走这里：publicUrl() 直接返回 OSS/CDN 公网地址，字节流不经过应用服务器。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "oss")
public class OssStorageService implements StorageService {

    private static final String NOT_FOUND_CODE = "NoSuchKey";

    private final OSS presignClient;
    private final OSS dataClient;
    private final String bucket;
    private final String publicBaseUrl;
    private final Duration presignExpiry;

    public OssStorageService(StorageProperties props) {
        StorageProperties.Oss oss = props.oss();
        if (oss == null || isBlank(oss.endpoint()) || isBlank(oss.bucket())
                || isBlank(oss.accessKeyId()) || isBlank(oss.accessKeySecret())) {
            throw new IllegalStateException("app.storage.type=oss 时必须配置 OSS 的 endpoint / bucket / access-key-id / access-key-secret");
        }
        this.presignClient = new OSSClientBuilder().build(oss.resolvedPresignEndpoint(), oss.accessKeyId(), oss.accessKeySecret());
        this.dataClient = oss.resolvedDataEndpoint().equals(oss.resolvedPresignEndpoint())
                ? this.presignClient
                : new OSSClientBuilder().build(oss.resolvedDataEndpoint(), oss.accessKeyId(), oss.accessKeySecret());
        this.bucket = oss.bucket();
        this.publicBaseUrl = oss.resolvedPublicBaseUrl();
        this.presignExpiry = Duration.ofMinutes(props.presignExpiryMinutes());
        log.info("OSS 存储已启用: bucket={} 数据端点={} 公开前缀={}", bucket, oss.resolvedInternalEndpoint(), publicBaseUrl);
    }

    @Override
    public PresignResult presignPut(String objectName, String contentType) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectName, HttpMethod.PUT);
            request.setExpiration(new Date(System.currentTimeMillis() + presignExpiry.toMillis()));
            // OSS 的 V1 签名把 Content-Type 算进签名字符串：必须签进去，浏览器直传时带同一个头才不会被判 SignatureDoesNotMatch
            if (contentType != null && !contentType.isBlank()) {
                request.setContentType(contentType);
            }
            String url = presignClient.generatePresignedUrl(request).toString();
            return new PresignResult(objectName, url);
        } catch (Exception e) {
            throw StorageException.failure("生成上传地址失败: " + objectName, e);
        }
    }

    @Override
    public String publicUrl(String objectName) {
        return publicBaseUrl + "/" + Arrays.stream(objectName.split("/"))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    @Override
    public void putObject(String objectName, File file, String contentType) {
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType);
            meta.setContentLength(file.length());
            dataClient.putObject(new PutObjectRequest(bucket, objectName, file, meta));
        } catch (Exception e) {
            throw StorageException.failure("上传文件失败: " + objectName, e);
        }
    }

    @Override
    public InputStream getObject(String objectName) {
        try {
            return dataClient.getObject(bucket, objectName).getObjectContent();
        } catch (OSSException e) {
            throw translate(e, objectName);
        } catch (Exception e) {
            throw StorageException.failure("读取文件失败: " + objectName, e);
        }
    }

    @Override
    public ObjectStat stat(String objectName) {
        try {
            ObjectMetadata meta = dataClient.getObjectMetadata(bucket, objectName);
            return new ObjectStat(meta.getContentLength(), meta.getContentType());
        } catch (OSSException e) {
            throw translate(e, objectName);
        } catch (Exception e) {
            throw StorageException.failure("读取文件信息失败: " + objectName, e);
        }
    }

    @Override
    public InputStream open(String objectName, long offset, long length) {
        try {
            GetObjectRequest request = new GetObjectRequest(bucket, objectName);
            if (length > 0) {
                request.setRange(offset, offset + length - 1);
            }
            return dataClient.getObject(request).getObjectContent();
        } catch (OSSException e) {
            throw translate(e, objectName);
        } catch (Exception e) {
            throw StorageException.failure("读取文件失败: " + objectName, e);
        }
    }

    @Override
    public void deleteObject(String objectName) {
        try {
            dataClient.deleteObject(bucket, objectName);
        } catch (OSSException e) {
            log.debug("对象删除失败（按不存在处理）: {} ({})", objectName, e.getErrorCode());
        } catch (Exception e) {
            throw StorageException.failure("删除文件失败: " + objectName, e);
        }
    }

    private static StorageException translate(OSSException e, String objectName) {
        return NOT_FOUND_CODE.equals(e.getErrorCode())
                ? StorageException.notFound(objectName)
                : StorageException.failure("OSS 操作失败: " + objectName, e);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
