package com.jobsapply.platform.posting;

import com.jobsapply.spi.common.Availability;
import java.util.UUID;

public record ListingRef(UUID listingId, UUID employerSourceId, String externalId, Availability availability) {}
