package in.tamiland.market;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record Enquiry(
        @NotBlank String plotId,
        @NotBlank String name,
        @Pattern(regexp = "^[0-9+()\\-\\s]{8,18}$", message = "Enter a valid phone number") String phone,
        @Email String email,
        @NotBlank String requestType,
        String preferredDate,
        String message) {}
