package com.zephyr.croj.outbox;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zephyr.croj.config.properties.OutboxProperties;
import com.zephyr.croj.mapper.OutboxEventMapper;
import com.zephyr.croj.model.entity.OutboxEvent;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {

    @Test
    void claimsAndPublishesSubmissionIdsThenMarksEventsComplete() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RocketMQTemplate rocketMq = mock(RocketMQTemplate.class);
        OutboxProperties properties = new OutboxProperties();
        OutboxEvent event = new OutboxEvent();
        event.setId("event-1");
        event.setAggregateId(99L);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(mapper.claimNext(anyString(), eq(30L))).thenReturn(1, 0);
        when(mapper.findClaimed(anyString())).thenReturn(event);
        when(rocketMq.syncSend("submission-topic", "99", 5000L)).thenReturn(sendResult);

        new OutboxPublisher(mapper, rocketMq, properties).publishPending();

        verify(rocketMq).syncSend("submission-topic", "99", 5000L);
        verify(mapper).markPublished(eq("event-1"), anyString());
    }

    @Test
    void releasesFailedEventsWithABackoffInsteadOfLosingThem() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RocketMQTemplate rocketMq = mock(RocketMQTemplate.class);
        OutboxEvent event = new OutboxEvent();
        event.setId("event-2");
        event.setAggregateId(100L);
        event.setAttempts(2);
        when(mapper.claimNext(anyString(), eq(30L))).thenReturn(1, 0);
        when(mapper.findClaimed(anyString())).thenReturn(event);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rocketMq).syncSend("submission-topic", "100", 5000L);

        new OutboxPublisher(mapper, rocketMq, new OutboxProperties()).publishPending();

        verify(mapper).releaseAfterFailure(
                eq("event-2"), anyString(), eq(4L), eq("broker unavailable"));
    }

    @Test
    void nonOkBrokerStatusIsRetriedInsteadOfMarkedPublished() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RocketMQTemplate rocketMq = mock(RocketMQTemplate.class);
        OutboxEvent event = new OutboxEvent();
        event.setId("event-3");
        event.setAggregateId(101L);
        event.setAttempts(0);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.FLUSH_DISK_TIMEOUT);
        when(mapper.claimNext(anyString(), eq(30L))).thenReturn(1, 0);
        when(mapper.findClaimed(anyString())).thenReturn(event);
        when(rocketMq.syncSend("submission-topic", "101", 5000L)).thenReturn(sendResult);

        new OutboxPublisher(mapper, rocketMq, new OutboxProperties()).publishPending();

        verify(mapper).releaseAfterFailure(
                eq("event-3"), anyString(), eq(1L), eq("RocketMQ send status: FLUSH_DISK_TIMEOUT"));
        verify(mapper, never()).markPublished(eq("event-3"), anyString());
    }
}
