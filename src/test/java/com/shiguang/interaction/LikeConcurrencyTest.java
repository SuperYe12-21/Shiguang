package com.shiguang.interaction;

import com.shiguang.content.CreatePostRequest;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostVO;
import com.shiguang.content.PostService;
import com.shiguang.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.like.flush-interval-ms=99999999"})
class LikeConcurrencyTest {

    @Autowired
    private PostService postService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private UserService userService;

    @Test
    void concurrentLikesAndUnlikes_flushCountsStaysConsistent() throws Exception {
        Long authorId = userService.findOrCreateByPhone(randomPhone()).getId();
        PostVO post = postService.create(imageRequest(), authorId);

        int likeUsers = 16;
        List<Long> userIds = new java.util.ArrayList<>();
        for (int i = 0; i < likeUsers; i++) {
            userIds.add(userService.findOrCreateByPhone(randomPhone()).getId());
        }

        // 16 个用户并发点赞
        runConcurrently(likeUsers, userIds, (userId) -> likeService.likePost(post.getId(), userId));
        assertThat(countLikeRows(post.getId())).isEqualTo(likeUsers);

        likeService.flushPendingCounts();
        assertThat(loadPost(post.getId()).getLikeCount()).isEqualTo(likeUsers);

        // 前 8 个用户并发取消点赞
        runConcurrently(8, userIds, (userId) -> likeService.unlikePost(post.getId(), userId));
        assertThat(countLikeRows(post.getId())).isEqualTo(8);

        likeService.flushPendingCounts();
        Post after = loadPost(post.getId());
        assertThat(after.getLikeCount()).isEqualTo(8);

        // 再全部取消，计数不为负
        runConcurrently(likeUsers, userIds, (userId) -> likeService.unlikePost(post.getId(), userId));
        likeService.flushPendingCounts();
        assertThat(loadPost(post.getId()).getLikeCount()).isEqualTo(0);

        postService.delete(post.getId(), authorId);
    }

    @Test
    void sameUserDuplicateLike_isIdempotent() throws Exception {
        Long authorId = userService.findOrCreateByPhone(randomPhone()).getId();
        PostVO post = postService.create(imageRequest(), authorId);
        Long userId = userService.findOrCreateByPhone(randomPhone()).getId();

        likeService.likePost(post.getId(), userId);
        likeService.likePost(post.getId(), userId);
        likeService.likePost(post.getId(), userId);

        assertThat(countLikeRows(post.getId())).isEqualTo(1);
        likeService.flushPendingCounts();
        assertThat(loadPost(post.getId()).getLikeCount()).isEqualTo(1);

        likeService.unlikePost(post.getId(), userId);
        likeService.flushPendingCounts();
        assertThat(loadPost(post.getId()).getLikeCount()).isEqualTo(0);
        assertThat(countLikeRows(post.getId())).isZero();

        postService.delete(post.getId(), authorId);
    }

    private void runConcurrently(int count, List<Long> userIds,
                                 java.util.function.Consumer<Long> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < count; i++) {
            Long userId = userIds.get(i);
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.accept(userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    private int countLikeRows(Long postId) {
        return Math.toIntExact(postLikeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)));
    }

    private Post loadPost(Long postId) {
        return postMapper.selectById(postId);
    }

    private static CreatePostRequest imageRequest() {
        CreatePostRequest request = new CreatePostRequest();
        request.setType("IMAGE");
        request.setTitle("并发点赞测试");
        request.setImages(List.of("images/a.jpg"));
        return request;
    }

    private static String randomPhone() {
        return "139" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
    }
}