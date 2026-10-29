package in.tamiland.market;

import java.time.LocalDateTime;

public record PrebookingView(
        long id,
        String plotId,
        String plotTitle,
        String city,
        String locality,
        String ownerEmail,
        String buyerEmail,
        String buyerName,
        String phone,
        String message,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt) {}
