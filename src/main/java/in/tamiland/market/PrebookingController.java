package in.tamiland.market;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/prebookings")
public class PrebookingController {
    private final PrebookingService prebookings;
    private final NotificationService notifications;

    public PrebookingController(PrebookingService prebookings,NotificationService notifications) { this.prebookings=prebookings;this.notifications=notifications; }

    public record Request(String plotId,String message) {}
    public record Decision(String status) {}

    @GetMapping("/availability/{plotId}")
    public PrebookingService.Availability availability(@PathVariable String plotId) {
        try { return prebookings.availability(plotId); }
        catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage()); }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrebookingView request(@RequestBody Request request,Authentication authentication) {
        requireRole(authentication,"ROLE_BUYER","Please sign in with a buyer account to request a pre-booking");
        if(request.plotId()==null||request.plotId().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a land listing");
        if(request.message()!=null&&request.message().length()>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Message must be 1000 characters or less");
        try { PrebookingView saved=prebookings.request(request.plotId().trim(),authentication.getName(),request.message());notifications.notifyPrebookingOwner(saved);return saved; }
        catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage()); }
        catch(IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage()); }
    }

    @GetMapping("/mine")
    public List<PrebookingView> mine(Authentication authentication) {
        requireRole(authentication,"ROLE_BUYER","Buyer account required");
        return prebookings.forBuyer(authentication.getName());
    }

    @GetMapping("/owner")
    public List<PrebookingView> ownerRequests(Authentication authentication) {
        requireRole(authentication,"ROLE_OWNER","Owner account required");
        return prebookings.forOwner(authentication.getName());
    }

    @PutMapping("/{id}/owner")
    public PrebookingView ownerDecision(@PathVariable long id,@RequestBody Decision decision,Authentication authentication) {
        requireRole(authentication,"ROLE_OWNER","Owner account required");
        if(decision.status()==null||!List.of("APPROVED","DECLINED","CANCELLED").contains(decision.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose approve, decline, or release");
        try { PrebookingView saved=prebookings.ownerDecision(id,authentication.getName(),decision.status());notifications.notifyPrebookingBuyer(saved);return saved; }
        catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage()); }
        catch(IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage()); }
    }

    @PutMapping("/{id}/cancel")
    public PrebookingView buyerCancel(@PathVariable long id,Authentication authentication) {
        requireRole(authentication,"ROLE_BUYER","Buyer account required");
        try { PrebookingView saved=prebookings.buyerCancel(id,authentication.getName());notifications.notifyPrebookingOwner(saved);return saved; }
        catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage()); }
        catch(IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage()); }
    }

    private void requireRole(Authentication authentication,String role,String message) {
        if(authentication==null||authentication.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(role)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,message);
    }
}
