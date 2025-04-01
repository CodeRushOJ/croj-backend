package com.zephyr.croj.service.impl;

import com.zephyr.croj.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 * 邮件服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public boolean sendVerificationCode(String to, String username, String code) {
        String subject = "CodeRush OJ - 邮箱验证码";
        String template = "email/verification-code";

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("code", code);

        return sendEmail(to, subject, template, context);
    }

    @Override
    public boolean sendVerificationLink(String to, String username, Long userId, String code) {
        String subject = "CodeRush OJ - 验证您的邮箱";
        String template = "email/verification-link";

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("verifyUrl", frontendUrl + "/verify-email?userId=" + userId + "&code=" + code);

        return sendEmail(to, subject, template, context);
    }

    @Override
    public boolean sendResetPasswordEmail(String to, String username, String code) {
        String subject = "CodeRush OJ - 重置密码";
        String template = "email/reset-password";

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("code", code);
        context.setVariable("resetUrl", frontendUrl + "/reset-password?code=" + code);

        return sendEmail(to, subject, template, context);
    }

    /**
     * 发送邮件的通用方法
     */
    private boolean sendEmail(String to, String subject, String template, Context context) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            String content = templateEngine.process(template, context);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("邮件发送成功: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("邮件发送失败: {}", e.getMessage());
            return false;
        }
    }
}