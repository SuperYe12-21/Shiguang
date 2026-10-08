package com.shiguang.user;

import lombok.Data;

@Data
public class UpdatePrivacyRequest {

    private String like;

    private String favorite;

    private String follower;

    private String following;
}
