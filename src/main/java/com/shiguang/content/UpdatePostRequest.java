package com.shiguang.content;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePostRequest {

    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @Size(max = 500, message = "描述最长 500 字")
    private String description;
}
