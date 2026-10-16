package com.shiguang.content.transcode;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class TranscodePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TranscodeProperties props;

    public void send(Long postId) {
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.convertAndSend(props.exchange(), props.routingKey(), new TranscodeJob(postId), correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("转码消息未确认路由到队列");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("转码消息确认被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("转码消息确认失败", exception);
        }
    }
}
