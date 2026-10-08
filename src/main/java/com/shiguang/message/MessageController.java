package com.shiguang.message;

import com.shiguang.common.PageVO;
import com.shiguang.common.R;
import com.shiguang.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    public R<PageVO<ConversationVO>> conversations(@RequestParam(required = false) String cursor,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return R.ok(messageService.conversations(SecurityUtils.getUserId(), cursor, limit));
    }

    /** 聊天页数据：对方信息 + 能否发送 + 历史消息 */
    @GetMapping("/conversations/{peerId}/messages")
    public R<ChatPageVO> chat(@PathVariable Long peerId,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "30") int limit) {
        return R.ok(messageService.chat(SecurityUtils.getUserId(), peerId, cursor, limit));
    }

    @PostMapping("/messages")
    public R<PrivateMessageVO> send(@RequestBody SendMessageRequest request) {
        return R.ok(messageService.send(SecurityUtils.getUserId(), request.getToUserId(), request.getType(),
                request.getContent(), request.getImageUrls(), request.getPostId()));
    }

    @PostMapping("/conversations/{peerId}/read")
    public R<UnreadVO> markRead(@PathVariable Long peerId) {
        return R.ok(new UnreadVO(messageService.markRead(SecurityUtils.getUserId(), peerId)));
    }

    @GetMapping("/messages/unread")
    public R<UnreadVO> unread() {
        return R.ok(new UnreadVO(messageService.unreadCount(SecurityUtils.getUserId())));
    }
}
