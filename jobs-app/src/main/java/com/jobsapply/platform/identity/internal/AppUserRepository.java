package com.jobsapply.platform.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByPasswordHashIsNotNull();

    List<AppUser> findAllByOrderByUsernameAsc();
}
