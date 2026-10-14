package com.jobsapply.platform.employer;

import com.jobsapply.platform.employer.internal.Employer;
import com.jobsapply.platform.employer.internal.EmployerRepository;
import com.jobsapply.platform.employer.internal.UserEmployerPreference;
import com.jobsapply.platform.employer.internal.UserEmployerPreferenceRepository;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** The current user's private employer preferences. */
@Service
@Validated
@RequiredArgsConstructor
public class EmployerPreferenceService {

    private final UserEmployerPreferenceRepository preferences;
    private final EmployerRepository employers;

    @Transactional
    public EmployerPreferenceView set(
            String employerKey, @NotNull PreferenceMode mode, @Min(0) @Max(100) int priority, @Size(max = 1000) String note) {
        UserContext.requireUserId();
        Employer employer = employers.findByKey(employerKey).orElseThrow(() -> new NotFoundException("Employer", employerKey));
        UserEmployerPreference preference = preferences.findByEmployerId(employer.getId())
                .map(existing -> {
                    existing.change(mode, priority, note);
                    return existing;
                })
                .orElseGet(() -> preferences.save(UserEmployerPreference.builder()
                        .employerId(employer.getId()).mode(mode).priority((short) priority).note(note).build()));
        return view(preference, employer);
    }

    @Transactional
    public void remove(String employerKey) {
        UserContext.requireUserId();
        employers.findByKey(employerKey)
                .flatMap(employer -> preferences.findByEmployerId(employer.getId()))
                .ifPresent(preferences::delete);
    }

    @Transactional(readOnly = true)
    public List<EmployerPreferenceView> list() {
        UserContext.requireUserId();
        List<UserEmployerPreference> all = preferences.findAllByOrderByPriorityDesc();
        Map<UUID, Employer> byId = employers.findAllById(all.stream().map(UserEmployerPreference::getEmployerId).toList())
                .stream().collect(Collectors.toMap(Employer::getId, Function.identity()));
        return all.stream().map(p -> view(p, byId.get(p.getEmployerId()))).toList();
    }

    private static EmployerPreferenceView view(UserEmployerPreference p, Employer employer) {
        return new EmployerPreferenceView(employer.getKey(), employer.getDisplayName(), p.getMode(), p.getPriority(), p.getNote());
    }
}
