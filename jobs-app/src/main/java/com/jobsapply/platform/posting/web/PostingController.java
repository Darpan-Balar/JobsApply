package com.jobsapply.platform.posting.web;

import com.jobsapply.platform.posting.PostingQuery;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingSort;
import com.jobsapply.platform.posting.PostingViews;
import com.jobsapply.platform.shared.model.PageResult;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Postings", description = "Discovered job postings (shared catalog)")
@Validated
@RestController
@RequestMapping("/api/v1/postings")
@RequiredArgsConstructor
class PostingController {

    private final PostingQueryService postings;

    @Operation(summary = "Search postings", description = "stale=true: not closed but not seen recently (a flag, never a closure).")
    @GetMapping
    PageResult<PostingViews.Summary> search(
            @RequestParam(required = false) String employer,
            @RequestParam(required = false) Availability availability,
            @RequestParam(required = false) WorkplaceType workplace,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) Seniority seniority,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean stale,
            @RequestParam(required = false) Instant seenSince,
            @RequestParam(defaultValue = "LAST_SEEN") PostingSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return postings.search(PostingQuery.builder().employerKey(employer).availability(availability).workplaceType(workplace)
                .employmentType(employmentType).seniority(seniority).countryCode(country).text(q).stale(stale).seenSince(seenSince)
                .build(), sort, page, size);
    }

    @GetMapping("/{id}")
    PostingViews.Detail get(@PathVariable UUID id) {
        return postings.get(id);
    }
}
