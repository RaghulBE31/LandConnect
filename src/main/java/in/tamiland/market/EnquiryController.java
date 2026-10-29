package in.tamiland.market;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;

@RestController
@RequestMapping("/api/enquiries")
public class EnquiryController {
    private final PlotService plots;
    private final NotificationService notifications;
    public EnquiryController(PlotService plots,NotificationService notifications){this.plots=plots;this.notifications=notifications;}

    public record Submission(EnquiryView enquiry,String adminEmailStatus,String ownerEmailStatus) {}
    public record VisitUpdate(String status,String preferredDate) {}
    private void validateDate(String date) {
        if(date==null||date.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a visit date");
        try { if(LocalDate.parse(date).isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose today or a future date"); }
        catch(DateTimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid visit date"); }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Submission submit(@Valid @RequestBody Enquiry enquiry,Authentication authentication) {
        if(plots.find(enquiry.plotId()).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plot not found");
        if(!List.of("CONTACT","SITE_VISIT").contains(enquiry.requestType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported request type");
        boolean signedIn=authentication!=null&&authentication.isAuthenticated()&&!authentication.getPrincipal().equals("anonymousUser");
        if("SITE_VISIT".equals(enquiry.requestType())&&!signedIn&&(enquiry.email()==null||enquiry.email().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Add your email so we can keep you updated about the visit");
        String email=signedIn?authentication.getName():null;
        EnquiryView saved=plots.save(enquiry,email);
        NotificationService.Result result=notifications.notifyEnquiry(saved);
        return new Submission(saved,result.adminEmailStatus(),result.ownerEmailStatus());
    }

    @GetMapping("/mine")
    public List<EnquiryView> mine(Authentication authentication) {
        boolean owner=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_OWNER"));
        return plots.enquiriesFor(authentication.getName(),owner);
    }

    @PutMapping("/{id}/visit")
    public EnquiryView updateVisit(@PathVariable long id,@RequestBody VisitUpdate update,Authentication authentication) {
        if(!List.of("CONFIRMED","RESCHEDULED","COMPLETED","CANCELLED").contains(update.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported site visit status");
        if("RESCHEDULED".equals(update.status())) validateDate(update.preferredDate());
        EnquiryView before=plots.findEnquiry(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Site visit request not found"));
        if(before.ownerEmail()==null||!before.ownerEmail().trim().equalsIgnoreCase(authentication.getName().trim()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Site visit not found for your listings");
        if(!plots.updateVisitStatus(id,authentication.getName(),update.status(),update.preferredDate()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This visit has changed. Refresh your dashboard and try again.");
        EnquiryView saved=plots.findEnquiry(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Site visit request not found"));
        notifications.notifyVisitStatus(saved);
        return saved;
    }

    @PutMapping("/{id}/buyer-response")
    public EnquiryView buyerResponse(@PathVariable long id,@RequestBody VisitUpdate update,Authentication authentication) {
        if(authentication==null||!authentication.isAuthenticated()||authentication.getPrincipal().equals("anonymousUser"))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to respond to this visit");
        EnquiryView before=plots.findEnquiry(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Site visit request not found"));
        String email=authentication.getName();
        boolean isBuyer=before.buyerEmail()!=null&&before.buyerEmail().trim().equalsIgnoreCase(email.trim());
        if(before.buyerEmail()==null&&before.email()!=null) isBuyer=before.email().trim().equalsIgnoreCase(email.trim());
        if(!isBuyer) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sign in with the email address used to request this site visit");
        if(!"SITE_VISIT".equals(before.requestType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This request is not a site visit");
        if(!List.of("CONFIRMED","CHANGE_REQUESTED","CANCELLED").contains(update.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported site visit response");
        if("CHANGE_REQUESTED".equals(update.status())) validateDate(update.preferredDate());
        if(!plots.updateBuyerVisitStatus(id,email,update.status(),update.preferredDate()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This visit has changed or is no longer awaiting your response");
        EnquiryView saved=plots.findEnquiry(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Site visit request not found"));
        notifications.notifyOwnerVisitResponse(saved);
        return saved;
    }
}
