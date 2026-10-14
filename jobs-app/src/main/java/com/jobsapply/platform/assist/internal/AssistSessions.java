package com.jobsapply.platform.assist.internal;

import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Open assistance windows, at most one per user (in memory: after a restart there are no windows, and recovery marks
 * the interrupted attempts unconfirmed).
 */
@Component
public class AssistSessions {

    private final Map<UUID, LiveSession> byOwner = new ConcurrentHashMap<>();

    /** The user's session for this application, created if they have none; refused while another application has one. */
    public LiveSession reserve(UUID owner, UUID applicationId) {
        LiveSession live = byOwner.computeIfAbsent(owner, o -> new LiveSession(o, applicationId));
        if (!live.applicationId().equals(applicationId)) {
            throw new DomainRuleViolationException("Browser assistance is already open for another of your applications; "
                    + "finish or close it first (one application at a time)");
        }
        return live;
    }

    public Optional<LiveSession> find(UUID owner, UUID applicationId) {
        return Optional.ofNullable(byOwner.get(owner)).filter(s -> s.applicationId().equals(applicationId));
    }

    public Optional<LiveSession> findByApplication(UUID applicationId) {
        return byOwner.values().stream().filter(s -> s.applicationId().equals(applicationId)).findFirst();
    }

    public void release(LiveSession live) {
        byOwner.remove(live.owner(), live);
    }

    public int open() {
        return byOwner.size();
    }
}
