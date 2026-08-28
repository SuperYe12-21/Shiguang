package com.shiguang.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.content.transcode.TranscodePublisher;
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
import java.util.HashSet;
import java.util.List;
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
class FeedFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorageService storageService;

    @MockBean
    private TranscodePublisher transcodePublisher;

    private final String userAPhone = "133" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
    private final String userBPhone = "134" + ThreadLocalRandom.current().nextInt(10000000, 99999999);

    private final java.util.Map<Long, String> createdPosts = new java.util.HashMap<>();
    private String userAToken;
    private String userBToken;

    @AfterEach
    void cleanup() throws Exception {
        for (java.util.Map.Entry<Long, String> entry : createdPosts.entrySet()) {
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
    void feedPagination_noDuplicates_sortedByTimeDesc() throws Exception {
        userAToken = login(userAPhone);
        for (int i = 0; i < 25; i++) {
            createImagePost(userAToken, "分页作品" + i);
        }

        List<Long> allIds = new ArrayList<>();
        List<String> allTimes = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        while (pages < 10) {
            MvcResult result = mockMvc.perform(get("/api/posts/feed")
                            .header("Authorization", "Bearer " + userAToken)
                            .param("limit", "10")
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
                allIds.add(item.path("id").asLong());
                allTimes.add(item.path("createdAt").asText());
            }
            pages++;
            boolean hasMore = json.path("data").path("hasMore").asBoolean(false);
            String nextCursor = json.path("data").path("nextCursor").asText(null);
            if (!hasMore || nextCursor == null || nextCursor.isEmpty()) {
                break;
            }
            cursor = nextCursor;
        }

        // 无重复
        Set<Long> unique = new HashSet<>(allIds);
        assertThat(unique.size()).isEqualTo(allIds.size());
        // 至少拿到我们创建的 25 条
        assertThat(allIds.size()).isGreaterThanOrEqualTo(25);
        // 时间倒序（同秒按 id 倒序）
        for (int i = 1; i < allTimes.size(); i++) {
            String prev = allTimes.get(i - 1);
            String curr = allTimes.get(i);
            int cmp = prev.compareTo(curr);
            if (cmp == 0) {
                assertThat(allIds.get(i - 1)).isGreaterThan(allIds.get(i));
            } else {
                assertThat(cmp).isPositive();
            }
        }
    }

    @Test
    void userPosts_ownShowsAllStatuses_othersOnlyPublished() throws Exception {
        userAToken = login(userAPhone);
        userBToken = login(userBPhone);
        long publishedId = createImagePost(userAToken, "已发布作品");
        long processingId = createVideoPost(userAToken);

        // 他人视角：只有已发布
        mockMvc.perform(get("/api/user/" + userIdOf(userBToken) + "/posts")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk());

        String ownPosts = mockMvc.perform(get("/api/user/" + userIdOf(userAToken) + "/posts")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode ownJson = objectMapper.readTree(ownPosts);
        Set<Long> ownIds = new HashSet<>();
        ownJson.path("data").path("items").forEach(n -> ownIds.add(n.path("id").asLong()));
        assertThat(ownIds).contains(publishedId, processingId);

        String otherPosts = mockMvc.perform(get("/api/user/" + userIdOf(userAToken) + "/posts")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode otherJson = objectMapper.readTree(otherPosts);
        Set<Long> otherIds = new HashSet<>();
        otherJson.path("data").path("items").forEach(n -> otherIds.add(n.path("id").asLong()));
        assertThat(otherIds).contains(publishedId);
        assertThat(otherIds).doesNotContain(processingId);
    }

    @Test
    void feedCache_evictsWhenNewPostPublished() throws Exception {
        userAToken = login(userAPhone);
        userBToken = login(userBPhone);
        createImagePost(userAToken, "老作品");

        // 首次拉取写入缓存
        long firstId = firstFeedItemId(userAToken);

        // 新作品发布后缓存应失效，首屏立刻出现新作品
        long newPostId = createImagePost(userBToken, "新作品");
        long afterId = firstFeedItemId(userAToken);

        assertThat(afterId).isEqualTo(newPostId);
        assertThat(firstId).isNotEqualTo(newPostId);
    }

    @Test
    void feedAndDetail_arePublicWithoutLogin() throws Exception {
        userAToken = login(userAPhone);
        long postId = createImagePost(userAToken, "公开作品");

        mockMvc.perform(get("/api/posts/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false));
    }

    @Test
    void feedAuthorFollowing_reflectsFollowState() throws Exception {
        userAToken = login(userAPhone);
        userBToken = login(userBPhone);
        long idA = userIdOf(userAToken);
        long idB = userIdOf(userBToken);

        // A 关注 B，B 发布作品
        mockMvc.perform(post("/api/follow/" + idB).header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());
        long postB = createImagePost(userBToken, "被关注者的作品");
        createImagePost(userAToken, "自己的作品");

        // A 拉 Feed：B 的作品 author.following=true，自己的作品为 false
        MvcResult result = mockMvc.perform(get("/api/posts/feed")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items");
        boolean foundB = false;
        boolean foundOwn = false;
        for (JsonNode item : items) {
            if (item.path("id").asLong() == postB) {
                assertThat(item.path("author").path("following").asBoolean()).isTrue();
                foundB = true;
            }
            if (item.path("author").path("id").asLong() == idA) {
                assertThat(item.path("author").path("following").asBoolean()).isFalse();
                foundOwn = true;
            }
        }
        assertThat(foundB).isTrue();
        assertThat(foundOwn).isTrue();
    }

    private long firstFeedItemId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/posts/feed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("items").get(0).path("id").asLong();
    }

    private long userIdOf(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("id").asLong();
    }

    private long createImagePost(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IMAGE\",\"title\":\"" + title + "\",\"images\":[\"images/a.jpg\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long postId = json.path("data").path("id").asLong();
        createdPosts.put(postId, token);
        return postId;
    }

    private long createVideoPost(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIDEO\",\"title\":\"转码中\",\"videoObject\":\"videos/src.mp4\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long postId = json.path("data").path("id").asLong();
        createdPosts.put(postId, token);
        return postId;
    }

    private String login(String p) throws Exception {
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