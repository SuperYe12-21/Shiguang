package com.shiguang.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /** 我收到的私信未读总数 */
    @Select("""
            SELECT COUNT(*)
            FROM private_message m
            JOIN conversation c ON c.id = m.conversation_id
            WHERE m.receiver_id = #{userId}
              AND m.id > IF(c.user_a_id = #{userId}, c.a_last_read_id, c.b_last_read_id)
            """)
    long countUnread(@Param("userId") Long userId);

    /** 会话列表中每条的未读数：返回 conversation_id / cnt 两列 */
    @Select("""
            <script>
            SELECT m.conversation_id AS conversation_id, COUNT(*) AS cnt
            FROM private_message m
            JOIN conversation c ON c.id = m.conversation_id
            WHERE m.receiver_id = #{userId}
              AND m.id > IF(c.user_a_id = #{userId}, c.a_last_read_id, c.b_last_read_id)
              AND m.conversation_id IN
              <foreach collection="conversationIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
            GROUP BY m.conversation_id
            </script>
            """)
    List<Map<String, Object>> countUnreadByConversation(@Param("userId") Long userId,
                                                        @Param("conversationIds") Collection<Long> conversationIds);
}
