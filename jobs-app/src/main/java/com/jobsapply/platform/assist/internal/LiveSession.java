package com.jobsapply.platform.assist.internal;

import com.jobsapply.platform.assist.AssistView;
import com.jobsapply.platform.shared.error.ConcurrencyConflictException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** One user's assistance for one application. Actions on it are serialized by its lock. */
public final class LiveSession {

    private final UUID owner;
    private final UUID applicationId;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile UUID attemptId;
    private volatile PlatformSession window;
    private volatile AssistView last;

    LiveSession(UUID owner, UUID applicationId) {
        this.owner = owner;
        this.applicationId = applicationId;
    }

    public UUID owner() {
        return owner;
    }

    public UUID applicationId() {
        return applicationId;
    }

    public UUID attemptId() {
        return attemptId;
    }

    public PlatformSession window() {
        return window;
    }

    public AssistView last() {
        return last;
    }

    public void opened(UUID attempt, PlatformSession session) {
        this.attemptId = attempt;
        this.window = session;
    }

    public AssistView remember(AssistView view) {
        this.last = view;
        return view;
    }

    public void lock(Duration wait) {
        try {
            if (!lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ConcurrencyConflictException("Another assistance action for this application is still running; try again shortly");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrencyConflictException("Interrupted while waiting for the assistance window");
        }
    }

    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
