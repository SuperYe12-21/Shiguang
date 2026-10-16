package com.shiguang.content.transcode;

import com.shiguang.content.PostTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostTranscodeEventListener {

    private final TranscodeTaskService taskService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void register(PostTranscodeRequestedEvent event) {
        taskService.register(event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(PostTranscodeRequestedEvent event) {
        try {
            taskService.dispatch(event.postId());
        } catch (Exception exception) {
            log.warn("作品 {} 已提交，转码投递等待恢复: {}", event.postId(), exception.getMessage());
        }
    }
}
