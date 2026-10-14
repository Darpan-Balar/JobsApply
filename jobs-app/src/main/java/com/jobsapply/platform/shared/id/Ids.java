package com.jobsapply.platform.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates RFC 9562 version-7 UUIDs: 48-bit Unix-millisecond timestamp followed by random bits. Time-ordered, so
 * primary-key indexes stay compact; generated in the application so entities have an id before persisting.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        return uuidV7(System.currentTimeMillis());
    }

    static UUID uuidV7(long epochMillis) {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L; // version 7
        msb |= ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL); // rand_a (12 bits)

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (random[i] & 0xFFL);
        }
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // IETF variant (10xx)
        return new UUID(msb, lsb);
    }

    /** Milliseconds since the epoch encoded in a version-7 UUID. */
    public static long timestampMillis(UUID uuidV7) {
        if (uuidV7.version() != 7) {
            throw new IllegalArgumentException("Not a version-7 UUID: " + uuidV7);
        }
        return uuidV7.getMostSignificantBits() >>> 16;
    }
}
