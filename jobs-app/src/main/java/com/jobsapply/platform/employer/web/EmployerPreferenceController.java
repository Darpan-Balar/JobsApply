package com.jobsapply.platform.employer.web;

import com.jobsapply.platform.employer.EmployerPreferenceService;
import com.jobsapply.platform.employer.EmployerPreferenceView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My employer preferences", description = "The current user's private watch / ignore list")
@RestController
@RequestMapping("/api/v1/me/employer-preferences")
@RequiredArgsConstructor
class EmployerPreferenceController {

    private final EmployerPreferenceService preferences;

    @GetMapping
    List<EmployerPreferenceView> list() {
        return preferences.list();
    }

    @PutMapping("/{employerKey}")
    EmployerPreferenceView put(@PathVariable String employerKey, @Valid @RequestBody EmployerRequests.PutPreference body) {
        return preferences.set(employerKey, body.mode(), body.priority() == null ? 50 : body.priority(), body.note());
    }

    @DeleteMapping("/{employerKey}")
    ResponseEntity<Void> remove(@PathVariable String employerKey) {
        preferences.remove(employerKey);
        return ResponseEntity.noContent().build();
    }
}
