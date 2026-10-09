package com.shiguang.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.common.SearchText;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.content.PostVO;
import com.shiguang.content.PostVisibility;
import com.shiguang.storage.StorageService;
import com.shiguang.user.User;
import com.shiguang.user.UserMapper;
import com.shiguang.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理员作品管理：权限每次查库确认（管理流量小，且改角色立即生效） */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    /** 作者关键字最多命中的用户数，避免 IN 条件过长 */
    private static final int AUTHOR_MATCH_LIMIT = 100;

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    private final StorageService storageService;

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new BizException(403, "需要管理员权限");
        }
    }

    /** 全量作品列表：含处理中 / 失败 / 仅自己可见 / 已下架 */
    public PageVO<AdminPostVO> listPosts(String status, String visibility, String authorKeyword,
                                         Long postId, String cursor, int limit) {
        int size = normalizeLimit(limit);
        String keyword = SearchText.normalize(authorKeyword);
        List<Long> authorIds = keyword.isEmpty() ? List.of() : findAuthorIds(keyword);
        if (!keyword.isEmpty() && authorIds.isEmpty()) {
            return emptyPage();
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (postId != null) {
            wrapper.eq(Post::getId, postId);
        }
        PostStatus parsedStatus = parseStatus(status);
        if (parsedStatus != null) {
            wrapper.eq(Post::getStatus, parsedStatus);
        }
        PostVisibility parsedVisibility = parseVisibility(visibility);
        if (parsedVisibility != null) {
            wrapper.eq(Post::getVisibility, parsedVisibility);
        }
        if (!authorIds.isEmpty()) {
            wrapper.in(Post::getUserId, authorIds);
        }
        if (cursor != null && !cursor.isBlank()) {
            Cursor c = parseCursor(cursor);
            wrapper.and(w -> w.lt(Post::getCreatedAt, c.time())
                    .or(o -> o.eq(Post::getCreatedAt, c.time()).lt(Post::getId, c.id())));
        }
        wrapper.orderByDesc(Post::getCreatedAt)
                .orderByDesc(Post::getId)
                .last("LIMIT " + (size + 1));
        List<Post> rows = postMapper.selectList(wrapper);

        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        Map<Long, User> authors = loadAuthors(page);
        List<AdminPostVO> items = page.stream().map(post -> toVO(post, authors)).toList();
        String nextCursor = hasMore && !page.isEmpty() ? cursorOf(page.get(page.size() - 1)) : null;
        return PageVO.<AdminPostVO>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(nextCursor != null)
                .build();
    }

    public PostVO block(Long postId, String reason) {
        return postService.adminBlock(postId, reason);
    }

    public PostVO unblock(Long postId) {
        return postService.adminUnblock(postId);
    }

    public void delete(Long postId) {
        postService.adminDelete(postId);
    }

    private List<Long> findAuthorIds(String keyword) {
        String like = SearchText.escapeLike(keyword);
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .and(w -> w.like(User::getNickname, like).or().like(User::getPhone, like))
                .last("LIMIT " + AUTHOR_MATCH_LIMIT));
        return users.stream().map(User::getId).toList();
    }

    private Map<Long, User> loadAuthors(List<Post> posts) {
        List<Long> ids = posts.stream().map(Post::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private AdminPostVO toVO(Post post, Map<Long, User> authors) {
        User author = authors.get(post.getUserId());
        return AdminPostVO.builder()
                .id(post.getId())
                .type(post.getType())
                .status(post.getStatus())
                .visibility(post.getVisibility())
                .title(post.getTitle())
                .coverUrl(coverUrl(post))
                .blockReason(post.getBlockReason())
                .failReason(post.getFailReason())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .viewCount(post.getViewCount() == null ? 0L : post.getViewCount())
                .createdAt(post.getCreatedAt())
                .authorId(post.getUserId())
                .authorNickname(author == null ? "用户已注销" : author.getNickname())
                .authorPhone(author == null ? "" : author.getPhone())
                .authorAvatarUrl(author == null ? "" : avatarUrl(author.getAvatarUrl()))
                .build();
    }

    private String coverUrl(Post post) {
        String object = !isBlank(post.getCoverObject()) ? post.getCoverObject()
                : firstImage(post);
        return isBlank(object) ? "" : storageService.publicUrl(object);
    }

    private static String firstImage(Post post) {
        List<String> images = post.getImagesObject();
        return images == null || images.isEmpty() ? "" : images.get(0);
    }

    private String avatarUrl(String avatarUrl) {
        if (isBlank(avatarUrl) || avatarUrl.startsWith("http")) {
            return avatarUrl;
        }
        return storageService.publicUrl(avatarUrl);
    }

    private static PostStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return PostStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(1, "作品状态参数非法: " + raw);
        }
    }

    private static PostVisibility parseVisibility(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return PostVisibility.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(1, "可见性参数非法: " + raw);
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static Cursor parseCursor(String cursor) {
        try {
            String[] parts = cursor.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new Cursor(
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(parts[0])), ZoneId.systemDefault()),
                    Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new BizException(1, "分页游标无效");
        }
    }

    private static String cursorOf(Post post) {
        long epoch = post.getCreatedAt() == null ? 0
                : post.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return epoch + "_" + post.getId();
    }

    private static PageVO<AdminPostVO> emptyPage() {
        return PageVO.<AdminPostVO>builder()
                .items(new ArrayList<>())
                .nextCursor(null)
                .hasMore(false)
                .build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record Cursor(LocalDateTime time, Long id) {
    }
}
