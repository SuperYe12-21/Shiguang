package com.shiguang.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPrivacyVO {

    private String like;

    private String favorite;

    private String follower;

    private String following;

    /** 他人主页用：四项列表当前访客能否查看 */
    @Data
    @Builder
    public static class Access {

        private Boolean like;

        private Boolean favorite;

        private Boolean follower;

        private Boolean following;
    }
}
