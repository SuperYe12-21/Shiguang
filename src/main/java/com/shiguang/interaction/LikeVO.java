package com.shiguang.interaction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LikeVO {

    private Boolean liked;

    private Integer likeCount;
}