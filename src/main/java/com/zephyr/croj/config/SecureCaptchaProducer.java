package com.zephyr.croj.config;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Generates short-lived captcha challenges without the unmaintained kaptcha dependency.
 */
@Component
public final class SecureCaptchaProducer {

    static final int WIDTH = 110;
    static final int HEIGHT = 40;
    private static final int CHALLENGE_LENGTH = 4;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private final SecureRandom secureRandom = new SecureRandom();

    public String createText() {
        StringBuilder challenge = new StringBuilder(CHALLENGE_LENGTH);
        for (int index = 0; index < CHALLENGE_LENGTH; index++) {
            challenge.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return challenge.toString();
    }

    public BufferedImage createImage(String challenge) {
        Assert.isTrue(
                challenge != null
                        && challenge.length() == CHALLENGE_LENGTH
                        && challenge.chars().allMatch(character -> ALPHABET.indexOf(character) >= 0),
                "captcha challenge must contain four allowed characters"
        );
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setStroke(new BasicStroke(1.2f));
            for (int index = 0; index < 6; index++) {
                graphics.setColor(randomMutedColor());
                graphics.drawLine(
                        secureRandom.nextInt(WIDTH),
                        secureRandom.nextInt(HEIGHT),
                        secureRandom.nextInt(WIDTH),
                        secureRandom.nextInt(HEIGHT)
                );
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int index = 0; index < challenge.length(); index++) {
                graphics.setColor(randomTextColor());
                int x = 8 + index * 25 + secureRandom.nextInt(3);
                int y = 30 + secureRandom.nextInt(5) - 2;
                graphics.drawString(String.valueOf(challenge.charAt(index)), x, y);
            }
            graphics.setColor(new Color(105, 179, 90));
            graphics.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private Color randomMutedColor() {
        return new Color(
                120 + secureRandom.nextInt(100),
                120 + secureRandom.nextInt(100),
                120 + secureRandom.nextInt(100)
        );
    }

    private Color randomTextColor() {
        return new Color(
                secureRandom.nextInt(90),
                secureRandom.nextInt(90),
                90 + secureRandom.nextInt(100)
        );
    }
}
