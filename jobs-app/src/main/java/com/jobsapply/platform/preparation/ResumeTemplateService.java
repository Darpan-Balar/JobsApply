package com.jobsapply.platform.preparation;

import com.jobsapply.platform.preparation.internal.ResumeTemplate;
import com.jobsapply.platform.preparation.internal.ResumeTemplateRepository;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.UpsertOutcome;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Shared resume templates, imported from configuration. */
@Service
@Validated
@RequiredArgsConstructor
public class ResumeTemplateService {

    private final ResumeTemplateRepository templates;
    private final JsonCodec json;

    @Transactional
    public UpsertOutcome upsertSystem(@Valid ResumeTemplateSpec spec) {
        String definition = json.write(spec);
        String hash = JsonCodec.sha256Hex(definition);
        Optional<ResumeTemplate> existing = templates.findSystem(spec.key());
        if (existing.isEmpty()) {
            templates.save(ResumeTemplate.builder().key(spec.key()).displayName(spec.displayName())
                    .definitionJson(definition).contentHash(hash).build());
            return UpsertOutcome.CREATED;
        }
        return existing.get().replace(spec.displayName(), definition, hash) ? UpsertOutcome.UPDATED : UpsertOutcome.UNCHANGED;
    }

    @Transactional(readOnly = true)
    public Optional<ResumeTemplateSpec> find(String key) {
        return templates.findSystem(key).map(t -> json.read(t.getDefinitionJson(), ResumeTemplateSpec.class));
    }

    @Transactional(readOnly = true)
    public Set<String> keys() {
        return Set.copyOf(templates.systemKeys());
    }
}
