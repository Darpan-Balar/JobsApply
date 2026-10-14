package com.jobsapply.platform.posting;

import com.jobsapply.platform.employer.BindingRef;
import com.jobsapply.platform.employer.EmployerRef;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.posting.internal.JobListing;
import com.jobsapply.platform.posting.internal.JobListingRepository;
import com.jobsapply.platform.posting.internal.JobPosting;
import com.jobsapply.platform.posting.internal.JobPostingRepository;
import com.jobsapply.platform.posting.internal.PostingSpecifications;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.model.PageResult;
import com.jobsapply.spi.common.Availability;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostingQueryService {

    private final JobPostingRepository postings;
    private final JobListingRepository listings;
    private final EmployerService employers;
    private final PostingProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResult<PostingViews.Summary> search(PostingQuery query, PostingSort sort, int page, int size) {
        Optional<UUID> employerId = query.employerKey() == null ? Optional.empty() : employers.findId(query.employerKey());
        if (query.employerKey() != null && employerId.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0, 0);
        }
        Instant staleBefore = staleBefore();
        Page<JobPosting> result = postings.findAll(
                PostingSpecifications.matching(query, employerId.orElse(null), staleBefore), PageRequest.of(page, size, sort.sort()));
        Map<UUID, EmployerRef> refs = employers.refs(result.getContent().stream().map(JobPosting::getEmployerId).distinct().toList());
        return PageResult.of(result, p -> summary(p, refs.get(p.getEmployerId()), staleBefore));
    }

    @Transactional(readOnly = true)
    public PostingViews.Detail get(UUID id) {
        JobPosting posting = postings.findById(id).orElseThrow(() -> new NotFoundException("Posting", id));
        var postingListings = listings.findByJobPostingIdOrderByFirstSeenAtAsc(id);
        Map<UUID, BindingRef> bindings = employers.bindingRefs(postingListings.stream().map(JobListing::getEmployerSourceId).toList());
        EmployerRef employer = employers.refs(List.of(posting.getEmployerId())).get(posting.getEmployerId());
        return new PostingViews.Detail(summary(posting, employer, staleBefore()), posting.getDescriptionText(),
                postingListings.stream().map(l -> new PostingViews.Listing(l.getId(), bindings.get(l.getEmployerSourceId()).sourceKey(),
                        l.getExternalId(), l.getListingUrl(), l.getApplicationUrl(), l.getPublishedAt(), l.getSourceUpdatedAt(),
                        l.getAvailability(), l.getAvailabilityEvidence(), l.getFirstSeenAt(), l.getLastSeenAt(), l.getLastCheckedAt())).toList());
    }

    private Instant staleBefore() {
        return clock.instant().minus(properties.staleAfter());
    }

    private static PostingViews.Summary summary(JobPosting p, EmployerRef employer, Instant staleBefore) {
        PostingViews.Salary salary = p.getSalaryCurrency() == null ? null
                : new PostingViews.Salary(p.getSalaryMin(), p.getSalaryMax(), p.getSalaryCurrency(), p.getSalaryPeriod());
        return new PostingViews.Summary(p.getId(), employer.key(), employer.displayName(), p.getTitle(), p.getSeniority(),
                p.getEmploymentType(), p.getWorkplaceType(), salary, p.getAvailability(),
                p.getAvailability() != Availability.CLOSED && p.getLastSeenAt().isBefore(staleBefore),
                p.getFirstSeenAt(), p.getLastSeenAt(),
                p.getLocations().stream().map(l -> new PostingViews.Location(l.getRawText(), l.getCity(), l.getRegion(),
                        l.getCountryCode(), l.isRemote())).toList());
    }
}
