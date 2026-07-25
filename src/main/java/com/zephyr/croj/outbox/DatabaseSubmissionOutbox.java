package com.zephyr.croj.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.mapper.OutboxEventMapper;
import com.zephyr.croj.model.entity.OutboxEvent;
import com.zephyr.croj.model.entity.Submission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseSubmissionOutbox implements SubmissionOutbox {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public void enqueue(Submission submission) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setAggregateType("SUBMISSION");
        event.setAggregateId(submission.getId());
        event.setEventType("SubmissionRequested");
        event.setPayload(serialize(event.getId(), submission));
        event.setAttempts(0);
        if (mapper.insert(event) != 1) {
            throw new IllegalStateException("Failed to persist submission outbox event");
        }
    }

    private String serialize(String eventId, Submission submission) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("eventId", eventId);
        payload.put("submissionId", submission.getId());
        payload.put("attemptNo", 1);
        payload.put("problemId", submission.getProblemId());
        payload.put("userId", submission.getUserId());
        payload.put("language", submission.getLanguage());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize submission outbox event", exception);
        }
    }
}
