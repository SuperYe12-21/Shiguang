package com.shiguang.common.counter;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.shiguang.content.PostMapper;
import com.shiguang.feed.ViewCountService;
import com.shiguang.interaction.CommentMapper;
import com.shiguang.interaction.LikeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

@SpringJUnitConfig(classes = CounterTestApplication.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = {"spring.profiles.active=dev", "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379", "spring.data.redis.database=14",
                "spring.datasource.url=jdbc:mysql://localhost:3306/shiguang?useSSL=false&allowPublicKeyRetrieval=true",
                "spring.datasource.username=shiguang", "logging.level.com.shiguang=WARN"})
class CounterRecoveryTest {

    enum Counter {
        VIEW("view:pending", "post", "view_count", "p:"),
        POST_LIKE("like:pending", "post", "like_count", "p:"),
        COMMENT_LIKE("like:pending", "comment", "like_count", "c:");

        final String pendingKey;
        final String table;
        final String column;
        final String prefix;

        Counter(String pendingKey, String table, String column, String prefix) {
            this.pendingKey = pendingKey;
            this.table = table;
            this.column = column;
            this.prefix = prefix;
        }
    }

    @Autowired
    private LikeService likes;

    @Autowired
    private ViewCountService views;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private StringRedisTemplate redis;

    @SpyBean
    private PostMapper posts;

    @SpyBean
    private CommentMapper comments;

    private final List<Long> postIds = new ArrayList<>();
    private final List<Long> commentIds = new ArrayList<>();

    @BeforeEach
    void recoverPreviousBatch() {
        likes.flushPendingCounts();
        views.flushPendingCounts();
    }

    @AfterEach
    void cleanup() {
        reset(redis, posts, comments);
        likes.flushPendingCounts();
        views.flushPendingCounts();
        commentIds.forEach(identifier -> jdbc.update("DELETE FROM comment WHERE id = ?", identifier));
        postIds.forEach(identifier -> jdbc.update("DELETE FROM post WHERE id = ?", identifier));
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void crashBeforeApplyRetainsBatchForNextFlush(Counter counter) {
        long identifier = target(counter);
        increment(counter, identifier, 3);
        if (counter == Counter.COMMENT_LIKE) {
            doAnswer(invocation -> { throw new SimulatedCrash(); })
                    .when(comments).update(isNull(), any(Wrapper.class));
        } else {
            doAnswer(invocation -> { throw new SimulatedCrash(); })
                    .when(posts).update(isNull(), any(Wrapper.class));
        }

        assertThatThrownBy(() -> flush(counter)).isInstanceOf(SimulatedCrash.class);
        assertThat(stored(counter, identifier)).isZero();
        assertThat(pending(counter, identifier)).isEqualTo(3);

        increment(counter, identifier, 2);
        assertThat(pending(counter, identifier)).isEqualTo(5);

        reset(posts, comments);
        flush(counter);
        flush(counter);
        assertThat(stored(counter, identifier)).isEqualTo(5);
        assertThat(pending(counter, identifier)).isZero();
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void databaseFailureRollsBackEveryDeltaAndReceipt(Counter counter) {
        long firstId = target(counter);
        long secondId = target(counter);
        increment(counter, firstId, 2);
        increment(counter, secondId, 4);
        AtomicInteger updates = new AtomicInteger();
        org.mockito.stubbing.Answer<Object> failSecond = invocation -> {
            if (updates.incrementAndGet() == 2) {
                throw new DataAccessResourceFailureException("injected database outage");
            }
            Object mapper = counter == Counter.COMMENT_LIKE ? comments : posts;
            return mockingDetails(mapper).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        };
        if (counter == Counter.COMMENT_LIKE) {
            doAnswer(failSecond).when(comments).update(isNull(), any(Wrapper.class));
        } else {
            doAnswer(failSecond).when(posts).update(isNull(), any(Wrapper.class));
        }

        flush(counter);

        assertThat(stored(counter, firstId)).isZero();
        assertThat(stored(counter, secondId)).isZero();
        assertThat(pending(counter, firstId)).isEqualTo(2);
        assertThat(pending(counter, secondId)).isEqualTo(4);
        reset(posts, comments);
        flush(counter);
        assertThat(stored(counter, firstId)).isEqualTo(2);
        assertThat(stored(counter, secondId)).isEqualTo(4);
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void committedBatchIsExcludedFromReadsAndNotReappliedBeforeAck(Counter counter) {
        long identifier = target(counter);
        increment(counter, identifier, 3);
        interruptAck(counter, false);

        assertThatThrownBy(() -> flush(counter)).isInstanceOf(SimulatedCrash.class);

        assertThat(stored(counter, identifier)).isEqualTo(3);
        assertThat(redis.hasKey(counter.pendingKey + ":inflight")).isTrue();
        assertThat(pending(counter, identifier)).isZero();
        increment(counter, identifier, 2);
        assertThat(pending(counter, identifier)).isEqualTo(2);
        reset(redis);
        flush(counter);
        flush(counter);
        assertThat(stored(counter, identifier)).isEqualTo(5);
        assertThat(pending(counter, identifier)).isZero();
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void lostAckResponseDoesNotRepeatCommittedUpdates(Counter counter) {
        long identifier = target(counter);
        increment(counter, identifier, 3);
        interruptAck(counter, true);

        assertThatThrownBy(() -> flush(counter)).isInstanceOf(SimulatedCrash.class);

        assertThat(stored(counter, identifier)).isEqualTo(3);
        assertThat(redis.hasKey(counter.pendingKey + ":inflight")).isFalse();
        reset(redis);
        increment(counter, identifier, 2);
        flush(counter);
        assertThat(stored(counter, identifier)).isEqualTo(5);
        assertThat(pending(counter, identifier)).isZero();
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void lostStagingResponseIsRecoveredWithoutJvmBatchState(Counter counter) {
        long identifier = target(counter);
        increment(counter, identifier, 3);
        AtomicBoolean interrupted = new AtomicBoolean();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            List<String> keys = invocation.getArgument(1);
            if (keys.equals(List.of(counter.pendingKey, counter.pendingKey + ":inflight",
                    counter.pendingKey + ":batch", counter.pendingKey + ":cursor"))
                    && interrupted.compareAndSet(false, true)) {
                throw new SimulatedCrash();
            }
            return result;
        }).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> flush(counter)).isInstanceOf(SimulatedCrash.class);

        assertThat(stored(counter, identifier)).isZero();
        assertThat(pending(counter, identifier)).isEqualTo(3);
        reset(redis);
        flush(counter);
        assertThat(stored(counter, identifier)).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void eachFlushHasBoundedWorkAndEventuallyDrainsTheBuffer(Counter counter) {
        Map<String, String> entries = new HashMap<>();
        for (int index = 0; index < 600; index++) {
            entries.put(counter.prefix + (8_000_000_000_000_000L + index), "1");
        }
        redis.opsForHash().putAll(counter.pendingKey, entries);

        flush(counter);

        assertThat(redis.opsForHash().size(counter.pendingKey)).isBetween(344L, 599L);
        for (int attempt = 0; attempt < 16 && redis.hasKey(counter.pendingKey); attempt++) {
            flush(counter);
        }
        assertThat(redis.hasKey(counter.pendingKey)).isFalse();
        assertThat(redis.hasKey(counter.pendingKey + ":inflight")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Counter.class, names = {"POST_LIKE", "COMMENT_LIKE"})
    void negativeLikeDeltaClampsAtZeroAndAcknowledgesBatch(Counter counter) {
        long identifier = target(counter);
        increment(counter, identifier, -2);

        flush(counter);

        assertThat(stored(counter, identifier)).isZero();
        assertThat(pending(counter, identifier)).isZero();
        assertThat(redis.hasKey(counter.pendingKey + ":inflight")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void crashAfterApplyBeforeCommitRollsBackAndRetries(Counter counter) {
        long identifier = target(counter);
        increment(counter, identifier, 3);
        org.mockito.stubbing.Answer<Object> crashAfterUpdate = invocation -> {
            Object mapper = counter == Counter.COMMENT_LIKE ? comments : posts;
            mockingDetails(mapper).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            throw new SimulatedCrash();
        };
        if (counter == Counter.COMMENT_LIKE) {
            doAnswer(crashAfterUpdate).when(comments).update(isNull(), any(Wrapper.class));
        } else {
            doAnswer(crashAfterUpdate).when(posts).update(isNull(), any(Wrapper.class));
        }

        assertThatThrownBy(() -> flush(counter)).isInstanceOf(SimulatedCrash.class);

        assertThat(stored(counter, identifier)).isZero();
        assertThat(pending(counter, identifier)).isEqualTo(3);
        reset(posts, comments);
        flush(counter);
        assertThat(stored(counter, identifier)).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(Counter.class)
    void concurrentFlushersApplyTheSameBatchOnlyOnce(Counter counter) throws Exception {
        long identifier = target(counter);
        increment(counter, identifier, 3);
        CountDownLatch staged = new CountDownLatch(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            List<String> keys = invocation.getArgument(1);
            if (keys.equals(List.of(counter.pendingKey, counter.pendingKey + ":inflight",
                    counter.pendingKey + ":batch", counter.pendingKey + ":cursor"))) {
                staged.countDown();
                assertThat(staged.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        var workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> flush(counter));
            Future<?> second = workers.submit(() -> flush(counter));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(stored(counter, identifier)).isEqualTo(3);
        assertThat(pending(counter, identifier)).isZero();
    }

    @Test
    void viewDeduplicationSurvivesFlushAndDifferentUsersIncrementIndependently() {
        long identifier = target(Counter.VIEW);

        views.recordView(identifier, 1L);
        views.recordView(identifier, 1L);
        views.recordView(identifier, 2L);
        views.recordView(null, 1L);
        views.recordView(identifier, null);

        assertThat(pending(Counter.VIEW, identifier)).isEqualTo(2);
        views.flushPendingCounts();
        views.recordView(identifier, 1L);
        assertThat(stored(Counter.VIEW, identifier)).isEqualTo(2);
        assertThat(pending(Counter.VIEW, identifier)).isZero();
        redis.delete(List.of("view:dup:" + identifier + ":1", "view:dup:" + identifier + ":2"));
    }

    private void interruptAck(Counter counter, boolean afterAck) {
        AtomicBoolean interrupted = new AtomicBoolean();
        doAnswer(invocation -> {
            List<String> keys = invocation.getArgument(1);
            if (keys.equals(List.of(counter.pendingKey + ":inflight", counter.pendingKey + ":batch"))
                    && interrupted.compareAndSet(false, true)) {
                if (afterAck) {
                    invocation.callRealMethod();
                }
                throw new SimulatedCrash();
            }
            return invocation.callRealMethod();
        }).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    private long target(Counter counter) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            String sql = counter == Counter.COMMENT_LIKE
                    ? "INSERT INTO comment(post_id, user_id, content) VALUES (0, 0, 'counter recovery')"
                    : "INSERT INTO post(user_id, type, status) VALUES (0, 'IMAGE', 'PUBLISHED')";
            return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        }, key);
        long identifier = key.getKey().longValue();
        (counter == Counter.COMMENT_LIKE ? commentIds : postIds).add(identifier);
        return identifier;
    }

    private void increment(Counter counter, long identifier, long delta) {
        redis.opsForHash().increment(counter.pendingKey, counter.prefix + identifier, delta);
    }

    private long stored(Counter counter, long identifier) {
        return jdbc.queryForObject("SELECT " + counter.column + " FROM " + counter.table + " WHERE id = ?",
                Long.class, identifier);
    }

    private long pending(Counter counter, long identifier) {
        long delta = switch (counter) {
            case VIEW -> views.pendingDeltas(List.of(identifier)).getOrDefault(identifier, 0L);
            case POST_LIKE -> likes.postPendingDelta(identifier);
            case COMMENT_LIKE -> likes.commentPendingDelta(identifier);
        };
        if (counter == Counter.POST_LIKE) {
            assertThat(likes.postPendingDeltas(List.of(identifier)).get(identifier)).isEqualTo((int) delta);
        } else if (counter == Counter.COMMENT_LIKE) {
            assertThat(likes.commentPendingDeltas(List.of(identifier)).get(identifier)).isEqualTo((int) delta);
        }
        return delta;
    }

    private void flush(Counter counter) {
        if (counter == Counter.VIEW) {
            views.flushPendingCounts();
        } else {
            likes.flushPendingCounts();
        }
    }

    private static class SimulatedCrash extends Error {
    }
}
