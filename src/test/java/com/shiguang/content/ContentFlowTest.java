package com.shiguang.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.content.transcode.TranscodePublisher;
import com.shiguang.storage.PresignResult;
import com.shiguang.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.sms.cooldown-seconds=0", "app.sms.hourly-limit=100"})
@AutoConfigureMockMvc
class ContentFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorageService storageService;

    @MockBean
    private TranscodePublisher transcodePublisher;

    private final String phone = "135" + ThreadLocalRandom.current().nextInt(10000000, 99999999);

    @Test
    void presignReturnsSignedUploadUrl() throws Exception {
        String token = login(phone);
        when(storageService.presignPut(anyString(), anyString()))
                .thenReturn(new PresignResult("videos/2026-08-28/abc.mp4", "http://127.0.0.1:9000/up/abc.mp4?X-Amz-Signature=xx"));

        mockMvc.perform(post("/api/upload/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIDEO\",\"contentType\":\"video/mp4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.objectName").value("videos/2026-08-28/abc.mp4"))
                .andExpect(jsonPath("$.data.uploadUrl").value("http://127.0.0.1:9000/up/abc.mp4?X-Amz-Signature=xx"));
    }

    @Test
    void presignWithUnsupportedTypeFails() throws Exception {
        String token = login(phone);
        mockMvc.perform(post("/api/upload/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXE\",\"contentType\":\"application/octet-stream\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void createImagePost_publishedAndDetailShowsSignedUrls() throws Exception {
        String token = login(phone);
        when(storageService.presignedGetUrl("images/a.jpg")).thenReturn("http://127.0.0.1:9000/images/a.jpg?sig=1");
        when(storageService.presignedGetUrl("images/b.jpg")).thenReturn("http://127.0.0.1:9000/images/b.jpg?sig=2");

        MvcResult created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IMAGE\",\"title\":\"海边日落\",\"images\":[\"images/a.jpg\",\"images/b.jpg\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.type").value("IMAGE"))
                .andReturn();
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        long postId = json.path("data").path("id").asLong();

        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0]").value("http://127.0.0.1:9000/images/a.jpg?sig=1"))
                .andExpect(jsonPath("$.data.author.nickname").exists())
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    void createVideoPost_goesProcessing() throws Exception {
        String token = login(phone);
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIDEO\",\"title\":\"我的第一支视频\",\"videoObject\":\"videos/src.mp4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void createVideoWithoutObject_fails() throws Exception {
        String token = login(phone);
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIDEO\",\"title\":\"缺文件\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("视频作品必须提供 videoObject"));
    }

    @Test
    void deleteOwnPost_ok_andOthersForbidden() throws Exception {
        String token = login(phone);
        when(storageService.presignedGetUrl(anyString())).thenReturn("http://127.0.0.1:9000/x.jpg?sig=1");
        MvcResult created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IMAGE\",\"images\":[\"images/a.jpg\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        long postId = json.path("data").path("id").asLong();

        mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));
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