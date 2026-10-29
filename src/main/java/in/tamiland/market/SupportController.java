package in.tamiland.market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportService support;
    public SupportController(SupportService support) { this.support=support; }

    public record QueryRequest(@NotBlank @Size(max=120) String name,
                               @NotBlank @Email @Size(max=255) String email,
                               @NotBlank @Pattern(regexp="Website issue|Account or sign-in|Listing or plot details|Enquiry or site visit|Other question") String topic,
                               @NotBlank @Size(min=10,max=3000) String message,
                               String website) {}
    public record QueryResponse(long id,String emailStatus) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QueryResponse submit(@Valid @RequestBody QueryRequest request) {
        // Quietly accept a honeypot submission without storing or mailing it.
        if(request.website()!=null&&!request.website().isBlank()) return new QueryResponse(0,"RECEIVED");
        return support.submit(request);
    }
}
