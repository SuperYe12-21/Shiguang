package com.shiguang.content.transcode;

import com.shiguang.content.PostTranscodeRequestedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PostTranscodeEventListenerTest {

    @Test
    void brokerFailureDoesNotTurnCommittedPostIntoFailedHttpRequest() {
        TranscodeTaskService tasks = mock(TranscodeTaskService.class);
        doThrow(new IllegalStateException("broker unavailable")).when(tasks).dispatch(31L);
        PostTranscodeEventListener listener = new PostTranscodeEventListener(tasks);

        assertDoesNotThrow(() -> listener.onRequested(new PostTranscodeRequestedEvent(31L)));
    }
}
