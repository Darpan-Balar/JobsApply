package com.jobsapply.platform.audit;

import com.jobsapply.platform.audit.internal.AuditEventRepository;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.ActorType;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current user's audit trail. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository audit;
    private final JsonCodec json;

    public record AuditView(UUID id, Instant occurredAt, ActorType actorType, String aggregateType, UUID aggregateId, String eventType,
            Map<String, Object> payload) {}

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<AuditView> mine(UUID aggregateId, int limit) {
        UUID me = UserContext.requireUserId();
        return audit.findForSubject(me, aggregateId, PageRequest.of(0, limit)).stream()
                .map(e -> new AuditView(e.getId(), e.getOccurredAt(), e.getActorType(), e.getAggregateType(), e.getAggregateId(),
                        e.getEventType(), json.read(e.getPayloadJson(), Map.class)))
                .toList();
    }
}
