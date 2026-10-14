package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserStatus;
import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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

    @Column(name = "username", nullable = false, updatable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    public void disable() {
        this.status = UserStatus.DISABLED;
    }
}
