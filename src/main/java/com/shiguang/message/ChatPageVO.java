package com.shiguang.message;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatPageVO {

    private PeerVO peer;

    /** 当前是否允许发送（非互关且对方未回复时可能被限制） */
    private Boolean canSend;

    /** 限制原因提示，可发送时为 null */
    private String hint;

    private List<PrivateMessageVO> items;

    private String nextCursor;

    private Boolean hasMore;
}
