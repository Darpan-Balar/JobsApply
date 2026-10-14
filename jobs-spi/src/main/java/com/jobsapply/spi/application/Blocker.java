package com.jobsapply.spi.application;

/** Conditions that require the human to take over. Never bypassed. */
public enum Blocker {
    CAPTCHA,
    MFA,
    LOGIN_REQUIRED,
    ACCESS_DENIED,
    UNRECOGNIZED_FORM
}
