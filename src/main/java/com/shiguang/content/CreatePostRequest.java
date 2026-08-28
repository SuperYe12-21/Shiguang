package com.shiguang.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @NotBlank(message = "作品类型不能为空")
    private String type;

    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @Size(max = 500, message = "描述最长 500 字")
    private String description;

    @Size(max = 500, message = "视频对象名非法")
    private String videoObject;

    @Size(max = 500, message = "封面对象名非法")
    private String coverObject;

    @Size(max = 18, message = "最多 18 张图片")
    private List<@Size(max = 500, message = "图片对象名非法") String> images;
}