package com.shiguang.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.common.BizException;
import com.shiguang.content.CreatePostRequest;
import com.shiguang.content.PostService;
import com.shiguang.content.transcode.TranscodePublisher;
import com.shiguang.interaction.CommentService;
import com.shiguang.message.MessageService;
import com.shiguang.user.User;
import com.shiguang.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UploadOwnershipFlowTest {

    @Autowired private PostService postService;
    @Autowired private UserService userService;
    @Autowired private CommentService commentService;
    @Autowired private MessageService messageService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private StorageService storageService;
    @MockBean private TranscodePublisher transcodePublisher;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userService.findOrCreateByPhone("upload-" + UUID.randomUUID().toString().substring(0, 12));
        other = userService.findOrCreateByPhone("upload-" + UUID.randomUUID().toString().substring(0, 12));
        when(storageService.stat(anyString())).thenReturn(new StorageService.ObjectStat(128, "image/jpeg"));
        when(storageService.open(anyString(), anyLong(), anyLong()))
                .thenAnswer(invocation -> new ByteArrayInputStream(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        when(storageService.publicUrl(anyString())).thenAnswer(invocation -> "/api/media/" + invocation.getArgument(0));
        when(storageService.presignPut(anyString(), anyString()))
                .thenAnswer(invocation -> new PresignResult(invocation.getArgument(0), "https://upload.invalid/signed"));
    }

    @Test
    void unknownStoredKeyCannotBeClaimedByPost() {
        assertThatThrownBy(() -> imagePost(owner.getId(), "images/arbitrary-existing.jpg"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void otherUsersUploadCannotBeBoundToPost() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        assertThatThrownBy(() -> imagePost(other.getId(), key)).isInstanceOf(BizException.class);
    }

    @Test
    void ownImageUploadCanBeBoundWithoutCompression() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        assertThat(imagePost(owner.getId(), key)).isPositive();
    }

    @Test
    void oversizedImageIsRejected() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        when(storageService.stat(key)).thenReturn(new StorageService.ObjectStat(20L * 1024 * 1024 + 1, "image/jpeg"));
        assertThatThrownBy(() -> imagePost(owner.getId(), key)).isInstanceOf(BizException.class);
    }

    @Test
    void missingObjectIsRejected() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        when(storageService.stat(key)).thenThrow(StorageException.notFound(key));
        assertThatThrownBy(() -> imagePost(owner.getId(), key)).isInstanceOf(BizException.class);
    }

    @Test
    void actualContentTypeMustMatchRegistration() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        when(storageService.stat(key)).thenReturn(new StorageService.ObjectStat(128, "video/mp4"));
        assertThatThrownBy(() -> imagePost(owner.getId(), key)).isInstanceOf(BizException.class);
    }

    @Test
    void videoPurposeCannotBeUsedForImages() throws Exception {
        String key = upload(owner.getId(), "VIDEO", "video/mp4");
        when(storageService.stat(key)).thenReturn(new StorageService.ObjectStat(128, "video/mp4"));
        assertThatThrownBy(() -> imagePost(owner.getId(), key)).isInstanceOf(BizException.class);
    }

    @Test
    void avatarCannotReferenceSomeoneElsesUpload() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        assertThatThrownBy(() -> userService.updateProfile(other.getId(), "昵称", key, "简介"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void newExternalAvatarIsRejected() {
        assertThatThrownBy(() -> userService.updateProfile(owner.getId(), "昵称", "https://example.com/new.jpg", "简介"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void commentsAndRepliesRejectOthersUploads() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        Long postId = imagePost(owner.getId(), key);
        assertThatThrownBy(() -> commentService.create(postId, other.getId(), "评论", List.of(key)))
                .isInstanceOf(BizException.class);
        Long commentId = commentService.create(postId, owner.getId(), "楼层", List.of()).getId();
        assertThatThrownBy(() -> commentService.createReply(commentId, other.getId(), "回复", List.of(key)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void messagesRejectOthersUploads() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        assertThatThrownBy(() -> messageService.send(other.getId(), owner.getId(), "IMAGE", "", List.of(key), null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void registrationPersistsUploaderAndPurpose() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        assertThat(jdbc.queryForObject("SELECT uploader_id FROM storage_upload WHERE object_name = ?", Long.class, key))
                .isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT purpose FROM storage_upload WHERE object_name = ?", String.class, key))
                .isEqualTo("IMAGE");
    }

    @Test
    void unchangedLegacyAvatarAndItsDisplayedUrlRemainEditable() {
        String key = "images/legacy-avatar.jpg";
        jdbc.update("UPDATE user SET avatar_url = ? WHERE id = ?", key, owner.getId());
        assertThat(userService.updateProfile(owner.getId(), "昵称", "/api/media/" + key, "简介").getAvatarUrl())
                .isEqualTo(key);
        assertThat(userService.updateProfile(owner.getId(), "新昵称", key, "简介").getAvatarUrl()).isEqualTo(key);
    }

    @Test
    void unchangedLegacyHttpsAvatarRemainsEditable() {
        String url = "https://example.com/existing.jpg";
        jdbc.update("UPDATE user SET avatar_url = ? WHERE id = ?", url, owner.getId());
        assertThat(userService.updateProfile(owner.getId(), "昵称", url, "简介").getAvatarUrl()).isEqualTo(url);
    }

    @Test
    void publishedDerivativesAreRegisteredToPostOwner() throws Exception {
        String key = upload(owner.getId(), "VIDEO", "video/mp4");
        when(storageService.stat(key)).thenReturn(new StorageService.ObjectStat(128, "video/mp4"));
        CreatePostRequest request = new CreatePostRequest();
        request.setType("VIDEO");
        request.setVideoObject(key);
        Long postId = postService.create(request, owner.getId()).getId();
        String video = "videos/" + postId + "/" + UUID.randomUUID() + ".mp4";
        String cover = "covers/" + postId + "/" + UUID.randomUUID() + ".jpg";
        when(storageService.stat(video)).thenReturn(new StorageService.ObjectStat(128, "video/mp4"));
        postService.markPublished(postId, video, cover);
        assertThat(jdbc.queryForList("SELECT uploader_id FROM storage_upload WHERE object_name IN (?, ?)", Long.class, video, cover))
                .containsExactlyInAnyOrder(owner.getId(), owner.getId());
    }

    @Test
    void deletingOnePostPreservesObjectsStillUsedByAnotherPostOrAvatar() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        Long first = imagePost(owner.getId(), key);
        Long second = imagePost(owner.getId(), key);
        userService.updateProfile(owner.getId(), "昵称", key, "");
        postService.delete(first, owner.getId());
        postService.delete(second, owner.getId());
        assertThat(jdbc.queryForObject("SELECT deleted FROM storage_upload WHERE object_name = ?", Boolean.class, key)).isFalse();
        verify(storageService, never()).deleteObject(key);
        assertThat(userService.getById(owner.getId()).getAvatarUrl()).isEqualTo(key);
    }

    @Test
    void deletingLegacyForgedReferenceDoesNotDeleteAnotherUsersStorage() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        Long postId = imagePost(other.getId(), upload(other.getId(), "IMAGE", "image/jpeg"));
        jdbc.update("UPDATE post SET images = JSON_ARRAY(?) WHERE id = ?", key, postId);
        postService.delete(postId, other.getId());
        assertThat(jdbc.queryForObject("SELECT deleted FROM storage_upload WHERE object_name = ?", Boolean.class, key)).isFalse();
        verify(storageService, never()).deleteObject(key);
    }

    @Test
    void commentAndMessageReferencesProtectSharedObject() throws Exception {
        String key = upload(owner.getId(), "IMAGE", "image/jpeg");
        Long postId = imagePost(owner.getId(), key);
        commentService.create(postId, owner.getId(), "评论", List.of(key));
        messageService.send(owner.getId(), other.getId(), "IMAGE", "", List.of(key), null);
        postService.delete(postId, owner.getId());
        assertThat(jdbc.queryForObject("SELECT deleted FROM storage_upload WHERE object_name = ?", Boolean.class, key)).isFalse();
    }

    @Test
    void legacyBackfillOnlyClaimsUnambiguousGeneratedKeysAndIsIdempotent() throws Exception {
        String owned = "images/2026-10-16/" + UUID.randomUUID() + ".jpg";
        String shared = "images/2026-10-16/" + UUID.randomUUID() + ".jpg";
        String arbitrary = "images/arbitrary-existing.jpg";
        String external = "https://example.com/images/2026-10-16/" + UUID.randomUUID() + ".jpg";
        jdbc.update("INSERT INTO post (user_id, type, images, status) VALUES (?, 'IMAGE', ?, 'PUBLISHED')",
                owner.getId(), objectMapper.writeValueAsString(List.of(owned, shared, arbitrary, external)));
        jdbc.update("INSERT INTO post (user_id, type, images, status) VALUES (?, 'IMAGE', ?, 'PUBLISHED')",
                other.getId(), objectMapper.writeValueAsString(List.of(shared)));
        applyLegacyBackfill();
        assertThat(jdbc.queryForObject("SELECT uploader_id FROM storage_upload WHERE object_name = ?", Long.class, owned))
                .isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_upload WHERE object_name IN (?, ?, ?)", Long.class,
                shared, arbitrary, external)).isZero();
        jdbc.update("UPDATE storage_upload SET deleted = 1 WHERE object_name = ?", owned);
        applyLegacyBackfill();
        assertThat(jdbc.queryForObject("SELECT deleted FROM storage_upload WHERE object_name = ?", Boolean.class, owned)).isTrue();
    }

    private void applyLegacyBackfill() throws Exception {
        String script = Files.readString(Path.of("db/init/013_m14_upload_ownership.sql"));
        String backfill = script.substring(script.indexOf("DROP TEMPORARY TABLE"));
        for (String statement : backfill.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
    }

    @Test
    void presignRejectsMimeForWrongPurposeAndUnsafeExtension() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner.getId(), null, List.of()));
        try {
            mockMvc.perform(post("/api/upload/presign").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"IMAGE\",\"contentType\":\"video/mp4\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/upload/presign").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"IMAGE\",\"contentType\":\"image/jpeg\",\"extension\":\"../html\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long imagePost(Long userId, String key) {
        CreatePostRequest request = new CreatePostRequest();
        request.setType("IMAGE");
        request.setImages(List.of(key));
        return postService.create(request, userId).getId();
    }

    private String upload(Long userId, String purpose, String contentType) throws Exception {
        String response = mockMvc.perform(post("/api/upload/presign")
                            .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("type", purpose, "contentType", contentType))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("objectName").asText();
    }
}
