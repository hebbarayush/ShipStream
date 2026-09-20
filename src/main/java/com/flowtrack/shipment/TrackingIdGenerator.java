package com.flowtrack.shipment;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates ids like FT-7K2M9QX4TB. Alphabet omits look-alike characters (0/O, 1/I). */
@Component
class TrackingIdGenerator {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    String next() {
        StringBuilder sb = new StringBuilder("FT-");
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
