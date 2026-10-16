package com.shiguang.content.transcode;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.content.PostTranscodeRequestedEvent;
import com.shiguang.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeTaskService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_SECONDS = 30;
    private static final int LEASE_SECONDS = 120;

    private final TranscodeTaskMapper taskMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    private final TranscodePublisher publisher;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final Map<Long, String> activeLeases = new ConcurrentHashMap<>();

    public record Claim(Long postId, String token, int attempt) {
    }

    @Transactional
    public void register(Long postId) {
        taskMapper.register(postId);
    }

    public void dispatch(Long postId) {
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        boolean prepared = Boolean.TRUE.equals(inNewTransaction(() ->
                taskMapper.prepareDispatch(postId, token, now, now.plusMinutes(30)) == 1));
        if (!prepared) {
            return;
        }
        try {
            publisher.send(postId);
        } catch (Exception exception) {
            log.warn("转码任务 {} 投递失败，等待补偿: {}", postId, exception.getMessage());
            inNewTransaction(() -> taskMapper.dispatchFailed(postId, token, errorOf(exception),
                    LocalDateTime.now(), LocalDateTime.now().plusSeconds(RETRY_SECONDS)));
        }
    }

    public Claim claim(Long postId) {
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        Claim claim = inNewTransaction(() -> {
            if (taskMapper.claim(postId, token, now, now.plusSeconds(LEASE_SECONDS), MAX_ATTEMPTS) != 1) {
                return null;
            }
            return new Claim(postId, token, taskMapper.selectById(postId).getAttempts());
        });
        if (claim != null) {
            activeLeases.put(postId, token);
        }
        return claim;
    }

    public boolean complete(Claim claim, String videoObject, String coverObject) {
        try {
            return Boolean.TRUE.equals(inNewTransaction(() -> {
                LocalDateTime now = LocalDateTime.now();
                if (taskMapper.finish(claim.postId(), claim.token(), "COMPLETED", "", now, now, false) != 1) {
                    return false;
                }
                Post post = postMapper.selectById(claim.postId());
                if (post == null || post.getStatus() != PostStatus.PROCESSING) {
                    return false;
                }
                postService.markPublished(claim.postId(), videoObject, coverObject);
                return true;
            }));
        } finally {
            activeLeases.remove(claim.postId(), claim.token());
        }
    }

    public void fail(Claim claim, String reason) {
        fail(claim, reason, false);
    }

    private void fail(Claim claim, String reason, boolean expiredOnly) {
        boolean removeLease = !expiredOnly;
        try {
            boolean finished = Boolean.TRUE.equals(inNewTransaction(() -> {
                LocalDateTime now = LocalDateTime.now();
                boolean exhausted = claim.attempt() >= MAX_ATTEMPTS;
                String error = truncate(reason);
                if (taskMapper.finish(claim.postId(), claim.token(), exhausted ? "FAILED" : "PENDING",
                        error, now.plusSeconds(RETRY_SECONDS), now, expiredOnly) != 1) {
                    return false;
                }
                if (exhausted) {
                    Post post = postMapper.selectById(claim.postId());
                    if (post != null && post.getStatus() == PostStatus.PROCESSING) {
                        postService.markFailed(claim.postId(), error);
                    }
                }
                return true;
            }));
            removeLease |= finished;
        } finally {
            if (removeLease) {
                activeLeases.remove(claim.postId(), claim.token());
            }
        }
    }

    public void cancel(Long postId) {
        inNewTransaction(() -> taskMapper.cancel(postId, LocalDateTime.now()));
        activeLeases.remove(postId);
    }

    @Scheduled(fixedDelayString = "${app.transcode.recovery-interval-ms:30000}")
    public void recover() {
        LocalDateTime now = LocalDateTime.now();
        for (TranscodeTask task : taskMapper.selectList(new LambdaQueryWrapper<TranscodeTask>()
                .eq(TranscodeTask::getState, "RUNNING").lt(TranscodeTask::getLeaseUntil, now)
                .last("LIMIT 20"))) {
            fail(new Claim(task.getPostId(), task.getLeaseToken(), task.getAttempts()), "处理进程中断或租约过期", true);
        }
        for (TranscodeTask task : taskMapper.selectList(new LambdaQueryWrapper<TranscodeTask>()
                .in(TranscodeTask::getState, "PENDING", "DISPATCHED")
                .le(TranscodeTask::getNextAttemptAt, now).orderByAsc(TranscodeTask::getNextAttemptAt)
                .last("LIMIT 20"))) {
            Post post = postMapper.selectById(task.getPostId());
            if (post == null || post.getStatus() != PostStatus.PROCESSING) {
                cancel(task.getPostId());
            } else {
                dispatch(task.getPostId());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.transcode.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        activeLeases.forEach((postId, token) -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                if (taskMapper.renew(postId, token, now.plusSeconds(LEASE_SECONDS), now) == 0) {
                    activeLeases.remove(postId, token);
                }
            } catch (Exception exception) {
                log.warn("转码任务 {} 续租失败: {}", postId, exception.getMessage());
            }
        });
    }

    @Transactional
    public void retry(Long postId, Long userId) {
        Post post = postService.requirePost(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BizException(403, "只能重试自己的作品");
        }
        if (post.getStatus() != PostStatus.FAILED) {
            throw new BizException("只有处理失败的作品可以重试");
        }
        if (post.getSourceObject() == null || post.getSourceObject().isBlank()
                || storageService.stat(post.getSourceObject()).size() <= 0) {
            throw new BizException("原始视频不存在，请重新上传");
        }
        int changed = postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, postId)
                .eq(Post::getStatus, PostStatus.FAILED).set(Post::getStatus, PostStatus.PROCESSING)
                .set(Post::getFailReason, ""));
        if (changed != 1) {
            throw new BizException("作品状态已改变，请刷新后重试");
        }
        taskMapper.register(postId);
        taskMapper.resetFailed(postId, LocalDateTime.now());
        eventPublisher.publishEvent(new PostTranscodeRequestedEvent(postId));
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }

    private static String errorOf(Exception exception) {
        return truncate(exception.getMessage());
    }

    private static String truncate(String reason) {
        return reason == null ? "处理失败" : reason.substring(0, Math.min(reason.length(), 500));
    }
}
