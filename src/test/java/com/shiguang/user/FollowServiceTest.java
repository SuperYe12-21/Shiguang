package com.shiguang.user;

import com.shiguang.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FollowService followService;

    private final User target = new User();

    @Test
    void followSelf_throws() {
        assertThatThrownBy(() -> followService.follow(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能关注自己");
    }

    @Test
    void followMissingUser_throws404() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> followService.follow(1L, 99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void follow_insertsAndReturnsState() {
        target.setId(9L);
        when(userMapper.selectById(9L)).thenReturn(target);
        when(followMapper.selectCount(any())).thenReturn(1L, 3L);

        FollowVO vo = followService.follow(1L, 9L);

        assertThat(vo.getFollowing()).isTrue();
        assertThat(vo.getFollowerCount()).isEqualTo(3L);
        verify(followMapper).insert(any(Follow.class));
    }

    @Test
    void duplicateFollow_isIdempotent() {
        target.setId(9L);
        when(userMapper.selectById(9L)).thenReturn(target);
        when(followMapper.insert(any(Follow.class))).thenThrow(new DuplicateKeyException("dup"));
        when(followMapper.selectCount(any())).thenReturn(1L, 3L);

        FollowVO vo = followService.follow(1L, 9L);

        assertThat(vo.getFollowing()).isTrue();
        assertThat(vo.getFollowerCount()).isEqualTo(3L);
    }

    @Test
    void unfollow_deletesRelationshipAndReturnsState() {
        target.setId(9L);
        when(userMapper.selectById(9L)).thenReturn(target);
        when(followMapper.selectCount(any())).thenReturn(0L, 2L);

        FollowVO vo = followService.unfollow(1L, 9L);

        assertThat(vo.getFollowing()).isFalse();
        assertThat(vo.getFollowerCount()).isEqualTo(2L);
        verify(followMapper).delete(any());
    }
}