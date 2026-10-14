package com.jobsapply.platform.shared.model;

import java.util.Objects;
import java.util.UUID;

/** Who performed an action: a specific user, or the system (scheduler, recovery). */
public record Actor(ActorType type, UUID userId) {

    public Actor {
        Objects.requireNonNull(type, "type");
        if (type == ActorType.USER) {
            Objects.requireNonNull(userId, "userId is required for a USER actor");
        } else if (userId != null) {
            throw new IllegalArgumentException("SYSTEM actor has no userId");
        }
    }

    public static Actor user(UUID userId) {
        return new Actor(ActorType.USER, userId);
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }

    public boolean isUser() {
        return type == ActorType.USER;
    }
}
