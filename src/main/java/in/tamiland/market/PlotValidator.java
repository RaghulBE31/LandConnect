package in.tamiland.market;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/** Server-side validation of owner-submitted listings. The browser form is not a trust boundary. */
final class PlotValidator {
    private static final Pattern ID=Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{2,39}$");
    private static final Pattern PHONE=Pattern.compile("^[+0-9 ()-]{7,20}$");
    private static final Pattern SAFE_IMAGE=Pattern.compile("^(https://[^\\s'\"()<>\\\\]{4,480}|/uploads/[A-Za-z0-9._/-]{1,200})$");
    private static final List<String> TYPES=List.of("Residential","Agricultural","Commercial");
    private static final List<String> ROLES=List.of("Owner","Agent");

    private PlotValidator() {}

    static void validate(Plot p) {
        if(p==null) bad("Listing details are required");
        require(p.id()!=null&&ID.matcher(p.id()).matches(),"Plot ID must be 3-40 letters, digits or hyphens");
        text(p.title(),"Title",5,120,true);
        require(p.type()!=null&&TYPES.stream().anyMatch(t->t.equalsIgnoreCase(p.type())),"Land type must be Residential, Agricultural or Commercial");
        text(p.city(),"City",2,100,true); text(p.district(),"District",2,100,true); text(p.locality(),"Locality",2,120,true);
        text(p.address(),"Address",0,250,false);
        text(p.roadWidth(),"Road width",0,40,false); text(p.facing(),"Facing",0,40,false); text(p.zoning(),"Zoning",0,40,false);
        text(p.approvalLabel(),"Approval label",0,80,false); text(p.patta(),"Patta",0,80,false); text(p.surveyNo(),"Survey number",0,80,false);
        text(p.description(),"Description",0,2000,false);
        require(p.price()>0&&p.price()<=100_000_000_000L,"Price must be a positive amount");
        require(p.acres()>0&&p.acres()<=100_000,"Area must be a positive number of acres");
        require(p.cents()>=0&&p.cents()<=10_000_000,"Cents must be zero or more");
        require(p.pricePerCent()>=0&&p.pricePerCent()<=100_000_000_000L,"Price per cent is out of range");
        // Rough Tamil Nadu bounding box (with margin) to catch swapped or zeroed coordinates.
        require(p.latitude()>=7.5&&p.latitude()<=14.5&&p.longitude()>=75.5&&p.longitude()<=81.0,"Location must be inside Tamil Nadu");
        require(p.image()==null||p.image().isBlank()||SAFE_IMAGE.matcher(p.image()).matches(),"Cover image must be an https link or an uploaded photo");
        if(p.ownerRole()!=null&&!p.ownerRole().isBlank()) require(ROLES.contains(p.ownerRole()),"Listing role must be Owner or Agent");
        if(p.phone()!=null&&!p.phone().isBlank()) require(PHONE.matcher(p.phone()).matches(),"Phone number looks invalid");
        List<String> highlights=p.highlights()==null?List.of():p.highlights();
        require(highlights.size()<=12,"Use at most 12 highlights");
        highlights.forEach(h->text(h,"Highlight",1,80,true));
        List<Plot.Point> boundary=p.boundary()==null?List.of():p.boundary();
        require(boundary.size()<=60,"Boundary has too many points");
        boundary.forEach(pt->require(pt!=null&&pt.lat()>=7.5&&pt.lat()<=14.5&&pt.lng()>=75.5&&pt.lng()<=81.0,"Boundary point is outside Tamil Nadu"));
    }

    private static void text(String value,String label,int min,int max,boolean required) {
        if(value==null||value.isBlank()) { if(required) bad(label+" is required"); return; }
        require(value.length()>=min&&value.length()<=max,label+" must be "+min+"-"+max+" characters");
        // Listings are rendered into HTML by the browser app; reject markup and control characters outright.
        require(value.chars().noneMatch(c->c=='<'||c=='>'||(c<32&&c!='\n'&&c!='\r'&&c!='\t')),label+" cannot contain < > or control characters");
    }

    private static void require(boolean ok,String message) { if(!ok) bad(message); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
