package com.jobsapply.platform.tracking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Inputs and views of the tracking module. All of them may contain personal data: never log them. */
public final class TrackingViews {

    private TrackingViews() {}

    public record ContactInput(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 320) String email,
            @Size(max = 50) String phone,
            @Size(max = 200) String title,
            @Size(max = 500) String profileUrl,
            @Size(max = 4000) String notes,
            UUID employerId) {
        @Override
        public String toString() {
            return "ContactInput[redacted]";
        }
    }

    public record ContactView(UUID id, UUID employerId, String name, String email, String phone, String title, String profileUrl,
            String notes, Instant createdAt, Instant updatedAt) {
        @Override
        public String toString() {
            return "ContactView[" + id + "]";
        }
    }

    public record InterviewInput(
            @NotNull Instant scheduledAt,
            @Min(5) @Max(600) Integer durationMinutes,
            @NotNull InterviewKind kind,
            @Size(max = 500) String location,
            @Size(max = 4000) String notes,
            @Size(max = 10) List<UUID> contactIds) {
        @Override
        public String toString() {
            return "InterviewInput[" + kind + ", redacted]";
        }
    }

    public record InterviewView(UUID id, UUID applicationId, Instant scheduledAt, Integer durationMinutes, InterviewKind kind,
            InterviewStatus status, String location, String notes, List<UUID> contactIds, Instant createdAt) {
        @Override
        public String toString() {
            return "InterviewView[" + id + "]";
        }
    }

    public record FollowUpInput(
            @NotNull LocalDate dueOn,
            @NotBlank @Size(max = 1000) String note,
            UUID applicationId,
            UUID contactId) {
        @Override
        public String toString() {
            return "FollowUpInput[" + dueOn + ", redacted]";
        }
    }

    /** @param overdue open and due before today */
    public record FollowUpView(UUID id, UUID applicationId, UUID contactId, LocalDate dueOn, boolean done, Instant doneAt, String note,
            boolean overdue) {
        @Override
        public String toString() {
            return "FollowUpView[" + id + "]";
        }
    }

    /** For the dashboard: what is coming up. */
    public record Summary(List<InterviewView> upcomingInterviews, List<FollowUpView> openFollowUps, long overdueFollowUps) {}
}
