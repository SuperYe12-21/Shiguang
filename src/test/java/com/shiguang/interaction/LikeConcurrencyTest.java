package com.shiguang.interaction;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shiguang.common.counter.CounterTestApplication;
import com.shiguang.content.PostMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(classes = CounterTestApplication.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = {"spring.profiles.active=dev", "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379", "spring.data.redis.database=14",
                "spring.datasource.url=jdbc:mysql://localhost:3306/shiguang?useSSL=false&allowPublicKeyRetrieval=true",
                "spring.datasource.username=shiguang", "logging.level.com.shiguang=WARN"})
class LikeConcurrencyTest {

    @Autowired
    private LikeService likes;

    @Autowired
    private PostMapper posts;

    @SpyBean
    private PostLikeMapper relations;

    @SpyBean
    private CommentLikeMapper commentRelations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private long postId;
    private long commentId;

    @BeforeEach
    void createTargets() {
        likes.flushPendingCounts();
        postId = insert("INSERT INTO post(user_id, type, status) VALUES (0, 'IMAGE', 'PUBLISHED')");
        commentId = insert("INSERT INTO comment(post_id, user_id, content) VALUES (" + postId + ", 0, 'like test')");
    }

    @AfterEach
    void cleanup() {
        reset(relations, commentRelations);
        likes.flushPendingCounts();
        likes.cleanupComment(commentId);
        likes.cleanupPost(postId);
        jdbc.update("DELETE FROM comment WHERE id = ?", commentId);
        jdbc.update("DELETE FROM post WHERE id = ?", postId);
    }

    @Test
    void concurrentLikesAndUnlikes_flushCountsStaysConsistent() throws Exception {
        List<Long> users = LongStream.rangeClosed(1, 16).boxed().toList();
        runConcurrently(users, userId -> likes.likePost(postId, userId));
        assertThat(countLikeRows()).isEqualTo(16);
        likes.flushPendingCounts();
        assertThat(posts.selectById(postId).getLikeCount()).isEqualTo(16);

        runConcurrently(users.subList(0, 8), userId -> likes.unlikePost(postId, userId));
        likes.flushPendingCounts();
        assertThat(countLikeRows()).isEqualTo(8);
        assertThat(posts.selectById(postId).getLikeCount()).isEqualTo(8);

        runConcurrently(users, userId -> likes.unlikePost(postId, userId));
        likes.flushPendingCounts();
        assertThat(countLikeRows()).isZero();
        assertThat(posts.selectById(postId).getLikeCount()).isZero();
    }

    @Test
    void sameUserDuplicateLike_isIdempotent() throws Exception {
        runConcurrently(java.util.Collections.nCopies(16, 1L), userId -> likes.likePost(postId, userId));
        assertThat(countLikeRows()).isEqualTo(1);
        likes.flushPendingCounts();
        assertThat(posts.selectById(postId).getLikeCount()).isEqualTo(1);
        likes.unlikePost(postId, 1L);
        likes.flushPendingCounts();
        assertThat(posts.selectById(postId).getLikeCount()).isZero();
        assertThat(countLikeRows()).isZero();
    }

    @Test
    void failedRelationInsertLeavesMembershipAndPendingUntouched() {
        doThrow(new DataAccessResourceFailureException("injected relation outage"))
                .when(relations).insert(any(PostLike.class));

        assertThatThrownBy(() -> likes.likePost(postId, 1L)).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(countLikeRows()).isZero();
        assertThat(likes.isPostLiked(postId, 1L)).isFalse();
        assertThat(likes.postPendingDelta(postId)).isZero();
    }

    @Test
    void failedRelationDeleteLeavesMembershipAndPendingUntouched() {
        likes.likePost(postId, 1L);
        doThrow(new DataAccessResourceFailureException("injected relation outage"))
                .when(relations).delete(any(Wrapper.class));

        assertThatThrownBy(() -> likes.unlikePost(postId, 1L)).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(countLikeRows()).isEqualTo(1);
        assertThat(likes.isPostLiked(postId, 1L)).isTrue();
        assertThat(likes.postPendingDelta(postId)).isEqualTo(1);
    }

    @Test
    void staleEmptyCacheDoesNotCountExistingRelationTwice() {
        relations.insert(new PostLike(postId, 1L));
        jdbc.update("UPDATE post SET like_count = 1 WHERE id = ?", postId);
        redis.opsForValue().set("like:init:post:" + postId, "1");

        likes.likePost(postId, 1L);
        likes.flushPendingCounts();

        assertThat(countLikeRows()).isEqualTo(1);
        assertThat(posts.selectById(postId).getLikeCount()).isEqualTo(1);
        assertThat(likes.isPostLiked(postId, 1L)).isTrue();
    }

    @Test
    void staleEmptyCacheStillDeletesExistingRelation() {
        relations.insert(new PostLike(postId, 1L));
        jdbc.update("UPDATE post SET like_count = 1 WHERE id = ?", postId);
        redis.opsForValue().set("like:init:post:" + postId, "1");

        likes.unlikePost(postId, 1L);
        likes.flushPendingCounts();

        assertThat(countLikeRows()).isZero();
        assertThat(posts.selectById(postId).getLikeCount()).isZero();
    }

    @Test
    void failedCommentRelationInsertDoesNotIncrementPending() {
        doThrow(new DataAccessResourceFailureException("injected relation outage"))
                .when(commentRelations).insert(any(CommentLike.class));

        assertThatThrownBy(() -> likes.likeComment(commentId, 1L))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(likes.commentPendingDelta(commentId)).isZero();
        assertThat(redis.opsForSet().isMember("like:comment:" + commentId, "1")).isFalse();
    }

    @Test
    void warmCacheDoesNotEagerlyLoadEveryRelation() {
        likes.likePost(postId, 1L);
        likes.likeComment(commentId, 1L);
        clearInvocations(relations, commentRelations);

        assertThat(likes.isPostLiked(postId, 1L)).isTrue();
        likes.likeComment(commentId, 1L);

        verify(relations, never()).selectList(any(Wrapper.class));
        verify(commentRelations, never()).selectList(any(Wrapper.class));
    }

    private void runConcurrently(List<Long> users, Consumer<Long> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(users.size());
        CountDownLatch ready = new CountDownLatch(users.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Long userId : users) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.accept(userId);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long countLikeRows() {
        return relations.selectCount(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
    }

    private long insert(String sql) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), key);
        return key.getKey().longValue();
    }
}
