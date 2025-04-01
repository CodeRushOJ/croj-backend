package com.zephyr.croj.controller;

import com.google.code.kaptcha.Producer;
import com.zephyr.croj.common.constants.CaptchaConstants;
import com.zephyr.croj.utils.RedisCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/captcha")
@Tag(name = "验证码管理", description = "验证码相关接口")
@Slf4j
@RequiredArgsConstructor
public class CaptchaController {

    private final Producer captchaProducer;
    private final RedisCache redisCache;

    @GetMapping
    @Operation(summary = "获取验证码")
    public void getCaptcha(HttpServletResponse response) throws IOException {
        // 设置响应类型
        response.setContentType("image/jpeg");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        // 生成验证码文本
        String capText = captchaProducer.createText();
        log.debug("生成验证码：{}", capText);

        // 生成唯一key
        String captchaKey = UUID.randomUUID().toString();

        // 将验证码存入Redis缓存
        redisCache.setCacheObject(
                CaptchaConstants.CAPTCHA_CODE_KEY + ":" + captchaKey,
                capText,
                CaptchaConstants.CAPTCHA_EXPIRATION,
                TimeUnit.MINUTES
        );

        // 设置验证码key到响应头并允许前端访问
        response.setHeader("Captcha-Key", captchaKey);
        response.setHeader("Access-Control-Expose-Headers", "Captcha-Key");

        // 创建验证码图片
        BufferedImage image = captchaProducer.createImage(capText);

        // 输出到响应流
        try (ServletOutputStream out = response.getOutputStream()) {
            ImageIO.write(image, "jpg", out);
            out.flush();
        }
    }
}