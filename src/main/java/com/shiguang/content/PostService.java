package com.shiguang.content;

import com.shiguang.common.BizException;
import com.shiguang.interaction.CommentService;
import com.shiguang.interaction.LikeService;
import com.shiguang.storage.StorageService;
import com.shiguang.user.User;
import com.shiguang.user.FollowService;
import com.shiguang.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final UserService userService;
    private final StorageService storageService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final FollowService followService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PostVO create(CreatePostRequest request, Long userId) {
        PostType type = parseType(request.getType());
        Post post = new Post();
        post.setUserId(userId);
        post.setType(type);
        post.setTitle(trimToNull(request.getTitle()));
        post.setDescription(trimToNull(request.getDescription()));
        post.setCoverObject(trimToNull(request.getCoverObject()));
        post.setLikeCount(0);
        post.setCommentCount(0);

        if (type == PostType.VIDEO) {
            if (isBlank(request.getVideoObject())) {
                throw new BizException("视频作品必须提供 videoObject");
            }
            post.setSourceObject(request.getVideoObject().trim());
            post.setStatus(PostStatus.PROCESSING);
        } else {
            if (request.getImages() == null || request.getImages().isEmpty()) {
                throw new BizException("图文作品必须提供至少一张图片");
            }
            post.setImagesObject(request.getImages().stream().map(String::trim).toList());
            post.setStatus(PostStatus.PUBLISHED);
        }
        postMapper.insert(post);

        if (type == PostType.VIDEO) {
            eventPublisher.publishEvent(new PostTranscodeRequestedEvent(post.getId()));
            log.info("video post {} transcode requested", post.getId());
        } else {
            eventPublisher.publishEvent(new PostPublishedEvent(post.getId()));
        }
        return toVO(post);
    }

    public PostVO getDetail(Long id) {
        return getDetail(id, null);
    }

    public PostVO getDetail(Long id, Long viewerId) {
        Post post = requirePost(id);
        PostVO vo = toVO(post);
        vo.setLiked(viewerId != null && likeService.isPostLiked(id, viewerId));
        vo.setLikeCount(Math.max(0, post.getLikeCount() + likeService.postPendingDelta(id)));
        if (vo.getAuthor() != null && viewerId != null) {
            vo.getAuthor().setFollowing(followService.followingMap(viewerId, java.util.List.of(post.getUserId()))
                    .getOrDefault(post.getUserId(), false));
        }
        return vo;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Post post = requirePost(id);
        if (!post.getUserId().equals(userId)) {
            throw new BizException(403, "只能删除自己的作品");
        }
        deleteObjects(post);
        postMapper.deleteById(id);
        likeService.cleanupPost(id);
        commentService.cleanupPost(id);
        eventPublisher.publishEvent(new PostDeletedEvent(id));
    }

    public void markPublished(Long postId, String videoObject, String coverObject) {
        Post post = requirePost(postId);
        if (post.getStatus() != PostStatus.PROCESSING) {
            return;
        }
        post.setVideoObject(videoObject);
        post.setCoverObject(coverObject);
        post.setStatus(PostStatus.PUBLISHED);
        post.setFailReason("");
        postMapper.updateById(post);
        eventPublisher.publishEvent(new PostPublishedEvent(postId));
        log.info("post {} published", postId);
    }

    public void markFailed(Long postId, String reason) {
        Post post = requirePost(postId);
        if (post.getStatus() != PostStatus.PROCESSING) {
            return;
        }
        post.setStatus(PostStatus.FAILED);
        post.setFailReason(reason == null ? "" : reason.substring(0, Math.min(reason.length(), 500)));
        postMapper.updateById(post);
        log.warn("post {} marked FAILED: {}", postId, post.getFailReason());
    }

    public Post requirePost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BizException(404, "作品不存在或已删除");
        }
        return post;
    }

    public PostVO toVO(Post post) {
        User author = userService.getById(post.getUserId());
        PostVO.PostVOBuilder builder = PostVO.builder()
                .id(post.getId())
                .type(post.getType())
                .title(post.getTitle())
                .description(post.getDescription())
                .status(post.getStatus())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .failReason(post.getFailReason())
                .createdAt(post.getCreatedAt())
                .author(PostVO.Author.builder()
                        .id(author.getId())
                        .nickname(author.getNickname())
                        .avatarUrl(author.getAvatarUrl() == null || author.getAvatarUrl().isBlank()
                                || author.getAvatarUrl().startsWith("http")
                                ? author.getAvatarUrl()
                                : storageService.presignedGetUrl(author.getAvatarUrl()))
                        .build());
        if (!isBlank(post.getVideoObject())) {
            builder.videoUrl(storageService.presignedGetUrl(post.getVideoObject()));
        }
        if (!isBlank(post.getCoverObject())) {
            builder.coverUrl(storageService.presignedGetUrl(post.getCoverObject()));
        }
        if (post.getImagesObject() != null) {
            builder.images(post.getImagesObject().stream().map(storageService::presignedGetUrl).toList());
        }
        return builder.build();
    }

    private PostType parseType(String type) {
        try {
            return PostType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BizException("不支持的发布类型: " + type);
        }
    }

    private void deleteObjects(Post post) {
        safeDelete(post.getSourceObject());
        safeDelete(post.getVideoObject());
        safeDelete(post.getCoverObject());
        if (post.getImagesObject() != null) {
            post.getImagesObject().forEach(this::safeDelete);
        }
    }

    private void safeDelete(String objectName) {
        if (isBlank(objectName)) {
            return;
        }
        try {
            storageService.deleteObject(objectName);
        } catch (Exception e) {
            log.warn("删除存储对象失败: {}", objectName, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? "" : s.trim();
    }
}