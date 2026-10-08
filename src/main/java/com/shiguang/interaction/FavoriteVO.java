package com.shiguang.interaction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteVO {

    private Boolean favorited;

    private Long favoriteCount;
}
