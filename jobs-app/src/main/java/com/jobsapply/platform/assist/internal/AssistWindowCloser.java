package com.jobsapply.platform.assist.internal;

import com.jobsapply.platform.application.ApplicationEvents;
import com.jobsapply.platform.assist.AssistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Once an attempt has ended (however it was reported), its assistance window has no purpose: close it. */
@Component
@RequiredArgsConstructor
class AssistWindowCloser {

    private final AssistService assist;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ApplicationEvents.AttemptFinished event) {
        assist.closeFor(event.applicationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ApplicationEvents.AttemptVerified event) {
        assist.closeFor(event.applicationId());
    }
}
