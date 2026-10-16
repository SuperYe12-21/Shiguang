package com.shiguang.content.transcode;

import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostStatus;
import com.shiguang.storage.StorageService;
import com.shiguang.storage.UploadOwnershipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TranscodeWorkerTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private FfmpegRunner ffmpegRunner;

    @Mock
    private TranscodeTaskService taskService;

    @Mock
    private UploadOwnershipService uploadOwnershipService;

    @InjectMocks
    private TranscodeWorker worker;

    private static Post processingPost() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(7L);
        post.setType(com.shiguang.content.PostType.VIDEO);
        post.setStatus(PostStatus.PROCESSING);
        post.setSourceObject("videos/src.mp4");
        return post;
    }

    @Test
    void success_transcodesUploadsAndPublishes() throws Exception {
        when(postMapper.selectById(1L)).thenReturn(processingPost());
        when(storageService.getObject("videos/src.mp4")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        TranscodeTaskService.Claim claim = new TranscodeTaskService.Claim(1L, "lease", 1);
        when(taskService.claim(1L)).thenReturn(claim);
        when(taskService.complete(eq(claim), anyString(), anyString())).thenReturn(true);

        worker.handle(new TranscodeJob(1L));

        verify(ffmpegRunner).transcodeToMp4(any(Path.class), any(Path.class));
        verify(ffmpegRunner).extractCover(any(Path.class), any(Path.class));
        verify(storageService).putObject(anyString(), any(File.class), eq("video/mp4"));
        verify(storageService).putObject(anyString(), any(File.class), eq("image/jpeg"));
        verify(taskService).complete(eq(claim), anyString(), anyString());
        verify(uploadOwnershipService).deleteOwnedIfPresent(7L, "videos/src.mp4");
    }

    @Test
    void failureIsRecordedInRecoverableTask() {
        when(postMapper.selectById(1L)).thenReturn(processingPost());
        when(storageService.getObject("videos/src.mp4")).thenThrow(new IllegalStateException("读取文件失败: videos/src.mp4"));
        TranscodeTaskService.Claim claim = new TranscodeTaskService.Claim(1L, "lease", 1);
        when(taskService.claim(1L)).thenReturn(claim);

        worker.handle(new TranscodeJob(1L));

        verify(taskService).fail(eq(claim), anyString());
        verify(taskService, never()).complete(any(), any(), any());
    }

    @Test
    void missingPost_isSkipped() {
        when(postMapper.selectById(1L)).thenReturn(null);

        worker.handle(new TranscodeJob(1L));

        verify(taskService).cancel(1L);
        verify(taskService, never()).claim(any());
    }

    @Test
    void transientStorageFailureKeepsPostProcessingForRetry() {
        when(postMapper.selectById(1L)).thenReturn(processingPost());
        when(storageService.getObject("videos/src.mp4")).thenThrow(new IllegalStateException("temporary storage failure"));
        when(taskService.claim(1L)).thenReturn(new TranscodeTaskService.Claim(1L, "lease", 1));

        worker.handle(new TranscodeJob(1L));

        verify(taskService).fail(any(), eq("temporary storage failure"));
    }

    @Test
    void alreadyClaimedTaskDoesNotStartAnotherTranscode() {
        when(postMapper.selectById(1L)).thenReturn(processingPost());

        worker.handle(new TranscodeJob(1L));

        verify(ffmpegRunner, never()).transcodeToMp4(any(), any());
        verify(storageService, never()).getObject(any());
    }

    @Test
    void nonProcessingPost_isSkipped() {
        Post post = processingPost();
        post.setStatus(PostStatus.PUBLISHED);
        when(postMapper.selectById(1L)).thenReturn(post);

        worker.handle(new TranscodeJob(1L));

        verify(taskService).cancel(1L);
        verify(taskService, never()).claim(any());
    }
}
