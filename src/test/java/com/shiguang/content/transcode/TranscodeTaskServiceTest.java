package com.shiguang.content.transcode;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostService;
import com.shiguang.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscodeTaskServiceTest {

    @Mock private TranscodeTaskMapper taskMapper;
    @Mock private PostMapper postMapper;
    @Mock private PostService postService;
    @Mock private TranscodePublisher publisher;
    @Mock private StorageService storageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private TranscodeTaskService tasks;

    @BeforeEach
    void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TranscodeTask.class);
    }

    @Test
    void staleExpirationSnapshotDoesNotStopRenewingLiveLease() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(taskMapper.claim(eq(42L), anyString(), any(), any(), anyInt())).thenReturn(1);
        TranscodeTask task = new TranscodeTask();
        task.setPostId(42L);
        task.setAttempts(1);
        task.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        when(taskMapper.selectById(42L)).thenReturn(task);
        TranscodeTaskService.Claim claim = tasks.claim(42L);
        task.setLeaseToken(claim.token());
        when(taskMapper.selectList(any())).thenReturn(List.of(task), List.of());
        when(taskMapper.finish(eq(42L), eq(claim.token()), anyString(), anyString(), any(), any(), eq(true)))
                .thenReturn(0);
        when(taskMapper.renew(eq(42L), eq(claim.token()), any(), any())).thenReturn(1);

        tasks.recover();
        tasks.heartbeat();

        verify(taskMapper).renew(eq(42L), eq(claim.token()), any(), any());
    }
}
