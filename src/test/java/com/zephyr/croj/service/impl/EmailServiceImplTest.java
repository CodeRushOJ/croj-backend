package com.zephyr.croj.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

class EmailServiceImplTest {

    @Test
    void returnsFailureWhenTheMailTransportFails() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        when(templateEngine.process(eq("email/verification-code"), any(Context.class)))
                .thenReturn("<p>verification code</p>");
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(message);

        EmailServiceImpl service = new EmailServiceImpl(mailSender, templateEngine);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@coderushoj.local");

        assertThat(service.sendVerificationCode("user@example.com", "user", "123456"))
                .isFalse();
        verify(mailSender).send(message);
    }
}
