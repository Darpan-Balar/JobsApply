package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.identity.UserStatus;
import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AppUser extends BaseEntity {

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role;

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void setPassword(String hash, Instant now) {
        this.passwordHash = hash;
        this.passwordChangedAt = now;
    }

    /** First-account setup takes over the pre-login single-user account (its data stays with it). */
    public void claim(String newUsername, String newDisplayName, UserRole newRole) {
        this.username = newUsername;
        this.displayName = newDisplayName;
        this.role = newRole;
    }
}
