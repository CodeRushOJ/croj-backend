package com.zephyr.croj.outbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zephyr.croj.config.properties.OutboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

    @Test
    void claimLeaseMustOutliveTheSynchronousPublishTimeout() {
        OutboxProperties properties = new OutboxProperties();
        assertTrue(properties.isLeaseConfigurationSafe());

        properties.setClaimTimeout(Duration.ofSeconds(5));
        properties.setPublishTimeout(Duration.ofSeconds(5));
        assertFalse(properties.isLeaseConfigurationSafe());

        properties.setPublishTimeout(Duration.ZERO);
        assertFalse(properties.isLeaseConfigurationSafe());
    }
}
