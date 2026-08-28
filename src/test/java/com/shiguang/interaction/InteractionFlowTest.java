package com.shiguang.interaction;

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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.sms.cooldown-seconds=0", "app.sms.hourly-limit=100", "app.like.flush-interval-ms=99999999"})
@AutoConfigureMockMvc
class InteractionFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorageService storageService;

    @MockBean
    private TranscodePublisher transcodePublisher;

    private final String userAPhone = "131" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
    private final String userBPhone = "132" + ThreadLocalRandom.current().nextInt(10000000, 99999999);

    private final List<Long> createdPosts = new ArrayList<>();
    private String userAToken;

    @AfterEach
    void cleanup() throws Exception {
        for (Long postId : createdPosts) {
            try {
                mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + userAToken));
            } catch (Exception ignored) {
                // 已被测试删除的作品直接忽略
            }
        }
        createdPosts.clear();
    }

    @Test
    void likeUnlikeFlow_updatesCountAndState() throws Exception {
        String tokenA = login(userAPhone);
        userAToken = tokenA;
        String tokenB = login(userBPhone);
        long postId = createImagePost(tokenA, "点赞测试");

        // B 点赞
        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        // 重复点赞幂等
        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        // A 点赞后计数 2
        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.likeCount").value(2));

        // B 取消点赞
        mockMvc.perform(delete("/api/posts/" + postId + "/like").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        // 详情页按登录人返回点赞状态
        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.liked").value(false));
    }

    @Test
    void likeUnpublishedPost_fails() throws Exception {
        String tokenA = login(userAPhone);
        userAToken = tokenA;
        long postId = createVideoPost(tokenA);

        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("作品尚未发布，暂时无法互动"));
    }

    @Test
    void commentFlow_createListLikeDelete_permissions() throws Exception {
        String tokenA = login(userAPhone);
        userAToken = tokenA;
        String tokenB = login(userBPhone);
        long postId = createImagePost(tokenA, "评论测试");

        // B 发表评论
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"拍得真好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("拍得真好"))
                .andExpect(jsonPath("$.data.likeCount").value(0));

        // 列表
        MvcResult listResult = mockMvc.perform(get("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("拍得真好"))
                .andExpect(jsonPath("$.data.items[0].liked").value(false))
                .andExpect(jsonPath("$.data.items[0].author.nickname").exists())
                .andReturn();
        JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
        long commentId = listJson.path("data").path("items").get(0).path("id").asLong();

        // 评论点赞/取消
        mockMvc.perform(post("/api/comments/" + commentId + "/like").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(delete("/api/comments/" + commentId + "/like").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));

        // 作品评论数联动
        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.commentCount").value(1));

        // A 不能删 B 的评论
        mockMvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        // B 删自己的评论
        mockMvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/posts/" + postId + "/comments").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.commentCount").value(0));
    }

    @Test
    void blankComment_fails() throws Exception {
        String tokenA = login(userAPhone);
        userAToken = tokenA;
        long postId = createImagePost(tokenA, "空评论测试");

        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
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
        createdPosts.add(postId);
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
        createdPosts.add(postId);
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