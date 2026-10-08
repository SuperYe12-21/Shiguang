package com.shiguang.message;

import lombok.Data;

import java.util.List;

@Data
public class SendMessageRequest {

    private Long toUserId;

    private String content;

    /** TEXT / IMAGE / POST_CARD，缺省为 TEXT */
    private String type;

    /** 图片对象名，最多 9 张 */
    private List<String> imageUrls;

    /** 作品卡片指向的作品 */
    private Long postId;
}
