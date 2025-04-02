package com.zephyr.croj.config;

import com.google.code.kaptcha.text.TextProducer;
import com.google.code.kaptcha.util.Configurable;

import java.security.SecureRandom;

public class CustomTextProducer extends Configurable implements TextProducer {
    private static final String CHAR_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String getText() {
        int length = getConfig().getTextProducerCharLength();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(CHAR_STRING.length());
            sb.append(CHAR_STRING.charAt(randomIndex));
        }
        return sb.toString();
    }
}