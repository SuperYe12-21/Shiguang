package com.shiguang.storage.image;

import com.shiguang.common.BizException;
import com.shiguang.storage.StorageService;
import com.shiguang.storage.UploadOwnershipService;
import com.shiguang.storage.UploadPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 图片入库前的标准化：把 HEIC/HEIF（iPhone 默认拍照格式）在服务端转成 JPEG。
 *
 * 前端直传只信 file.type 和扩展名，iOS 上会出现「文件名是 .jpeg、内容其实是 HEIC」的情况，
 * 直接入库就是一张 Chromium 打不开的坏图，所以这里读对象真实字节来判定格式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageNormalizer {

    /** 判定 ftyp 盒和 brand 用不了这么多，留点余量 */
    private static final int SNIFF_BYTES = 32;

    /** ISO-BMFF 里的 HEIF 家族 brand；avif 不在此列（Chromium 自己能显示） */
    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "heif", "mif1", "msf1");

    private final StorageService storageService;
    private final HeifConverter heifConverter;
    private final UploadOwnershipService uploadOwnershipService;

    /** 批量标准化，保持原有顺序 */
    public List<String> normalizeAll(Long ownerId, List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return objectNames;
        }
        return objectNames.stream().map(objectName -> normalize(ownerId, objectName, UploadPurpose.IMAGE)).toList();
    }

    /** 返回可直接入库的对象名：HEIC/HEIF 换成转好的 JPEG，其余原样返回 */
    public String normalize(Long ownerId, String objectName, UploadPurpose... purposes) {
        if (objectName == null || objectName.isBlank()) {
            return objectName;
        }
        uploadOwnershipService.validateAndBind(ownerId, objectName, purposes);
        if (!isHeif(objectName)) {
            return objectName;
        }
        return convertToJpeg(ownerId, objectName);
    }

    /** 读头部字节判格式；探测失败就放行，存储抖动不该挡用户发内容 */
    private boolean isHeif(String objectName) {
        try (InputStream in = storageService.open(objectName, 0, SNIFF_BYTES)) {
            return isHeifHeader(in.readNBytes(SNIFF_BYTES));
        } catch (Exception e) {
            log.warn("图片格式探测失败，按原样放行: {} ({})", objectName, e.getMessage());
            return false;
        }
    }

    /** ftyp 盒固定在第 4~8 字节，brand 从第 8 字节起每 4 字节一个 */
    static boolean isHeifHeader(byte[] head) {
        if (head == null || head.length < 12) {
            return false;
        }
        if (head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p') {
            return false;
        }
        for (int offset = 8; offset + 4 <= head.length; offset += 4) {
            String brand = new String(head, offset, 4, StandardCharsets.US_ASCII);
            if (HEIF_BRANDS.contains(brand)) {
                return true;
            }
        }
        return false;
    }

    private String convertToJpeg(Long ownerId, String objectName) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("shiguang-heif-");
            Path source = workDir.resolve("source.heic");
            try (InputStream in = storageService.getObject(objectName)) {
                Files.copy(in, source);
            }
            Path jpeg = workDir.resolve("converted.jpg");
            heifConverter.toJpeg(source, jpeg);

            String converted = "images/" + LocalDate.now() + "/" + UUID.randomUUID() + ".jpg";
            storageService.putObject(converted, jpeg.toFile(), "image/jpeg");
            uploadOwnershipService.registerNormalized(ownerId, objectName, converted);
            deleteOriginal(ownerId, objectName);
            log.info("HEIC 已转成 JPEG: {} -> {}", objectName, converted);
            return converted;
        } catch (Exception e) {
            log.error("HEIC 转换失败: {}", objectName, e);
            throw new BizException(1, "这张图片是 HEIC 格式，无法处理，请改用 JPG 或 PNG");
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** JPEG 已经入库，原文件留着只会占空间 */
    private void deleteOriginal(Long ownerId, String objectName) {
        try {
            uploadOwnershipService.deleteOwnedIfPresent(ownerId, objectName);
        } catch (Exception e) {
            log.warn("HEIC 原文件清理失败: {} ({})", objectName, e.getMessage());
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    log.warn("清理临时文件失败: {}", path);
                }
            });
        } catch (Exception e) {
            log.warn("清理临时目录失败: {}", dir);
        }
    }
}
