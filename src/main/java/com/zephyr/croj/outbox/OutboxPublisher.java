package com.zephyr.croj.outbox;

import com.zephyr.croj.config.properties.OutboxProperties;
import com.zephyr.croj.mapper.OutboxEventMapper;
import com.zephyr.croj.model.entity.OutboxEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", matchIfMissing = true)
@Slf4j
public class OutboxPublisher {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventMapper mapper;
    private final RocketMQTemplate rocketMq;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT1S}")
    public void publishPending() {
        for (int index = 0; index < properties.getBatchSize(); index++) {
            String claimId = UUID.randomUUID().toString();
            if (mapper.claimNext(claimId, properties.getClaimTimeout().toSeconds()) == 0) {
                return;
            }
            OutboxEvent event = mapper.findClaimed(claimId);
            if (event == null) {
                continue;
            }
            try {
                SendResult result = rocketMq.syncSend(
                        properties.getSubmissionTopic(),
                        String.valueOf(event.getAggregateId()),
                        properties.getPublishTimeout().toMillis());
                if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                    throw new IllegalStateException("RocketMQ send status: "
                            + (result == null ? "UNKNOWN" : result.getSendStatus()));
                }
                mapper.markPublished(event.getId(), claimId);
            } catch (RuntimeException exception) {
                int attempts = event.getAttempts() == null ? 0 : event.getAttempts();
                long delaySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
                String error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                mapper.releaseAfterFailure(
                        event.getId(),
                        claimId,
                        delaySeconds,
                        error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH)));
                log.warn("Outbox event {} publication failed; retry scheduled", event.getId(), exception);
            }
        }
    }
}
