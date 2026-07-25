package com.zephyr.croj.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.mapper.OutboxEventMapper;
import com.zephyr.croj.model.entity.OutboxEvent;
import com.zephyr.croj.model.entity.Submission;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatabaseSubmissionOutboxTest {

    @Test
    void createsAStableSubmissionRequestedEvent() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        when(mapper.insert(any(OutboxEvent.class))).thenReturn(1);
        DatabaseSubmissionOutbox outbox = new DatabaseSubmissionOutbox(mapper, new ObjectMapper());
        Submission submission = new Submission();
        submission.setId(99L);
        submission.setProblemId(42L);
        submission.setUserId(7L);
        submission.setLanguage("java17");

        outbox.enqueue(submission);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(mapper).insert(event.capture());
        assertEquals("SUBMISSION", event.getValue().getAggregateType());
        assertEquals(99L, event.getValue().getAggregateId());
        assertEquals("SubmissionRequested", event.getValue().getEventType());
        assertTrue(event.getValue().getPayload().contains("\"submissionId\":99"));
    }
}
