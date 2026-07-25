package com.zephyr.croj;

import org.junit.jupiter.api.Test;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:coderushoj;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.data.redis.password=test-only",
        "spring.mail.password=test-only",
        "jwt.secret=test-only-secret-with-at-least-32-bytes",
        "app.judge-result.service-token=judge-result-test-token-with-32-bytes",
        "app.upload.base-dir=target/test-uploads",
        "app.outbox.enabled=false",
})
class CrojApplicationTests {

    @MockitoBean
    private RocketMQTemplate rocketMQTemplate;

    @Test
    void contextLoads() {
    }

}
