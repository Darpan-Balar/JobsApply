package com.jobsapply.platform.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context against Testcontainers PostgreSQL (requires Docker). Tests must not be
 * {@code @Transactional} at class level when they exercise user scoping: the user context has to be bound before a
 * transaction opens (see {@code UserContext}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestPluginsConfiguration.class})
@ActiveProfiles("test")
public @interface IntegrationTest {}
