package com.shiguang.content.transcode;

import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostStatus;
import com.shiguang.storage.StorageService;
import com.shiguang.storage.UploadOwnershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeWorker {

    private final PostMapper postMapper;
    private final StorageService storageService;
    private final FfmpegRunner ffmpegRunner;
    private final TranscodeTaskService taskService;
    private final UploadOwnershipService uploadOwnershipService;

    @RabbitListener(queues = "${app.transcode.queue}")
    public void handle(TranscodeJob job) {
        Long postId = job.postId();
        Path workDir = null;
        TranscodeTaskService.Claim claim = null;
        String videoObject = null;
        String coverObject = null;
        boolean completionAttempted = false;
        try {
            Post post = postMapper.selectById(postId);
            if (post == null) {
                log.warn("转码任务指向不存在的作品: {}", postId);
                taskService.cancel(postId);
                return;
            }
            if (post.getStatus() != PostStatus.PROCESSING) {
                log.info("作品 {} 已不在 PROCESSING 状态，跳过转码", postId);
                taskService.cancel(postId);
                return;
            }
            claim = taskService.claim(postId);
            if (claim == null) {
                return;
            }
            workDir = Files.createTempDirectory("shiguang-transcode-" + postId);
            Path source = workDir.resolve("source" + extensionOf(post.getSourceObject()));
            try (InputStream in = storageService.getObject(post.getSourceObject())) {
                Files.copy(in, source);
            }

            Path mp4 = workDir.resolve("video.mp4");
            Path coverJpg = workDir.resolve("cover.jpg");
            ffmpegRunner.transcodeToMp4(source, mp4);
            ffmpegRunner.extractCover(source, coverJpg);

            videoObject = "videos/" + postId + "/" + UUID.randomUUID() + ".mp4";
            coverObject = "covers/" + postId + "/" + UUID.randomUUID() + ".jpg";
            storageService.putObject(videoObject, mp4.toFile(), "video/mp4");
            storageService.putObject(coverObject, coverJpg.toFile(), "image/jpeg");
            completionAttempted = true;
            if (!taskService.complete(claim, videoObject, coverObject)) {
                deleteOutput(videoObject);
                deleteOutput(coverObject);
                return;
            }
            log.info("作品 {} 转码完成: {} / {}", postId, videoObject, coverObject);
            deleteSourceIfUnused(post, postId);
        } catch (Exception e) {
            log.error("作品 {} 转码失败", postId, e);
            if (claim != null) {
                taskService.fail(claim, e.getMessage());
            }
            if (!completionAttempted) {
                deleteOutput(videoObject);
                deleteOutput(coverObject);
            }
        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    private static String extensionOf(String objectName) {
        if (objectName == null) {
            return "";
        }
        int dot = objectName.lastIndexOf('.');
        return dot >= 0 ? objectName.substring(dot) : "";
    }

    private void deleteOutput(String objectName) {
        if (objectName == null) {
            return;
        }
        try {
            storageService.deleteObject(objectName);
        } catch (Exception exception) {
            log.warn("转码临时产物清理失败: {}", objectName);
        }
    }

    private void deleteSourceIfUnused(Post post, Long postId) {
        String sourceObject = post.getSourceObject();
        if (sourceObject == null || sourceObject.isBlank()) {
            return;
        }
        try {
            uploadOwnershipService.deleteOwnedIfPresent(post.getUserId(), sourceObject);
            log.info("作品 {} 原始文件已清理: {}", postId, sourceObject);
        } catch (Exception e) {
            log.warn("作品 {} 原始文件清理失败: {}", postId, e.getMessage());
        }
    }

    private static void deleteRecursively(Path dir) {
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
