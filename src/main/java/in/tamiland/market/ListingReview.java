package in.tamiland.market;

import java.time.LocalDateTime;

public record ListingReview(Plot plot, String ownerEmail, String ownerName, String reviewStatus, LocalDateTime submittedAt) {}
