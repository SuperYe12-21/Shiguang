package com.shiguang.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlockPostRequest {

    /** 下架原因，作者可见；留空则只显示"已下架" */
    @Size(max = 200, message = "下架原因最多 200 字")
    private String reason;
}
