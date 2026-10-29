package in.tamiland.market;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PlotController {
    private final PlotService service;
    public PlotController(PlotService service) { this.service = service; }

    @GetMapping("/plots")
    public List<Plot> plots(@RequestParam(required=false) String city, @RequestParam(required=false) String type,
        @RequestParam(required=false) Long minPrice, @RequestParam(required=false) Long maxPrice,
        @RequestParam(required=false) Double minAcres, @RequestParam(required=false) Double maxAcres,
        @RequestParam(required=false) Boolean approved, @RequestParam(required=false) String q) {
        return service.search(city,type,minPrice,maxPrice,minAcres,maxAcres,approved,q);
    }
    @GetMapping("/plots/{id}")
    public Plot plot(@PathVariable String id) { return service.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Plot not found")); }
    @GetMapping("/my-plots")
    public List<Plot> myPlots(Authentication authentication) { return service.ownerPlots(authentication.getName()); }
    @GetMapping("/my-plots/review-status")
    public List<ListingReview> myPlotReviewStatus(Authentication authentication) { return service.ownerListingReviews(authentication.getName()); }
    @PostMapping("/plots")
    @ResponseStatus(HttpStatus.CREATED)
    public Plot createPlot(@RequestBody Plot plot,Authentication authentication) {
        if(authentication==null||authentication.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_OWNER"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Owner account required");
        PlotValidator.validate(plot);
        if(service.idExists(plot.id())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Plot ID already exists");
        return service.insertPlot(withAccountContact(plot,authentication.getName()),authentication.getName());
    }

    @PutMapping("/plots/{id}")
    public Plot updatePlot(@PathVariable String id,@RequestBody Plot plot,Authentication authentication) {
        if(!id.equals(plot.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Plot ID cannot be changed");
        if(!service.ownsPlot(id,authentication.getName())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Your plot was not found");
        PlotValidator.validate(plot);
        plot=withAccountContact(plot,authentication.getName());
        if(service.isReserved(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Release the active pre-booking before editing this listing");
        if(!service.updatePlot(plot,authentication.getName())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Plot could not be updated");
        return plot.withVerification("SELF_DECLARED");
    }

    @DeleteMapping("/plots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlot(@PathVariable String id,Authentication authentication) {
        if(!service.ownsPlot(id,authentication.getName())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Your plot was not found");
        if(service.isReserved(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Release the active pre-booking before removing this listing");
        if(!service.deletePlot(id,authentication.getName())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Your plot was not found");
    }

    @GetMapping("/plots/{id}/photos")
    public List<String> photos(@PathVariable String id) { return service.photos(id); }
    @GetMapping("/plots/{id}/owner")
    public OwnerProfile listingOwner(@PathVariable String id) {
        String email=service.ownerEmail(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner profile not found"));
        OwnerProfile profile=service.ownerProfile(email);
        if(profile==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner profile not found");
        return profile;
    }

    @GetMapping("/owners/{email}")
    public OwnerProfile ownerProfile(@PathVariable String email) {
        OwnerProfile profile=service.ownerProfile(email);
        if(profile==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner profile not found");
        return profile;
    }

    public record ReviewRequest(String status) {}
    @GetMapping("/admin/listings/pending")
    public List<ListingReview> pendingListings(Authentication authentication) { requireAdmin(authentication); return service.pendingReviews(); }
    @GetMapping("/admin/enquiries")
    public List<EnquiryView> adminEnquiries(Authentication authentication) { requireAdmin(authentication); return service.allEnquiries(); }
    @PutMapping("/admin/listings/{id}/review")
    public void reviewListing(@PathVariable String id,@RequestBody ReviewRequest request,Authentication authentication) {
        requireAdmin(authentication);
        if(!List.of("APPROVED","REJECTED").contains(request.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose APPROVED or REJECTED");
        if(!service.review(id,request.status())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pending listing not found");
    }
    private Plot withAccountContact(Plot plot,String email) {
        String[] account=service.accountContact(email);
        if(account==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account not found");
        String phone=account[1]!=null&&!account[1].isBlank()?account[1]:plot.phone();
        String role="Agent".equals(plot.ownerRole())?"Agent":"Owner";
        return plot.withContact(account[0],role,phone==null?"":phone);
    }
    private void requireAdmin(Authentication authentication) {
        if(authentication==null||authentication.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin account required");
    }
}
