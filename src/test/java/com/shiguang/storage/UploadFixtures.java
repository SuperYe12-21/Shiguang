package com.shiguang.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class UploadFixtures {

    private UploadFixtures() {
    }

    public static String uploaded(MockMvc mockMvc, ObjectMapper objectMapper, StorageService storage,
                                  String token, String purpose) throws Exception {
        String mime = "VIDEO".equals(purpose) ? "video/mp4" : "image/jpeg";
        when(storage.presignPut(anyString(), anyString()))
                .thenAnswer(invocation -> new PresignResult(invocation.getArgument(0), "https://upload.invalid/signed"));
        String response = mockMvc.perform(post("/api/upload/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", purpose, "contentType", mime))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String key = objectMapper.readTree(response).path("data").path("objectName").asText();
        when(storage.stat(key)).thenReturn(new StorageService.ObjectStat(128, mime));
        if (!"VIDEO".equals(purpose)) {
            when(storage.open(key, 0, 32)).thenAnswer(invocation ->
                    new ByteArrayInputStream(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        }
        return key;
    }
}
