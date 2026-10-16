package com.shiguang.content.transcode;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.content.PostTranscodeRequestedEvent;
import com.shiguang.content.PostType;
import com.shiguang.content.PostVisibility;
import com.shiguang.storage.StorageService;
import com.shiguang.storage.StorageUploadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.transcode.recovery-interval-ms=99999999",
        "app.transcode.heartbeat-interval-ms=99999999",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class TranscodeTaskRecoveryTest {

    @Autowired private TranscodeTaskService tasks;
    @Autowired private TranscodeTaskMapper taskMapper;
    @Autowired private PostMapper postMapper;
    @Autowired private StorageUploadMapper uploadMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @MockitoBean private TranscodePublisher publisher;
    @MockitoBean private StorageService storage;
    @MockitoSpyBean private PostService postService;

    private final List<Long> postIds = new ArrayList<>();

    @BeforeEach
    void storageMetadata() {
        when(storage.stat(anyString())).thenAnswer(invocation -> new StorageService.ObjectStat(128,
                invocation.<String>getArgument(0).endsWith(".mp4") ? "video/mp4" : "image/jpeg"));
    }

    @AfterEach
    void cleanup() {
        for (Long postId : postIds) {
            tasks.cancel(postId);
            taskMapper.deleteById(postId);
            postMapper.deleteById(postId);
            uploadMapper.deleteById(videoObject(postId));
            uploadMapper.deleteById(coverObject(postId));
        }
    }

    @Test
    void taskRegistrationRollsBackWithPost() {
        Long postId = new TransactionTemplate(transactionManager).execute(status -> {
            Post post = newPost();
            tasks.register(post.getId());
            status.setRollbackOnly();
            return post.getId();
        });

        assertThat(postMapper.selectById(postId)).isNull();
        assertThat(taskMapper.selectById(postId)).isNull();
    }

    @Test
    void committedPostRegistersTaskBeforeSending() {
        Long postId = new TransactionTemplate(transactionManager).execute(status -> {
            Post post = newPost();
            eventPublisher.publishEvent(new PostTranscodeRequestedEvent(post.getId()));
            return post.getId();
        });

        assertThat(taskMapper.selectById(postId).getState()).isEqualTo("DISPATCHED");
        verify(publisher).send(postId);
    }

    @Test
    void brokerFailureKeepsTaskForLaterDelivery() {
        Post post = newPost();
        tasks.register(post.getId());
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).send(post.getId());

        tasks.dispatch(post.getId());
        tasks.dispatch(post.getId());

        TranscodeTask task = taskMapper.selectById(post.getId());
        assertThat(task.getState()).isEqualTo("PENDING");
        assertThat(task.getAttempts()).isZero();
        assertThat(task.getLastError()).contains("broker unavailable");
        verify(publisher, times(1)).send(post.getId());
    }

    @Test
    void onlyOneWorkerCanClaimTask() {
        Post post = newPost();
        tasks.register(post.getId());

        assertThat(tasks.claim(post.getId())).isNotNull();
        assertThat(tasks.claim(post.getId())).isNull();
        assertThat(taskMapper.selectById(post.getId()).getAttempts()).isEqualTo(1);
    }

    @Test
    void expiredWorkerCannotPublishAfterAnotherWorkerClaims() {
        Post post = newPost();
        tasks.register(post.getId());
        TranscodeTaskService.Claim oldClaim = tasks.claim(post.getId());
        taskMapper.update(null, new LambdaUpdateWrapper<TranscodeTask>()
                .eq(TranscodeTask::getPostId, post.getId())
                .set(TranscodeTask::getLeaseUntil, LocalDateTime.now().minusSeconds(1)));

        tasks.recover();
        makeDue(post.getId());
        TranscodeTaskService.Claim newClaim = tasks.claim(post.getId());

        assertThat(newClaim).isNotNull();
        assertThat(newClaim.token()).isNotEqualTo(oldClaim.token());
        assertThat(tasks.complete(oldClaim, "videos/obsolete.mp4", "covers/obsolete.jpg")).isFalse();
        assertThat(taskMapper.selectById(post.getId()).getLeaseToken()).isEqualTo(newClaim.token());
        assertThat(postMapper.selectById(post.getId()).getStatus()).isEqualTo(PostStatus.PROCESSING);
    }

    @Test
    void failedPublicationRollsBackCompletionAndCanBeRetried() {
        Post post = newPost();
        tasks.register(post.getId());
        TranscodeTaskService.Claim claim = tasks.claim(post.getId());
        doThrow(new IllegalStateException("database write failed"))
                .when(postService).markPublished(eq(post.getId()), anyString(), anyString());

        assertThatThrownBy(() -> tasks.complete(claim, videoObject(post.getId()), coverObject(post.getId())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(taskMapper.selectById(post.getId()).getState()).isEqualTo("RUNNING");

        doCallRealMethod().when(postService).markPublished(eq(post.getId()), anyString(), anyString());
        assertThat(tasks.complete(claim, videoObject(post.getId()), coverObject(post.getId()))).isTrue();
        assertThat(taskMapper.selectById(post.getId()).getState()).isEqualTo("COMPLETED");
        assertThat(postMapper.selectById(post.getId()).getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(tasks.complete(claim, "videos/duplicate.mp4", "covers/duplicate.jpg")).isFalse();
    }

    @Test
    void failureStopsAfterThreeAttemptsAndOwnerCanRetry() {
        Post post = newPost();
        tasks.register(post.getId());
        for (int attempt = 1; attempt <= 3; attempt++) {
            makeDue(post.getId());
            TranscodeTaskService.Claim claim = tasks.claim(post.getId());
            assertThat(claim.attempt()).isEqualTo(attempt);
            tasks.fail(claim, "temporary failure");
        }

        assertThat(taskMapper.selectById(post.getId()).getState()).isEqualTo("FAILED");
        assertThat(postMapper.selectById(post.getId()).getStatus()).isEqualTo(PostStatus.FAILED);
        assertThat(tasks.claim(post.getId())).isNull();
        assertThatThrownBy(() -> tasks.retry(post.getId(), 99L)).isInstanceOf(BizException.class);

        tasks.retry(post.getId(), post.getUserId());

        assertThat(postMapper.selectById(post.getId()).getStatus()).isEqualTo(PostStatus.PROCESSING);
        assertThat(taskMapper.selectById(post.getId()).getAttempts()).isZero();
    }

    @Test
    void heartbeatRenewsOnlyCurrentClaim() {
        Post post = newPost();
        tasks.register(post.getId());
        TranscodeTaskService.Claim claim = tasks.claim(post.getId());
        taskMapper.update(null, new LambdaUpdateWrapper<TranscodeTask>()
                .eq(TranscodeTask::getPostId, post.getId())
                .set(TranscodeTask::getLeaseUntil, LocalDateTime.now().minusSeconds(1)));

        tasks.heartbeat();

        TranscodeTask task = taskMapper.selectById(post.getId());
        assertThat(task.getLeaseUntil()).isAfter(LocalDateTime.now());
        assertThat(task.getLeaseToken()).isEqualTo(claim.token());
    }

    private Post newPost() {
        Post post = new Post();
        post.setUserId(7L);
        post.setType(PostType.VIDEO);
        post.setStatus(PostStatus.PROCESSING);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setTitle("transcode recovery regression");
        post.setDescription("");
        post.setSourceObject("videos/recovery-" + UUID.randomUUID() + ".mp4");
        post.setLikeCount(0);
        post.setCommentCount(0);
        postMapper.insert(post);
        postIds.add(post.getId());
        return post;
    }

    private void makeDue(Long postId) {
        taskMapper.update(null, new LambdaUpdateWrapper<TranscodeTask>()
                .eq(TranscodeTask::getPostId, postId)
                .set(TranscodeTask::getNextAttemptAt, LocalDateTime.now().minusSeconds(1)));
    }

    private String videoObject(Long postId) {
        return "videos/" + postId + "/00000000-0000-0000-0000-000000000001.mp4";
    }

    private String coverObject(Long postId) {
        return "covers/" + postId + "/00000000-0000-0000-0000-000000000002.jpg";
    }
}
