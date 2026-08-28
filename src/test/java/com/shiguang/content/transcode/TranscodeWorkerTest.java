package com.shiguang.content.transcode;

import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.storage.StorageService;
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

@ExtendWith(MockitoExtension.class)
class TranscodeWorkerTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostService postService;

    @Mock
    private StorageService storageService;

    @Mock
    private FfmpegRunner ffmpegRunner;

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

        worker.handle(new TranscodeJob(1L));

        verify(ffmpegRunner).transcodeToMp4(any(Path.class), any(Path.class));
        verify(ffmpegRunner).extractCover(any(Path.class), any(Path.class));
        verify(storageService).putObject(anyString(), any(File.class), eq("video/mp4"));
        verify(storageService).putObject(anyString(), any(File.class), eq("image/jpeg"));
        verify(postService).markPublished(eq(1L), anyString(), anyString());
    }

    @Test
    void failure_marksFailed() {
        when(postMapper.selectById(1L)).thenReturn(processingPost());
        when(storageService.getObject("videos/src.mp4")).thenThrow(new IllegalStateException("读取文件失败: videos/src.mp4"));

        worker.handle(new TranscodeJob(1L));

        verify(postService).markFailed(eq(1L), anyString());
        verify(postService, org.mockito.Mockito.never()).markPublished(any(), any(), any());
    }

    @Test
    void missingPost_isSkipped() {
        when(postMapper.selectById(1L)).thenReturn(null);

        worker.handle(new TranscodeJob(1L));

        verify(postService, org.mockito.Mockito.never()).markFailed(any(), any());
        verify(postService, org.mockito.Mockito.never()).markPublished(any(), any(), any());
    }

    @Test
    void nonProcessingPost_isSkipped() {
        Post post = processingPost();
        post.setStatus(PostStatus.PUBLISHED);
        when(postMapper.selectById(1L)).thenReturn(post);

        worker.handle(new TranscodeJob(1L));

        verify(postService, org.mockito.Mockito.never()).markFailed(any(), any());
        verify(postService, org.mockito.Mockito.never()).markPublished(any(), any(), any());
    }
}