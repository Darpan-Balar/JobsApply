package com.jobsapply.platform.tracking.web;

import com.jobsapply.platform.tracking.InterviewStatus;
import com.jobsapply.platform.tracking.TrackingService;
import com.jobsapply.platform.tracking.TrackingViews;
import com.jobsapply.platform.tracking.TrackingViews.ContactInput;
import com.jobsapply.platform.tracking.TrackingViews.ContactView;
import com.jobsapply.platform.tracking.TrackingViews.FollowUpInput;
import com.jobsapply.platform.tracking.TrackingViews.FollowUpView;
import com.jobsapply.platform.tracking.TrackingViews.InterviewInput;
import com.jobsapply.platform.tracking.TrackingViews.InterviewView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My tracking", description = "Contacts, interviews and follow-ups (private, encrypted at rest)")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
class TrackingController {

    private final TrackingService tracking;

    record StatusChange(@NotNull InterviewStatus status) {}

    @GetMapping("/tracking/summary")
    ResponseEntity<TrackingViews.Summary> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tracking.summary());
    }

    @GetMapping("/contacts")
    List<ContactView> contacts() {
        return tracking.contacts();
    }

    @GetMapping("/contacts/{id}")
    ContactView contact(@PathVariable UUID id) {
        return tracking.contact(id);
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ContactView createContact(@Valid @RequestBody ContactInput body) {
        return tracking.createContact(body);
    }

    @PutMapping("/contacts/{id}")
    ContactView updateContact(@PathVariable UUID id, @Valid @RequestBody ContactInput body) {
        return tracking.updateContact(id, body);
    }

    @DeleteMapping("/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteContact(@PathVariable UUID id) {
        tracking.deleteContact(id);
    }

    @GetMapping("/interviews")
    List<InterviewView> interviews(@RequestParam(required = false) UUID applicationId) {
        return tracking.interviews(applicationId);
    }

    @PostMapping("/applications/{id}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    InterviewView schedule(@PathVariable UUID id, @Valid @RequestBody InterviewInput body) {
        return tracking.scheduleInterview(id, body);
    }

    @PutMapping("/interviews/{id}")
    InterviewView updateInterview(@PathVariable UUID id, @Valid @RequestBody InterviewInput body) {
        return tracking.updateInterview(id, body);
    }

    @PostMapping("/interviews/{id}/status")
    InterviewView markInterview(@PathVariable UUID id, @Valid @RequestBody StatusChange body) {
        return tracking.markInterview(id, body.status());
    }

    @DeleteMapping("/interviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteInterview(@PathVariable UUID id) {
        tracking.deleteInterview(id);
    }

    @GetMapping("/follow-ups")
    List<FollowUpView> followUps(@RequestParam(defaultValue = "false") boolean includeDone, @RequestParam(required = false) UUID applicationId) {
        return tracking.followUps(includeDone, applicationId);
    }

    @PostMapping("/follow-ups")
    @ResponseStatus(HttpStatus.CREATED)
    FollowUpView createFollowUp(@Valid @RequestBody FollowUpInput body) {
        return tracking.createFollowUp(body);
    }

    @PutMapping("/follow-ups/{id}")
    FollowUpView updateFollowUp(@PathVariable UUID id, @Valid @RequestBody FollowUpInput body) {
        return tracking.updateFollowUp(id, body);
    }

    @PostMapping("/follow-ups/{id}/done")
    FollowUpView complete(@PathVariable UUID id) {
        return tracking.completeFollowUp(id);
    }

    @PostMapping("/follow-ups/{id}/reopen")
    FollowUpView reopen(@PathVariable UUID id) {
        return tracking.reopenFollowUp(id);
    }

    @DeleteMapping("/follow-ups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFollowUp(@PathVariable UUID id) {
        tracking.deleteFollowUp(id);
    }
}
