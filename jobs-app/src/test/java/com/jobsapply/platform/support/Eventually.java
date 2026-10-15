package com.jobsapply.platform.support;

import java.time.Duration;
import java.time.Instant;

/** Retries an assertion until it passes or the timeout expires (for work done asynchronously after commit). */
public final class Eventually {

    private Eventually() {}

    public static void eventually(Runnable assertion) {
        eventually(Duration.ofSeconds(10), assertion);
    }

    public static void eventually(Duration timeout, Runnable assertion) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                if (Instant.now().isAfter(deadline)) {
                    throw e;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
