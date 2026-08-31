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

    private final TranscodePublisher transcodePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(PostTranscodeRequestedEvent event) {
        log.info("post {} transcode message sent after commit", event.postId());
        transcodePublisher.send(event.postId());
    }
}
