package com.zephyr.croj.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class));

    @Test
    void mailpitUsesPlainSmtpWithoutAuthenticationOrTls() {
        contextRunner
                .withSystemProperties(
                        "SMTP_HOST=mailpit",
                        "SMTP_PORT=1025",
                        "SMTP_USERNAME=noreply@coderushoj.local",
                        "SMTP_PASSWORD=",
                        "SMTP_AUTH=false",
                        "SMTP_STARTTLS=false",
                        "SMTP_SSL=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("mailpit");
                    assertThat(sender.getPort()).isEqualTo(1025);
                    assertThat(sender.getUsername()).isEqualTo("noreply@coderushoj.local");
                    assertThat(sender.getPassword()).isEmpty();
                    assertTransportProperties(sender.getJavaMailProperties(), false, false, false);
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.connectiontimeout", "3000")
                            .containsEntry("mail.smtp.timeout", "5000")
                            .containsEntry("mail.smtp.writetimeout", "5000");
                });
    }

    @Test
    void productionSmtpCanUseAuthenticatedStartTls() {
        contextRunner
                .withSystemProperties(
                        "SMTP_HOST=smtp.example.com",
                        "SMTP_PORT=587",
                        "SMTP_USERNAME=coderush",
                        "SMTP_PASSWORD=secret",
                        "SMTP_AUTH=true",
                        "SMTP_STARTTLS=true",
                        "SMTP_SSL=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("smtp.example.com");
                    assertThat(sender.getPort()).isEqualTo(587);
                    assertThat(sender.getUsername()).isEqualTo("coderush");
                    assertThat(sender.getPassword()).isEqualTo("secret");
                    assertTransportProperties(sender.getJavaMailProperties(), true, true, false);
                });
    }

    @Test
    void productionSmtpCanUseAuthenticatedImplicitTls() {
        contextRunner
                .withSystemProperties(
                        "SMTP_HOST=smtp.example.com",
                        "SMTP_PORT=465",
                        "SMTP_USERNAME=coderush",
                        "SMTP_PASSWORD=secret",
                        "SMTP_AUTH=true",
                        "SMTP_STARTTLS=false",
                        "SMTP_SSL=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getPort()).isEqualTo(465);
                    assertTransportProperties(sender.getJavaMailProperties(), true, false, true);
                });
    }

    private static void assertTransportProperties(
            Properties properties, boolean auth, boolean startTls, boolean ssl) {
        assertThat(properties)
                .containsEntry("mail.smtp.auth", Boolean.toString(auth))
                .containsEntry("mail.smtp.starttls.enable", Boolean.toString(startTls))
                .containsEntry("mail.smtp.starttls.required", Boolean.toString(startTls))
                .containsEntry("mail.smtp.ssl.enable", Boolean.toString(ssl))
                .doesNotContainKeys("mail.smtp.socketFactory.port", "mail.smtp.socketFactory.class");
    }
}
