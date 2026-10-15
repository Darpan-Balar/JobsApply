package com.jobsapply.platform.tracking;

import com.jobsapply.platform.application.ApplicationService;
import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.ApplicationView;
import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.persistence.OwnedEntity;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.tracking.TrackingViews.ContactInput;
import com.jobsapply.platform.tracking.TrackingViews.ContactView;
import com.jobsapply.platform.tracking.TrackingViews.FollowUpInput;
import com.jobsapply.platform.tracking.TrackingViews.FollowUpView;
import com.jobsapply.platform.tracking.TrackingViews.InterviewInput;
import com.jobsapply.platform.tracking.TrackingViews.InterviewView;
import com.jobsapply.platform.tracking.internal.Contact;
import com.jobsapply.platform.tracking.internal.ContactRepository;
import com.jobsapply.platform.tracking.internal.Details.ContactDetails;
import com.jobsapply.platform.tracking.internal.Details.FollowUpDetails;
import com.jobsapply.platform.tracking.internal.Details.InterviewDetails;
import com.jobsapply.platform.tracking.internal.FollowUp;
import com.jobsapply.platform.tracking.internal.FollowUpRepository;
import com.jobsapply.platform.tracking.internal.Interview;
import com.jobsapply.platform.tracking.internal.InterviewRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * The current user's contacts, interviews and follow-ups. Identifying data and notes are encrypted with row-bound
 * associated data, so an envelope copied to another row or user does not decrypt. "Today" is the local date of the
 * machine the platform runs on (local-first).
 */
@Service
@Validated
@RequiredArgsConstructor
public class TrackingService {

    private static final EnumSet<ApplicationStatus> INTERVIEWABLE = EnumSet.of(ApplicationStatus.SUBMITTED);
    private static final Duration UPCOMING = Duration.ofDays(14);
    private static final int DUE_SOON_DAYS = 7;

    private final ContactRepository contacts;
    private final InterviewRepository interviews;
    private final FollowUpRepository followUps;
    private final ApplicationService applications;
    private final PersonalDataCipher cipher;
    private final JsonCodec json;
    private final Clock clock;

    // --- contacts ------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ContactView> contacts() {
        UserContext.requireUserId();
        return contacts.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ContactView contact(UUID id) {
        return view(contact0(id));
    }

    @Transactional
    public ContactView createContact(@Valid ContactInput input) {
        UserContext.requireUserId();
        Contact contact = Contact.builder().employerId(input.employerId()).details("{}").build();
        contact.update(input.employerId(), seal("contact", contact, details(input)));
        return view(flush(contacts.save(contact)));
    }

    @Transactional
    public ContactView updateContact(UUID id, @Valid ContactInput input) {
        Contact contact = contact0(id);
        contact.update(input.employerId(), seal("contact", contact, details(input)));
        return view(flush(contact));
    }

    /** Follow-ups about this contact keep existing, without the contact. */
    @Transactional
    public void deleteContact(UUID id) {
        contacts.delete(contact0(id));
    }

    // --- interviews ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InterviewView> interviews(UUID applicationId) {
        UserContext.requireUserId();
        if (applicationId != null) {
            applications.get(applicationId);
            return interviews.findByApplicationIdOrderByScheduledAtAsc(applicationId).stream().map(this::view).toList();
        }
        return interviews.findAllByOrderByScheduledAtAsc().stream().map(this::view).toList();
    }

    /** Interviews belong to submitted applications. */
    @Transactional
    public InterviewView scheduleInterview(UUID applicationId, @Valid InterviewInput input) {
        ApplicationView application = applications.get(applicationId);
        if (!INTERVIEWABLE.contains(application.status())) {
            throw new DomainRuleViolationException("Interviews can be added to submitted applications only (this one is "
                    + application.status() + ")");
        }
        requireOwnContacts(input.contactIds());
        Interview interview = Interview.builder().applicationId(applicationId).scheduledAt(input.scheduledAt())
                .durationMinutes(input.durationMinutes()).kind(input.kind()).status(InterviewStatus.SCHEDULED).details("{}").build();
        interview.reschedule(input.scheduledAt(), input.durationMinutes(), input.kind(), seal("interview", interview, details(input)));
        return view(flush(interviews.save(interview)));
    }

    @Transactional
    public InterviewView updateInterview(UUID id, @Valid InterviewInput input) {
        Interview interview = interview0(id);
        requireOwnContacts(input.contactIds());
        interview.reschedule(input.scheduledAt(), input.durationMinutes(), input.kind(), seal("interview", interview, details(input)));
        return view(flush(interview));
    }

    @Transactional
    public InterviewView markInterview(UUID id, InterviewStatus status) {
        Interview interview = interview0(id);
        interview.mark(status);
        return view(flush(interview));
    }

    @Transactional
    public void deleteInterview(UUID id) {
        interviews.delete(interview0(id));
    }

    // --- follow-ups ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FollowUpView> followUps(boolean includeDone, UUID applicationId) {
        UserContext.requireUserId();
        LocalDate today = today();
        List<FollowUp> found = applicationId != null ? followUps.findByApplicationIdOrderByDueOnAsc(applicationId)
                : includeDone ? followUps.findAllByOrderByDueOnAsc() : followUps.findByDoneAtIsNullOrderByDueOnAsc();
        return found.stream().filter(f -> includeDone || f.getDoneAt() == null).map(f -> view(f, today)).toList();
    }

    @Transactional
    public FollowUpView createFollowUp(@Valid FollowUpInput input) {
        requireReferences(input);
        FollowUp followUp = FollowUp.builder().dueOn(input.dueOn()).applicationId(input.applicationId()).contactId(input.contactId())
                .details("{}").build();
        followUp.update(input.dueOn(), input.applicationId(), input.contactId(), seal("follow_up", followUp, new FollowUpDetails(input.note())));
        return view(flush(followUps.save(followUp)), today());
    }

    @Transactional
    public FollowUpView updateFollowUp(UUID id, @Valid FollowUpInput input) {
        FollowUp followUp = followUp0(id);
        requireReferences(input);
        followUp.update(input.dueOn(), input.applicationId(), input.contactId(), seal("follow_up", followUp, new FollowUpDetails(input.note())));
        return view(flush(followUp), today());
    }

    @Transactional
    public FollowUpView completeFollowUp(UUID id) {
        FollowUp followUp = followUp0(id);
        if (followUp.getDoneAt() == null) {
            followUp.complete(Instant.now(clock));
        }
        return view(flush(followUp), today());
    }

    @Transactional
    public FollowUpView reopenFollowUp(UUID id) {
        FollowUp followUp = followUp0(id);
        followUp.reopen();
        return view(flush(followUp), today());
    }

    @Transactional
    public void deleteFollowUp(UUID id) {
        followUps.delete(followUp0(id));
    }

    // --- dashboard -----------------------------------------------------------------------------------------------------

    /** Scheduled interviews in the next 14 days, open follow-ups due within a week (overdue included). */
    @Transactional(readOnly = true)
    public TrackingViews.Summary summary() {
        UserContext.requireUserId();
        Instant now = Instant.now(clock);
        LocalDate today = today();
        List<InterviewView> upcoming = interviews.findByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                InterviewStatus.SCHEDULED, now, now.plus(UPCOMING)).stream().map(this::view).toList();
        List<FollowUpView> due = followUps.findByDoneAtIsNullOrderByDueOnAsc().stream()
                .filter(f -> !f.getDueOn().isAfter(today.plusDays(DUE_SOON_DAYS))).map(f -> view(f, today)).toList();
        return new TrackingViews.Summary(upcoming, due, followUps.countByDoneAtIsNullAndDueOnBefore(today));
    }

    // --- internals -----------------------------------------------------------------------------------------------------

    private void requireReferences(FollowUpInput input) {
        if (input.applicationId() != null) {
            applications.get(input.applicationId());
        }
        if (input.contactId() != null) {
            contact0(input.contactId());
        }
    }

    private void requireOwnContacts(List<UUID> ids) {
        if (ids != null) {
            ids.forEach(this::contact0);
        }
    }

    private Contact contact0(UUID id) {
        UserContext.requireUserId();
        return contacts.findById(id).orElseThrow(() -> new NotFoundException("Contact", id));
    }

    private Interview interview0(UUID id) {
        UserContext.requireUserId();
        return interviews.findById(id).orElseThrow(() -> new NotFoundException("Interview", id));
    }

    private FollowUp followUp0(UUID id) {
        UserContext.requireUserId();
        return followUps.findById(id).orElseThrow(() -> new NotFoundException("Follow-up", id));
    }

    private <T> T flush(T entity) {
        try {
            contacts.flush();
            return entity;
        } catch (DataIntegrityViolationException e) {
            throw new DomainRuleViolationException("A referenced employer, application or contact does not exist");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.systemDefault()));
    }

    private String seal(String table, OwnedEntity row, Object details) {
        return cipher.encrypt(json.write(details), aad(table, row));
    }

    private <T> T open(String table, OwnedEntity row, String envelope, Class<T> type) {
        return json.read(cipher.decrypt(envelope, aad(table, row)), type);
    }

    private static String aad(String table, OwnedEntity row) {
        return table + "|" + row.getId() + "|" + row.getOwnerUserIdOrCurrent();
    }

    private static ContactDetails details(ContactInput input) {
        return new ContactDetails(input.name().strip(), input.email(), input.phone(), input.title(), input.profileUrl(), input.notes());
    }

    private static InterviewDetails details(InterviewInput input) {
        return new InterviewDetails(input.location(), input.notes(), input.contactIds() == null ? List.of() : List.copyOf(input.contactIds()));
    }

    private ContactView view(Contact c) {
        ContactDetails d = open("contact", c, c.getDetails(), ContactDetails.class);
        return new ContactView(c.getId(), c.getEmployerId(), d.name(), d.email(), d.phone(), d.title(), d.profileUrl(), d.notes(),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    private InterviewView view(Interview i) {
        InterviewDetails d = open("interview", i, i.getDetails(), InterviewDetails.class);
        return new InterviewView(i.getId(), i.getApplicationId(), i.getScheduledAt(), i.getDurationMinutes(), i.getKind(), i.getStatus(),
                d.location(), d.notes(), d.contactIds() == null ? List.of() : d.contactIds(), i.getCreatedAt());
    }

    private FollowUpView view(FollowUp f, LocalDate today) {
        FollowUpDetails d = open("follow_up", f, f.getDetails(), FollowUpDetails.class);
        return new FollowUpView(f.getId(), f.getApplicationId(), f.getContactId(), f.getDueOn(), f.getDoneAt() != null, f.getDoneAt(),
                d.note(), f.getDoneAt() == null && f.getDueOn().isBefore(today));
    }
}
