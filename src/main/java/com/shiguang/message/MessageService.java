package com.shiguang.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.interaction.LikeService;
import com.shiguang.storage.StorageService;
import com.shiguang.user.FollowService;
import com.shiguang.user.User;
import com.shiguang.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_CONVERSATIONS = 20;
    private static final int MAX_CONVERSATIONS = 50;
    private static final int DEFAULT_MESSAGES = 30;
    private static final int MAX_MESSAGES = 50;
    private static final int MAX_CONTENT = 1000;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_OBJECT_NAME = 200;
    private static final int SUMMARY_TITLE_LENGTH = 30;

    private final ConversationMapper conversationMapper;
    private final PrivateMessageMapper messageMapper;
    private final PostMapper postMapper;
    private final LikeService likeService;
    private final FollowService followService;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    /** 会话列表：按最近消息倒序，游标格式 millis_id */
    public PageVO<ConversationVO> conversations(Long me, String cursor, int limit) {
        int size = normalize(limit, DEFAULT_CONVERSATIONS, MAX_CONVERSATIONS);
        Cursor c = parseCursor(cursor);
        List<Conversation> rows = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .isNotNull(Conversation::getLastMessageAt)
                .and(w -> w.eq(Conversation::getUserAId, me).or().eq(Conversation::getUserBId, me))
                .and(c != null, w -> w
                        .lt(Conversation::getLastMessageAt, c.time())
                        .or(w2 -> w2.eq(Conversation::getLastMessageAt, c.time()).lt(Conversation::getId, c.id())))
                .orderByDesc(Conversation::getLastMessageAt)
                .orderByDesc(Conversation::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = rows.size() > size;
        List<Conversation> page = hasMore ? rows.subList(0, size) : rows;
        if (page.isEmpty()) {
            return PageVO.<ConversationVO>builder().items(List.of()).hasMore(false).build();
        }

        List<Long> peerIds = page.stream().map(row -> peerOf(row, me)).distinct().toList();
        Map<Long, User> peers = userMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> lastIds = page.stream().map(Conversation::getLastMessageId).filter(Objects::nonNull).toList();
        Map<Long, PrivateMessage> lastMessages = lastIds.isEmpty() ? Map.of()
                : messageMapper.selectBatchIds(lastIds).stream()
                .collect(Collectors.toMap(PrivateMessage::getId, Function.identity()));
        List<Long> cardPostIds = lastMessages.values().stream()
                .filter(message -> MessageType.POST_CARD.equals(typeOf(message)))
                .map(PrivateMessage::getPostId)
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, Post> cardPosts = cardPostIds.isEmpty() ? Map.of()
                : postMapper.selectBatchIds(cardPostIds).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, Long> unreadMap = unreadMap(me, page.stream().map(Conversation::getId).toList());

        List<ConversationVO> items = page.stream()
                .filter(row -> peers.containsKey(peerOf(row, me)))
                .map(row -> {
                    PrivateMessage last = row.getLastMessageId() == null ? null : lastMessages.get(row.getLastMessageId());
                    return ConversationVO.builder()
                            .id(row.getId())
                            .peer(toPeer(peers.get(peerOf(row, me))))
                            .lastContent(summaryOf(last, cardPosts))
                            .lastSenderId(last == null ? null : last.getSenderId())
                            .lastMessageAt(row.getLastMessageAt())
                            .unread(unreadMap.getOrDefault(row.getId(), 0L))
                            .build();
                })
                .toList();

        boolean more = hasMore && !items.isEmpty();
        return PageVO.<ConversationVO>builder()
                .items(items)
                .nextCursor(more ? cursorOf(page.get(page.size() - 1)) : null)
                .hasMore(more)
                .build();
    }

    /** 聊天页：对方信息 + 能否发送 + 历史消息（倒序，游标=消息 id） */
    public ChatPageVO chat(Long me, Long peerId, String cursor, int limit) {
        User peer = userMapper.selectById(peerId);
        if (peer == null) {
            throw new BizException(404, "用户不存在");
        }
        if (peerId.equals(me)) {
            throw new BizException(1, "不能和自己聊天");
        }
        int size = normalize(limit, DEFAULT_MESSAGES, MAX_MESSAGES);
        Conversation conv = find(me, peerId);

        List<PrivateMessage> rows = List.of();
        Long cursorId = parseId(cursor);
        if (conv != null) {
            rows = messageMapper.selectList(new LambdaQueryWrapper<PrivateMessage>()
                    .eq(PrivateMessage::getConversationId, conv.getId())
                    .lt(cursorId != null, PrivateMessage::getId, cursorId)
                    .orderByDesc(PrivateMessage::getId)
                    .last("LIMIT " + (size + 1)));
        }
        boolean hasMore = rows.size() > size;
        List<PrivateMessage> page = hasMore ? rows.subList(0, size) : rows;
        SendState state = sendState(me, peerId, conv);
        return ChatPageVO.builder()
                .peer(toPeer(peer))
                .canSend(state.canSend())
                .hint(state.hint())
                .items(toVOs(page))
                .nextCursor(hasMore ? page.get(page.size() - 1).getId().toString() : null)
                .hasMore(hasMore)
                .build();
    }

    /** 发送私信；非互关且对方未回复时最多先发一条 */
    @Transactional
    public PrivateMessageVO send(Long me, Long toUserId, String rawType, String rawContent,
                                 List<String> rawImages, Long postId) {
        MessageType type = parseType(rawType);
        String content = rawContent == null ? "" : rawContent.trim();
        List<String> images = normalizeImages(rawImages);
        if (type == MessageType.TEXT && content.isEmpty()) {
            throw new BizException(1, "消息不能为空");
        }
        if (type == MessageType.IMAGE && images.isEmpty()) {
            throw new BizException(1, "请选择要发送的图片");
        }
        if (type == MessageType.POST_CARD && postId == null) {
            throw new BizException(1, "要分享的作品不存在");
        }
        if (content.length() > MAX_CONTENT) {
            throw new BizException(1, "消息最长 " + MAX_CONTENT + " 字");
        }
        if (toUserId == null || me.equals(toUserId)) {
            throw new BizException(1, "不能给自己发私信");
        }
        if (userMapper.selectById(toUserId) == null) {
            throw new BizException(404, "用户不存在");
        }
        if (type == MessageType.POST_CARD) {
            // 作品必须存在且对发送者可见（仅自己可见的作品只有作者能分享）
            likeService.requireAccessiblePost(postId, me);
        }

        Conversation conv = findOrCreate(me, toUserId);
        SendState state = sendState(me, toUserId, conv);
        if (!state.canSend()) {
            throw new BizException(1, state.hint());
        }

        PrivateMessage message = new PrivateMessage();
        message.setConversationId(conv.getId());
        message.setSenderId(me);
        message.setReceiverId(toUserId);
        message.setContent(content);
        message.setType(type);
        message.setImagesObject(images.isEmpty() ? null : images);
        message.setPostId(type == MessageType.POST_CARD ? postId : null);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);

        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conv.getId())
                .set(Conversation::getLastMessageId, message.getId())
                .set(Conversation::getLastMessageAt, message.getCreatedAt()));

        PrivateMessageVO vo = singleVO(message);
        eventPublisher.publishEvent(new PrivateMessageSentEvent(me, toUserId, vo));
        return vo;
    }

    private PrivateMessageVO singleVO(PrivateMessage message) {
        return toVOs(List.of(message)).get(0);
    }

    private static MessageType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return MessageType.TEXT;
        }
        try {
            return MessageType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(1, "不支持的消息类型: " + rawType);
        }
    }

    /** 图片对象名去重 + 数量/长度校验（私信最多 9 张） */
    private static List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> objects = images.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (objects.size() > MAX_IMAGES) {
            throw new BizException(1, "最多发送 " + MAX_IMAGES + " 张图片");
        }
        for (String object : objects) {
            if (object.length() > MAX_OBJECT_NAME) {
                throw new BizException(1, "图片对象名非法");
            }
        }
        return objects;
    }

    /** 把与某人的会话标记为已读，返回我的未读总数 */
    @Transactional
    public long markRead(Long me, Long peerId) {
        Conversation conv = find(me, peerId);
        if (conv != null && conv.getLastMessageId() != null) {
            String column = me.equals(conv.getUserAId()) ? "a_last_read_id" : "b_last_read_id";
            conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                    .eq(Conversation::getId, conv.getId())
                    .setSql(column + " = GREATEST(" + column + ", " + conv.getLastMessageId() + ")"));
        }
        return unreadCount(me);
    }

    public long unreadCount(Long me) {
        return conversationMapper.countUnread(me);
    }

    // ---------- 内部 ----------

    private record SendState(boolean canSend, String hint) {
    }

    private record Cursor(LocalDateTime time, Long id) {
    }

    /** 非互关：对方回复过则放行；否则我最多先发 1 条 */
    private SendState sendState(Long me, Long peerId, Conversation conv) {
        boolean mutual = followService.isFollowing(me, peerId) && followService.isFollowing(peerId, me);
        if (mutual) {
            return new SendState(true, null);
        }
        boolean peerReplied = conv != null && messageMapper.selectCount(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getConversationId, conv.getId())
                .eq(PrivateMessage::getSenderId, peerId)) > 0;
        if (peerReplied) {
            return new SendState(true, null);
        }
        long mySent = conv == null ? 0 : messageMapper.selectCount(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getConversationId, conv.getId())
                .eq(PrivateMessage::getSenderId, me));
        if (mySent == 0) {
            return new SendState(true, "互相关注后才能连续发消息");
        }
        return new SendState(false, "对方回复后才能继续聊天");
    }

    private Conversation find(Long me, Long peerId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserAId, Math.min(me, peerId))
                .eq(Conversation::getUserBId, Math.max(me, peerId)));
    }

    private Conversation findOrCreate(Long me, Long peerId) {
        Conversation conv = find(me, peerId);
        if (conv != null) {
            return conv;
        }
        conv = new Conversation();
        conv.setUserAId(Math.min(me, peerId));
        conv.setUserBId(Math.max(me, peerId));
        conv.setALastReadId(0L);
        conv.setBLastReadId(0L);
        try {
            conversationMapper.insert(conv);
            return conv;
        } catch (DuplicateKeyException e) {
            return find(me, peerId);
        }
    }

    private Map<Long, Long> unreadMap(Long me, List<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : conversationMapper.countUnreadByConversation(me, conversationIds)) {
            Object cid = row.get("conversation_id");
            Object cnt = row.get("cnt");
            if (cid instanceof Number n1 && cnt instanceof Number n2) {
                result.put(n1.longValue(), n2.longValue());
            }
        }
        return result;
    }

    private static Long peerOf(Conversation conv, Long me) {
        return me.equals(conv.getUserAId()) ? conv.getUserBId() : conv.getUserAId();
    }

    private PeerVO toPeer(User user) {
        return PeerVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(toAvatarUrl(user.getAvatarUrl()))
                .build();
    }

    /** 推送用：某个用户的展示信息（头像地址已续期），用户不存在时返回 null */
    public PeerVO peerOf(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user == null ? null : toPeer(user);
    }

    private String toAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()
                || avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            return avatarUrl;
        }
        return storageService.publicUrl(avatarUrl);
    }

    /** 批量转 VO：一次性取齐卡片作品与作者昵称 */
    private List<PrivateMessageVO> toVOs(List<PrivateMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = messages.stream()
                .filter(message -> MessageType.POST_CARD.equals(typeOf(message)))
                .map(PrivateMessage::getPostId)
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, Post> posts = postIds.isEmpty() ? Map.of()
                : postMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        Set<Long> authorIds = posts.values().stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, String> authors = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        return messages.stream().map(message -> toVO(message, posts, authors)).toList();
    }

    private PrivateMessageVO toVO(PrivateMessage message, Map<Long, Post> posts, Map<Long, String> authors) {
        MessageType type = typeOf(message);
        return PrivateMessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .content(message.getContent())
                .type(type.name())
                .images(toImageUrls(message.getImagesObject()))
                .post(toCard(type, message.getPostId(), posts, authors))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private PostCardVO toCard(MessageType type, Long postId, Map<Long, Post> posts, Map<Long, String> authors) {
        if (!MessageType.POST_CARD.equals(type) || postId == null) {
            return null;
        }
        Post post = posts.get(postId);
        if (post == null) {
            return PostCardVO.builder().id(postId).available(false).build();
        }
        return PostCardVO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .type(post.getType() == null ? null : post.getType().name())
                .coverUrl(toCoverUrl(post))
                .authorId(post.getUserId())
                .authorNickname(authors.get(post.getUserId()))
                .available(true)
                .build();
    }

    private String toCoverUrl(Post post) {
        String object = post.getCoverObject();
        if (object == null || object.isBlank()) {
            List<String> images = post.getImagesObject();
            object = images == null || images.isEmpty() ? null : images.get(0);
        }
        if (object == null || object.isBlank()) {
            return null;
        }
        return storageService.publicUrl(object);
    }

    private List<String> toImageUrls(List<String> objects) {
        if (objects == null || objects.isEmpty()) {
            return List.of();
        }
        return objects.stream().map(storageService::publicUrl).toList();
    }

    private static MessageType typeOf(PrivateMessage message) {
        return message.getType() == null ? MessageType.TEXT : message.getType();
    }

    /** 会话列表摘要：文本 / [图片] / [作品] 标题 */
    private String summaryOf(PrivateMessage last, Map<Long, Post> posts) {
        if (last == null) {
            return null;
        }
        MessageType type = typeOf(last);
        return switch (type) {
            case IMAGE -> "[图片]";
            case POST_CARD -> {
                Post post = last.getPostId() == null ? null : posts.get(last.getPostId());
                String title = post == null || post.getTitle() == null ? "" : post.getTitle().trim();
                yield title.isEmpty() ? "[作品]" : "[作品] " + truncate(title, SUMMARY_TITLE_LENGTH);
            }
            default -> last.getContent();
        };
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static int normalize(int limit, int fallback, int max) {
        if (limit <= 0) {
            return fallback;
        }
        return Math.min(limit, max);
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        int idx = cursor.lastIndexOf('_');
        if (idx <= 0) {
            return null;
        }
        try {
            long millis = Long.parseLong(cursor.substring(0, idx));
            long id = Long.parseLong(cursor.substring(idx + 1));
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            return new Cursor(time, id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cursorOf(Conversation row) {
        long millis = row.getLastMessageAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return millis + "_" + row.getId();
    }
}
