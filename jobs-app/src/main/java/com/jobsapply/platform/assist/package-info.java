/**
 * Browser assistance (ADR-0012): opens the approved application's form in a window the candidate sees, fills only
 * HIGH-confidence values from the approved package, pauses on CAPTCHA, MFA and logins, and records the confirmation
 * after the candidate presses Submit. One window per user at a time; nothing moves on to another application.
 */
package com.jobsapply.platform.assist;
