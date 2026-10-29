package in.tamiland.market;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/support-queries")
public class AdminSupportController {
    private final SupportService support;
    public AdminSupportController(SupportService support) { this.support=support; }
    public record StatusUpdate(String status) {}
    @GetMapping public List<SupportService.Query> queries(Authentication user) {
        requireAdmin(user);
        return support.all();
    }
    @PutMapping("/{id}/status") public void update(@PathVariable long id,@RequestBody StatusUpdate request,Authentication user) {
        requireAdmin(user);
        if(request.status()==null||!List.of("OPEN","IN_PROGRESS","RESOLVED").contains(request.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid support status");
        if(!support.updateStatus(id,request.status())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Support query not found");
    }
    private void requireAdmin(Authentication user) {
        if(user==null||user.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin account required");
    }
}
