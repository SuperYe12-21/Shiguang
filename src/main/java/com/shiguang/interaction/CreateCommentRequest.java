package com.shiguang.interaction;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateCommentRequest {

    @Size(max = 1000, message = "评论最长 1000 字")
    private String content;

    /** 图片对象名，最多 3 张；与文字至少填一个 */
    @Size(max = 3, message = "评论最多 3 张图片")
    private List<@Size(max = 200, message = "图片对象名非法") String> images;
}
