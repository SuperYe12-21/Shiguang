package com.shiguang.content.transcode;

import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.storage.StorageService;
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
    private final PostService postService;
    private final StorageService storageService;
    private final FfmpegRunner ffmpegRunner;

    @RabbitListener(queues = "${app.transcode.queue}")
    public void handle(TranscodeJob job) {
        Long postId = job.postId();
        Path workDir = null;
        try {
            Post post = postMapper.selectById(postId);
            if (post == null) {
                log.warn("转码任务指向不存在的作品: {}", postId);
                return;
            }
            if (post.getStatus() != PostStatus.PROCESSING) {
                log.info("作品 {} 已不在 PROCESSING 状态，跳过转码", postId);
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

            String videoObject = "videos/" + postId + "/" + UUID.randomUUID() + ".mp4";
            String coverObject = "covers/" + postId + "/" + UUID.randomUUID() + ".jpg";
            storageService.putObject(videoObject, mp4.toFile(), "video/mp4");
            storageService.putObject(coverObject, coverJpg.toFile(), "image/jpeg");
            postService.markPublished(postId, videoObject, coverObject);
            log.info("作品 {} 转码完成: {} / {}", postId, videoObject, coverObject);
            deleteSourceIfUnused(post, postId);
        } catch (Exception e) {
            log.error("作品 {} 转码失败", postId, e);
            postService.markFailed(postId, e.getMessage());
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

    private void deleteSourceIfUnused(Post post, Long postId) {
        String sourceObject = post.getSourceObject();
        if (sourceObject == null || sourceObject.isBlank()) {
            return;
        }
        try {
            storageService.deleteObject(sourceObject);
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