package com.shiguang.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.user.User;
import com.shiguang.user.UserMapper;
import com.shiguang.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminUsersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void nonAdminCannotListUsers() throws Exception {
        String token = login(newPhone());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listUsersSortedByNewestRegistrationWithCursor() throws Exception {
        String tag = "新用户" + ThreadLocalRandom.current().nextInt(1000000, 9999999);
        String firstPhone = newPhone();
        String secondPhone = newPhone();
        String thirdPhone = newPhone();
        rename(login(firstPhone), tag + "-甲");
        rename(login(secondPhone), tag + "-乙");
        rename(login(thirdPhone), tag + "-丙");
        String adminToken = adminToken();

        MvcResult page = mockMvc.perform(get("/api/admin/users")
                        .param("keyword", tag)
                        .param("limit", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].nickname").value(tag + "-丙"))
                .andExpect(jsonPath("$.data.items[0].phone").value(thirdPhone))
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.items[0].postCount").value(0))
                .andExpect(jsonPath("$.data.items[1].nickname").value(tag + "-乙"))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(page.getResponse().getContentAsString());
        String cursor = body.path("data").path("nextCursor").asText();

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", tag)
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value(tag + "-甲"))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void keywordMatchesPhone() throws Exception {
        String phone = newPhone();
        login(phone);
        String adminToken = adminToken();

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", phone)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(phone));
    }

    private String adminToken() throws Exception {
        String phone = newPhone();
        String token = login(phone);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        user.setRole(UserRole.ADMIN);
        userMapper.updateById(user);
        return token;
    }

    private void rename(String token, String nickname) throws Exception {
        mockMvc.perform(put("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
    }

    private static String newPhone() {
        return "136" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
    }

    private String login(String phone) throws Exception {
        mockMvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
