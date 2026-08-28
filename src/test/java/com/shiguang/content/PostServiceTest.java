package com.shiguang.content;

import com.shiguang.common.BizException;
import com.shiguang.content.transcode.TranscodePublisher;
import com.shiguang.storage.StorageService;
import com.shiguang.user.User;
import com.shiguang.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private UserService userService;

    @Mock
    private StorageService storageService;

    @Mock
    private TranscodePublisher transcodePublisher;

    @InjectMocks
    private PostService postService;

    private final User author = new User();

    @BeforeEach
    void setUp() {
        author.setId(7L);
        author.setNickname("作者");
        author.setAvatarUrl("https://example.com/avatar.png");
        lenient().when(userService.getById(7L)).thenReturn(author);
    }

    private static CreatePostRequest videoRequest(String videoObject) {
        CreatePostRequest request = new CreatePostRequest();
        request.setType("VIDEO");
        request.setTitle("我的视频");
        request.setVideoObject(videoObject);
        return request;
    }

    private static CreatePostRequest imageRequest() {
        CreatePostRequest request = new CreatePostRequest();
        request.setType("IMAGE");
        request.setTitle("我的图文");
        request.setImages(List.of("images/a.jpg", "images/b.jpg"));
        return request;
    }

    @Test
    void imagePost_publishedImmediatelyAndNotEnqueued() {
        doAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(100L);
            return 1;
        }).when(postMapper).insert(any(Post.class));

        PostVO vo = postService.create(imageRequest(), 7L);

        assertThat(vo.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(vo.getImages()).hasSize(2);
        verify(transcodePublisher, never()).send(any());
    }

    @Test
    void videoPost_goesProcessingAndEnqueued() {
        doAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(101L);
            return 1;
        }).when(postMapper).insert(any(Post.class));

        PostVO vo = postService.create(videoRequest("videos/src.mp4"), 7L);

        assertThat(vo.getStatus()).isEqualTo(PostStatus.PROCESSING);
        verify(transcodePublisher).send(101L);
    }

    @Test
    void videoPostWithoutObject_throws() {
        assertThatThrownBy(() -> postService.create(videoRequest(null), 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("videoObject");
    }

    @Test
    void imagePostWithoutImages_throws() {
        CreatePostRequest request = new CreatePostRequest();
        request.setType("IMAGE");
        assertThatThrownBy(() -> postService.create(request, 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少一张图片");
    }

    @Test
    void deleteOwnPost_removesStorageObjects() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(7L);
        post.setSourceObject("videos/src.mp4");
        post.setVideoObject("videos/out.mp4");
        post.setCoverObject("covers/c.jpg");
        post.setImagesObject(List.of("images/a.jpg"));
        when(postMapper.selectById(1L)).thenReturn(post);

        postService.delete(1L, 7L);

        verify(postMapper).deleteById((Serializable) 1L);
        verify(storageService).deleteObject("videos/src.mp4");
        verify(storageService).deleteObject("videos/out.mp4");
        verify(storageService).deleteObject("covers/c.jpg");
        verify(storageService).deleteObject("images/a.jpg");
    }

    @Test
    void deleteOthersPost_forbidden() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(99L);
        when(postMapper.selectById(1L)).thenReturn(post);

        assertThatThrownBy(() -> postService.delete(1L, 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能删除自己的作品");
        verify(postMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void getDetail_resolvesSignedUrls() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(7L);
        post.setType(PostType.VIDEO);
        post.setTitle("t");
        post.setStatus(PostStatus.PUBLISHED);
        post.setVideoObject("videos/out.mp4");
        post.setCoverObject("covers/c.jpg");
        post.setLikeCount(3);
        post.setCommentCount(5);
        when(postMapper.selectById(1L)).thenReturn(post);
        when(storageService.presignedGetUrl("videos/out.mp4")).thenReturn("http://minio/videos/out.mp4?sig=1");
        when(storageService.presignedGetUrl("covers/c.jpg")).thenReturn("http://minio/covers/c.jpg?sig=2");

        PostVO vo = postService.getDetail(1L);

        assertThat(vo.getVideoUrl()).isEqualTo("http://minio/videos/out.mp4?sig=1");
        assertThat(vo.getCoverUrl()).isEqualTo("http://minio/covers/c.jpg?sig=2");
        assertThat(vo.getAuthor().getNickname()).isEqualTo("作者");
        assertThat(vo.getLikeCount()).isEqualTo(3);
    }

    @Test
    void getDetail_missing_throws404() {
        when(postMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> postService.getDetail(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void markPublished_transitionsToPublished() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(7L);
        post.setType(PostType.VIDEO);
        post.setStatus(PostStatus.PROCESSING);
        when(postMapper.selectById(1L)).thenReturn(post);

        postService.markPublished(1L, "videos/out.mp4", "covers/c.jpg");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).updateById((Post) captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(captor.getValue().getVideoObject()).isEqualTo("videos/out.mp4");
        assertThat(captor.getValue().getCoverObject()).isEqualTo("covers/c.jpg");
    }

    @Test
    void markFailed_setsFailReason() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(7L);
        post.setType(PostType.VIDEO);
        post.setStatus(PostStatus.PROCESSING);
        when(postMapper.selectById(1L)).thenReturn(post);

        postService.markFailed(1L, "ffmpeg 炸了");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).updateById((Post) captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PostStatus.FAILED);
        assertThat(captor.getValue().getFailReason()).contains("ffmpeg");
    }

    @Test
    void markPublished_ignoredWhenAlreadyPublished() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(7L);
        post.setType(PostType.VIDEO);
        post.setStatus(PostStatus.PUBLISHED);
        when(postMapper.selectById(1L)).thenReturn(post);

        postService.markPublished(1L, "videos/out.mp4", "covers/c.jpg");

        verify(postMapper, never()).updateById(any(Post.class));
    }
}
