package com.zephyr.croj.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    @NotBlank
    private String submissionTopic = "submission-topic";

    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @NotNull
    private Duration claimTimeout = Duration.ofSeconds(30);

    @NotNull
    private Duration publishTimeout = Duration.ofSeconds(5);

    @AssertTrue(message = "claim-timeout must be positive and at least twice publish-timeout")
    public boolean isLeaseConfigurationSafe() {
        if (claimTimeout == null || publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()) {
            return false;
        }
        return claimTimeout.compareTo(publishTimeout.multipliedBy(2)) >= 0;
    }
}
