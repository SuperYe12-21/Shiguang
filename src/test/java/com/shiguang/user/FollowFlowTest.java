package com.shiguang.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.sms.cooldown-seconds=0", "app.sms.hourly-limit=100", "app.like.flush-interval-ms=99999999"})
@AutoConfigureMockMvc
class FollowFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private UserService userService;

    @MockBean
    private StorageService storageService;

    private final Map<Long, String> createdPosts = new HashMap<>();
    private final List<long[]> createdFollows = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long[] pair : createdFollows) {
            followMapper.delete(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowerId, pair[0])
                    .eq(Follow::getFolloweeId, pair[1]));
        }
        createdFollows.clear();
        for (Map.Entry<Long, String> entry : createdPosts.entrySet()) {
            try {
                mockMvc.perform(delete("/api/posts/" + entry.getKey())
                        .header("Authorization", "Bearer " + entry.getValue()));
            } catch (Exception ignored) {
                // 已删除的作品直接忽略
            }
        }
        createdPosts.clear();
    }

    @Test
    void followUnfollowFlow_updatesCountsAndState() throws Exception {
        String tokenA = loginNew();
        String tokenB = loginNew();
        long idA = userIdOf(tokenA);
        long idB = userIdOf(tokenB);

        // A 关注 B
        mockMvc.perform(post("/api/follow/" + idB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        // 重复关注幂等
        mockMvc.perform(post("/api/follow/" + idB).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        // B 关注 A
        mockMvc.perform(post("/api/follow/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        // 主页聚合：B 的粉丝 1、关注 1，A 视角 followedByMe=true，且不暴露手机号
        mockMvc.perform(get("/api/user/" + idB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1))
                .andExpect(jsonPath("$.data.followingCount").value(1))
                .andExpect(jsonPath("$.data.followedByMe").value(true))
                .andExpect(jsonPath("$.data.phone").doesNotExist());

        // 未登录看主页：followedByMe=false
        mockMvc.perform(get("/api/user/" + idB))
                .andExpect(jsonPath("$.data.followedByMe").value(false));

        // A 取消关注
        mockMvc.perform(delete("/api/follow/" + idB).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.followerCount").value(0));
    }

    @Test
    void followSelfOrMissingUser_fails() throws Exception {
        String tokenA = loginNew();
        long idA = userIdOf(tokenA);

        mockMvc.perform(post("/api/follow/" + idA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("不能关注自己"));

        mockMvc.perform(post("/api/follow/99999999").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void followerLists_cursorPagination_noDuplicates() throws Exception {
        String tokenA = loginNew();
        long idA = userIdOf(tokenA);

        // 直接造 12 个粉丝关注 A
        List<Long> followerIds = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long followerId = userService.findOrCreateByPhone("155" + ThreadLocalRandom.current().nextInt(10000000, 99999999)).getId();
            followService.follow(followerId, idA);
            followerIds.add(followerId);
            createdFollows.add(new long[]{followerId, idA});
        }

        // 分页拉粉丝列表（每页 5）
        Set<Long> seen = new HashSet<>();
        String cursor = null;
        int pages = 0;
        while (pages < 10) {
            MvcResult result = mockMvc.perform(get("/api/user/" + idA + "/followers")
                            .header("Authorization", "Bearer " + tokenA)
                            .param("limit", "5")
                            .param("cursor", cursor == null ? "" : cursor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode items = json.path("data").path("items");
            if (items.isEmpty()) {
                break;
            }
            for (JsonNode item : items) {
                seen.add(item.path("id").asLong());
            }
            pages++;
            boolean hasMore = json.path("data").path("hasMore").asBoolean(false);
            String next = json.path("data").path("nextCursor").asText(null);
            if (!hasMore || next == null || next.isEmpty()) {
                break;
            }
            cursor = next;
        }

        assertThat(seen).containsAll(followerIds);
        assertThat(seen.size()).isEqualTo(followerIds.size());
        assertThat(pages).isGreaterThanOrEqualTo(3);

        mockMvc.perform(get("/api/user/" + idA).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.followerCount").value(12));
    }

    @Test
    void profilePostCount_countsPublishedOnly() throws Exception {
        String tokenA = loginNew();
        String tokenB = loginNew();
        long idB = userIdOf(tokenB);

        // A 发布一篇图文，B 发布一篇转码中的视频（不计入）
        MvcResult created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IMAGE\",\"title\":\"主页统计测试\",\"images\":[\"images/a.jpg\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        long postId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        createdPosts.put(postId, tokenA);

        MvcResult createdVideo = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIDEO\",\"title\":\"转码中\",\"videoObject\":\"videos/src.mp4\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long videoId = objectMapper.readTree(createdVideo.getResponse().getContentAsString()).path("data").path("id").asLong();
        createdPosts.put(videoId, tokenB);

        mockMvc.perform(get("/api/user/" + userIdOf(tokenA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.postCount").value(1));
        mockMvc.perform(get("/api/user/" + idB).header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.postCount").value(0));
    }

    @Test
    void meEndpoint_requiresLogin() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicProfileAndLists_accessibleWithoutLogin() throws Exception {
        String tokenA = loginNew();
        String tokenB = loginNew();
        long idB = userIdOf(tokenB);

        mockMvc.perform(post("/api/follow/" + idB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/" + idB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followedByMe").value(false));
        mockMvc.perform(get("/api/user/" + idB + "/followers"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/user/" + idB + "/following"))
                .andExpect(status().isOk());
    }

    private long userIdOf(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("id").asLong();
    }

    private String loginNew() throws Exception {
        String p = "150" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
        mockMvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + p + "\"}"))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + p + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("accessToken").asText();
    }
}