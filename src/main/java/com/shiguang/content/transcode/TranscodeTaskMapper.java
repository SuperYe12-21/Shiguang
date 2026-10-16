package com.shiguang.content.transcode;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TranscodeTaskMapper extends BaseMapper<TranscodeTask> {

    @Insert("INSERT IGNORE INTO transcode_task (post_id) VALUES (#{postId})")
    int register(@Param("postId") Long postId);

    @Update("""
            UPDATE transcode_task SET state = 'DISPATCHED', lease_token = #{token},
              next_attempt_at = #{next}, updated_at = #{now}
            WHERE post_id = #{postId} AND state IN ('PENDING', 'DISPATCHED') AND next_attempt_at <= #{now}
            """)
    int prepareDispatch(@Param("postId") Long postId, @Param("token") String token,
                        @Param("now") LocalDateTime now, @Param("next") LocalDateTime next);

    @Update("""
            UPDATE transcode_task SET state = 'PENDING', last_error = #{error},
              next_attempt_at = #{next}, updated_at = #{now}
            WHERE post_id = #{postId} AND state = 'DISPATCHED' AND lease_token = #{token}
            """)
    int dispatchFailed(@Param("postId") Long postId, @Param("token") String token,
                       @Param("error") String error, @Param("now") LocalDateTime now,
                       @Param("next") LocalDateTime next);

    @Update("""
            UPDATE transcode_task SET state = 'RUNNING', attempts = attempts + 1,
              lease_token = #{token}, lease_until = #{until}, updated_at = #{now}
            WHERE post_id = #{postId} AND attempts < #{maxAttempts}
              AND (state = 'DISPATCHED' OR (state = 'PENDING' AND next_attempt_at <= #{now}))
            """)
    int claim(@Param("postId") Long postId, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("until") LocalDateTime until,
              @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE transcode_task SET lease_until = #{until}, updated_at = #{now}
            WHERE post_id = #{postId} AND state = 'RUNNING' AND lease_token = #{token}
            """)
    int renew(@Param("postId") Long postId, @Param("token") String token,
              @Param("until") LocalDateTime until, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transcode_task SET state = #{state}, last_error = #{error},
              next_attempt_at = #{next}, lease_until = NULL, updated_at = #{now}
            WHERE post_id = #{postId} AND state = 'RUNNING' AND lease_token = #{token}
              AND (#{expiredOnly} = FALSE OR lease_until < #{now})
            """)
    int finish(@Param("postId") Long postId, @Param("token") String token,
               @Param("state") String state, @Param("error") String error,
               @Param("next") LocalDateTime next, @Param("now") LocalDateTime now,
               @Param("expiredOnly") boolean expiredOnly);

    @Update("""
            UPDATE transcode_task SET state = 'COMPLETED', lease_until = NULL, updated_at = #{now}
            WHERE post_id = #{postId} AND state NOT IN ('COMPLETED', 'FAILED')
            """)
    int cancel(@Param("postId") Long postId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transcode_task SET state = 'PENDING', attempts = 0, last_error = '',
              lease_token = '', lease_until = NULL, next_attempt_at = #{now}, updated_at = #{now}
            WHERE post_id = #{postId} AND state = 'FAILED'
            """)
    int resetFailed(@Param("postId") Long postId, @Param("now") LocalDateTime now);
}
