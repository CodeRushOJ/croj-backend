package com.zephyr.croj.outbox;

import com.zephyr.croj.model.entity.Submission;

public interface SubmissionOutbox {

    void enqueue(Submission submission);
}
