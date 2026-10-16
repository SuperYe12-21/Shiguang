package com.shiguang.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.storage.StorageService;
import com.shiguang.storage.UploadFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorageService storageService;

    private final String phone = "136" + ThreadLocalRandom.current().nextInt(10000000, 99999999);

    @Test
    void updateProfileReflectsOnMe() throws Exception {
        String token = login(phone);
        String key = UploadFixtures.uploaded(mockMvc, objectMapper, storageService, token, "IMAGE");
        org.mockito.Mockito.when(storageService.publicUrl(key)).thenReturn("https://example.com/a.png");
        mockMvc.perform(put("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("nickname", "拾光小鹿", "avatarUrl", key, "bio", "记录生活"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("拾光小鹿"))
                .andExpect(jsonPath("$.data.bio").value("记录生活"));

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("拾光小鹿"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/a.png"));
    }

    @Test
    void updateWithBlankNicknameFails() throws Exception {
        String token = login(phone);
        mockMvc.perform(put("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \"}"))
                .andExpect(status().isBadRequest());
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
