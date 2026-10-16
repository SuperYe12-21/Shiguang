package com.shiguang.storage;

import com.shiguang.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadOwnershipService {

    private final StorageUploadMapper uploadMapper;
    private final StorageService storageService;

    @Transactional
    public PresignResult presign(Long ownerId, UploadPurpose purpose, String contentType, String extension) {
        requireOwner(ownerId);
        String mime = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        String suffix = purpose.extension(mime, extension);
        String key = purpose.directory() + "/" + LocalDate.now() + "/" + UUID.randomUUID() + suffix;
        uploadMapper.insert(record(ownerId, key, purpose, mime));
        return storageService.presignPut(key, mime);
    }

    @Transactional
    public void validateAndBind(Long ownerId, String key, UploadPurpose... purposes) {
        StorageUpload upload = requireOwned(ownerId, key);
        if (Arrays.stream(purposes).noneMatch(purpose -> purpose == upload.getPurpose())) {
            throw new BizException("上传文件用途不匹配");
        }
        validateObject(upload);
        upload.setBound(true);
        uploadMapper.updateById(upload);
    }

    @Transactional
    public void registerDerived(Long ownerId, String key, UploadPurpose purpose) {
        requireOwner(ownerId);
        requireKey(key);
        StorageUpload existing = uploadMapper.selectById(key);
        if (existing == null) {
            StorageService.ObjectStat stat = stat(key);
            StorageUpload upload = record(ownerId, key, purpose, mime(stat.contentType()));
            validateStat(upload, stat);
            try {
                uploadMapper.insert(upload);
            } catch (DuplicateKeyException exception) {
                requireOwned(ownerId, key);
            }
        }
        validateAndBind(ownerId, key, purpose);
    }

    @Transactional
    public void registerNormalized(Long ownerId, String source, String jpeg) {
        StorageUpload original = requireOwned(ownerId, source);
        if (original.getPurpose() == UploadPurpose.VIDEO || !Boolean.TRUE.equals(original.getBound())) {
            throw new BizException("图片来源未经验证");
        }
        registerDerived(ownerId, jpeg, original.getPurpose());
    }

    @Transactional
    public void deleteOwnedIfPresent(Long ownerId, String key) {
        if (ownerId == null || key == null || key.isBlank()) {
            return;
        }
        StorageUpload upload = uploadMapper.lockByObjectName(key);
        if (upload == null || !Objects.equals(ownerId, upload.getUploaderId())) {
            return;
        }
        if (uploadMapper.markDeletedIfUnreferenced(ownerId, key) == 0) {
            return;
        }
        Runnable cleanup = () -> {
            try {
                storageService.deleteObject(key);
            } catch (Exception exception) {
                log.warn("已授权对象清理失败: {} ({})", key, exception.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private StorageUpload requireOwned(Long ownerId, String key) {
        requireOwner(ownerId);
        requireKey(key);
        StorageUpload upload = uploadMapper.lockByObjectName(key);
        if (upload == null || !Objects.equals(ownerId, upload.getUploaderId()) || Boolean.TRUE.equals(upload.getDeleted())) {
            throw new BizException(403, "文件未登记、已删除或不属于当前用户");
        }
        return upload;
    }

    private void validateObject(StorageUpload upload) {
        validateStat(upload, stat(upload.getObjectName()));
    }

    private StorageService.ObjectStat stat(String key) {
        try {
            StorageService.ObjectStat stat = storageService.stat(key);
            if (stat == null) {
                throw new BizException("上传文件不存在");
            }
            return stat;
        } catch (StorageException exception) {
            throw new BizException(exception.isNotFound() ? "上传文件不存在" : "暂时无法验证上传文件，请稍后重试");
        }
    }

    private static void validateStat(StorageUpload upload, StorageService.ObjectStat stat) {
        String actual = mime(stat.contentType());
        if (stat.size() <= 0 || stat.size() > upload.getMaxSize()) {
            throw new BizException("上传文件为空或超过大小限制");
        }
        if (!upload.getPurpose().accepts(actual)
                || upload.getContentType() != null && !upload.getContentType().equals(actual)) {
            throw new BizException("上传文件实际格式与登记不一致");
        }
    }

    private static StorageUpload record(Long ownerId, String key, UploadPurpose purpose, String contentType) {
        StorageUpload upload = new StorageUpload();
        upload.setObjectName(key);
        upload.setUploaderId(ownerId);
        upload.setPurpose(purpose);
        upload.setContentType(contentType);
        upload.setMaxSize(purpose.maxSize());
        upload.setBound(false);
        upload.setDeleted(false);
        return upload;
    }

    private static String mime(String contentType) {
        return contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static void requireOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new BizException(401, "请先登录");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.length() > 500 || !key.matches("(?:images|videos|covers)/[A-Za-z0-9_./-]+")
                || key.contains("..") || key.contains("//")) {
            throw new BizException("文件对象名非法");
        }
    }
}
