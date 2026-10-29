package in.tamiland.market;

import java.time.LocalDateTime;

public record EnquiryView(long id, String plotId, String plotTitle, String city, String locality,
                          String ownerEmail, String buyerEmail, String name, String phone, String email,
                          String requestType, String preferredDate, String message, String status, LocalDateTime createdAt) {}
