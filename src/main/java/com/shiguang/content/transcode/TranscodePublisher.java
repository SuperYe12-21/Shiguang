package com.shiguang.content.transcode;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranscodePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TranscodeProperties props;

    public void send(Long postId) {
        rabbitTemplate.convertAndSend(props.exchange(), props.routingKey(), new TranscodeJob(postId));
    }
}
