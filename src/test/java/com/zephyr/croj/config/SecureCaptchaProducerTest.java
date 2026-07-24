package com.zephyr.croj.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SecureCaptchaProducerTest {

    @Test
    void createsFourCharacterCryptographicChallengesAndRenderableImages() throws Exception {
        SecureCaptchaProducer producer = new SecureCaptchaProducer();
        Set<String> challenges = new HashSet<>();
        for (int index = 0; index < 32; index++) {
            String challenge = producer.createText();
            assertTrue(challenge.matches("[ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789]{4}"));
            challenges.add(challenge);
        }
        assertTrue(challenges.size() > 1);

        BufferedImage image = producer.createImage(challenges.iterator().next());
        assertEquals(110, image.getWidth());
        assertEquals(40, image.getHeight());
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpg", encoded));
        assertTrue(encoded.size() > 0);
    }
}
