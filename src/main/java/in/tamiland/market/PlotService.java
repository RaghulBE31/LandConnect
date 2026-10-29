package in.tamiland.market;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.util.stream.Stream;

@Service
public class PlotService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final List<Plot> plots = List.of(
      plot("TN-CH-102","A calm corner plot near the coast","Residential","Chennai","Chengalpattu","ECR · Uthandi","Uthandi, East Coast Road, Chennai",12.872,80.247,8,0.20,6400000,800000,"30 ft","East","Residential",true,"CMDA approved","Available","Survey 14/2B",true,true,true,"Kavitha Raman","Owner","+91 98765 41028","A ready-to-build corner parcel tucked just off the ECR, with a wide approach road and established homes nearby.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("Corner site","30 ft approach","Electricity nearby","Clear title copy"),12.872,80.247),
      plot("TN-CB-208","Green-edge homesite in Vadavalli","Residential","Coimbatore","Coimbatore","Vadavalli","Marudhamalai Road, Vadavalli, Coimbatore",11.044,76.872,10,0.25,5250000,525000,"33 ft","North","Residential",true,"DTCP approved","Available","Survey 88/1A",true,true,true,"Arun Prakash","Owner","+91 98432 71046","A levelled homesite in a quiet residential pocket, with hill views and easy access to schools and the city.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("North facing","DTCP layout","Water line nearby","Levelled plot"),11.044,76.872),
      plot("TN-MD-315","Open residential plot at Othakadai","Residential","Madurai","Madurai","Othakadai","Melur Main Road, Othakadai, Madurai",9.956,78.161,6,0.15,2100000,350000,"23 ft","South","Residential",true,"DTCP approved","Available","Survey 122/3C",true,true,false,"Meenakshi Selvam","Owner","+91 97888 22019","Well-proportioned plot with a clear approach from the main road and a growing residential community.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("Near bus route","DTCP approved","Rectangular site"),9.956,78.161),
      plot("TN-TR-411","Quiet neighbourhood plot in Srirangam","Residential","Tiruchirappalli","Tiruchirappalli","Srirangam","Amma Mandapam Road, Srirangam, Tiruchirappalli",10.858,78.692,5,0.125,3250000,650000,"24 ft","West","Residential",true,"DTCP approved","Available","Survey 46/7A",true,true,true,"S. Balasubramanian","Agent","+91 99433 60217","A compact, fenced plot in a settled neighbourhood with everyday shopping and transit within easy reach.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("Fenced","Water connection","Near transit"),10.858,78.692),
      plot("TN-SA-507","Wide frontage plot at Omalur","Residential","Salem","Salem","Omalur","Tharamangalam Road, Omalur, Salem",11.741,78.045,12,0.30,2700000,225000,"40 ft","North-East","Residential",true,"DTCP approved","Available","Survey 208/2A",true,false,false,"Nandhini Kumar","Owner","+91 93605 18872","Generous frontage and room for a family home in a fast-growing Salem district corridor.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("40 ft frontage","Easy highway access","Level ground"),11.741,78.045),
      plot("TN-TH-604","Fertile farm parcel near Kumbakonam","Agricultural","Kumbakonam","Thanjavur","Swamimalai","Swamimalai Main Road, Kumbakonam, Thanjavur",10.955,79.330,200,5.0,8500000,42500,"20 ft","East","Agricultural",false,"Agricultural land","Available","Survey 61/1B",true,true,false,"R. Gopalakrishnan","Owner","+91 94435 77824","A fertile agricultural parcel in the Cauvery delta region with access to an irrigation channel. Verify water rights and records.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("Cauvery delta","Irrigation channel","Farm access road"),10.955,79.330),
      plot("TN-ER-710","Roadside commercial land at Perundurai","Commercial","Erode","Erode","Perundurai","Perundurai–Vijayamangalam Road, Erode",11.275,77.587,24,0.60,7800000,325000,"60 ft","South-East","Commercial",true,"DTCP approved","Available","Survey 173/4",true,false,false,"Senthil Raj","Agent","+91 99524 83211","Commercially zoned parcel with broad road frontage in an established industrial and logistics corridor.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("60 ft road","Commercial zoning","Highway corridor"),11.275,77.587),
      plot("TN-CH-118","A compact site near Tambaram","Residential","Chennai","Chengalpattu","Tambaram East","Gandhi Road, Tambaram East, Chennai",12.924,80.135,4,0.10,3600000,900000,"24 ft","North","Residential",true,"CMDA approved","Available","Survey 32/5A",true,true,true,"Priya Narayanan","Owner","+91 97910 34582","A neat small plot close to local shops and suburban rail connections. Ideal for a compact home.","https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=85",List.of("Near suburban rail","CMDA approved","Fenced"),12.924,80.135)
    );
    private Plot plot(String id,String title,String type,String city,String district,String locality,String address,double lat,double lng,int cents,double acres,long price,long ppc,String road,String facing,String zoning,boolean approved,String approval,String patta,String survey,boolean electricity,boolean water,boolean fenced,String owner,String role,String phone,String desc,String image,List<String> highlights,double blat,double blng) {
        double d=0.0010;
        return new Plot(id,title,type,city,district,locality,address,lat,lng,cents,acres,price,ppc,road,facing,zoning,approved,approval,patta,survey,electricity,water,fenced,owner,role,phone,desc,image,highlights,List.of(new Plot.Point(blat-d,blng-d),new Plot.Point(blat-d,blng+d),new Plot.Point(blat+d,blng+d),new Plot.Point(blat+d,blng-d)));
    }
    public PlotService(JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=jdbc; this.mapper=mapper;
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM plots",Integer.class);
        if(count==null||count==0) plots.forEach(p->insertPlot(p,null));
    }
    public List<Plot> search(String city,String type,Long minPrice,Long maxPrice,Double minAcres,Double maxAcres,Boolean approved,String q) {
        Stream<Plot> s=allPlots().stream();
        if(city!=null&&!city.isBlank()) s=s.filter(p->p.city().equalsIgnoreCase(city)||p.district().equalsIgnoreCase(city));
        if(type!=null&&!type.isBlank()&&!type.equalsIgnoreCase("All land")) s=s.filter(p->p.type().equalsIgnoreCase(type));
        if(minPrice!=null) s=s.filter(p->p.price()>=minPrice); if(maxPrice!=null) s=s.filter(p->p.price()<=maxPrice);
        if(minAcres!=null) s=s.filter(p->p.acres()>=minAcres); if(maxAcres!=null) s=s.filter(p->p.acres()<=maxAcres);
        if(approved!=null) s=s.filter(p->p.approved()==approved);
        if(q!=null&&!q.isBlank()) { String term=q.toLowerCase(); s=s.filter(p->(p.title()+p.city()+p.locality()+p.district()+p.type()).toLowerCase().contains(term)); }
        return s.toList();
    }
    public Optional<Plot> find(String id) {
        List<Plot> rows=jdbc.query("SELECT payload FROM plots WHERE id=? AND active=TRUE AND review_status='APPROVED'",(rs,n)->readPlot(rs.getString("payload")),id);
        return rows.stream().findFirst();
    }
    public boolean idExists(String id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM plots WHERE id=?)",Boolean.class,id)); }
    public boolean isReserved(String id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prebookings WHERE plot_id=? AND status='APPROVED' AND expires_at>CURRENT_TIMESTAMP)",Boolean.class,id)); }
    public List<Plot> allPlots() { return jdbc.query("SELECT payload FROM plots WHERE active=TRUE AND review_status='APPROVED' ORDER BY id",(rs,n)->readPlot(rs.getString("payload"))); }
    public List<Plot> ownerPlots(String email) { return jdbc.query("SELECT payload FROM plots WHERE owner_email=? AND active=TRUE ORDER BY id",(rs,n)->readPlot(rs.getString("payload")),email); }
    public boolean ownsPlot(String id,String email) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM plots WHERE id=? AND owner_email=? AND active=TRUE)",Boolean.class,id,email)); }
    public Plot insertPlot(Plot p,String ownerEmail) {
        p=p.withVerification(ownerEmail==null?"DEMO":"SELF_DECLARED");
        try { jdbc.update("INSERT INTO plots(id,city,district,locality,land_type,price,acres,approved,owner_email,payload,review_status) VALUES(?,?,?,?,?,?,?,?,?,?,?)",p.id(),p.city(),p.district(),p.locality(),p.type(),p.price(),p.acres(),p.approved(),ownerEmail,mapper.writeValueAsString(p),ownerEmail==null?"APPROVED":"PENDING"); }
        catch(JsonProcessingException e){throw new IllegalStateException("Could not store plot details",e);}
        return p;
    }
    public boolean updatePlot(Plot p,String ownerEmail) {
        p=p.withVerification("SELF_DECLARED");
        try {
            return jdbc.update("UPDATE plots SET city=?,district=?,locality=?,land_type=?,price=?,acres=?,approved=?,payload=?,review_status='PENDING' WHERE id=? AND owner_email=? AND active=TRUE",
                    p.city(),p.district(),p.locality(),p.type(),p.price(),p.acres(),p.approved(),mapper.writeValueAsString(p),p.id(),ownerEmail)>0;
        } catch(JsonProcessingException e){throw new IllegalStateException("Could not update plot details",e);}
    }
    public boolean deletePlot(String id,String ownerEmail) { return jdbc.update("UPDATE plots SET active=FALSE WHERE id=? AND owner_email=? AND active=TRUE",id,ownerEmail)>0; }

    public List<ListingReview> pendingReviews() {
        return jdbc.query("SELECT p.payload,p.owner_email,p.review_status,p.created_at,u.full_name FROM plots p LEFT JOIN users u ON u.email=p.owner_email WHERE p.active=TRUE AND p.review_status='PENDING' ORDER BY p.created_at",
                (rs,n)->new ListingReview(readPlot(rs.getString("payload")),rs.getString("owner_email"),rs.getString("full_name"),rs.getString("review_status"),rs.getTimestamp("created_at").toLocalDateTime()));
    }
    public List<ListingReview> ownerListingReviews(String email) {
        return jdbc.query("SELECT p.payload,p.owner_email,p.review_status,p.created_at,u.full_name FROM plots p LEFT JOIN users u ON u.email=p.owner_email WHERE p.active=TRUE AND p.owner_email=? ORDER BY p.created_at DESC",
                (rs,n)->new ListingReview(readPlot(rs.getString("payload")),rs.getString("owner_email"),rs.getString("full_name"),rs.getString("review_status"),rs.getTimestamp("created_at").toLocalDateTime()),email);
    }
    @org.springframework.transaction.annotation.Transactional
    public boolean review(String id,String status) {
        List<String> rows=jdbc.query("SELECT payload FROM plots WHERE id=? AND active=TRUE AND review_status='PENDING' FOR UPDATE",(rs,n)->rs.getString(1),id);
        if(rows.isEmpty()) return false;
        // Approval by an admin means the listing was reviewed, not that title, approvals or boundaries were certified.
        String payload=rows.get(0);
        if("APPROVED".equals(status)) {
            try { payload=mapper.writeValueAsString(readPlot(payload).withVerification("ADMIN_REVIEWED")); }
            catch(JsonProcessingException e) { throw new IllegalStateException("Could not store review result",e); }
        }
        return jdbc.update("UPDATE plots SET review_status=?,payload=? WHERE id=? AND review_status='PENDING'",status,payload,id)>0;
    }
    /** Name and phone from the signed-in account: the displayed listing contact must not be self-asserted. */
    public String[] accountContact(String email) {
        return jdbc.query("SELECT full_name,phone FROM users WHERE email=?",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},email).stream().findFirst().orElse(null);
    }
    public int photoCount(String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM plot_photos WHERE plot_id=?",Integer.class,id); }
    public List<String> photos(String id) { return jdbc.query("SELECT image_url FROM plot_photos WHERE plot_id=? ORDER BY id",(rs,n)->rs.getString(1),id); }
    public void addPhoto(String id,String url) { jdbc.update("INSERT INTO plot_photos(plot_id,image_url) VALUES(?,?)",id,url); }
    public OwnerProfile ownerProfile(String email) {
        var account=jdbc.query("SELECT email,full_name,phone FROM users WHERE email=? AND role='OWNER'",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)},email);
        List<Plot> listings=jdbc.query("SELECT payload FROM plots WHERE owner_email=? AND active=TRUE AND review_status='APPROVED' ORDER BY created_at DESC",(rs,n)->readPlot(rs.getString(1)),email);
        if(account.isEmpty()) {
            if(listings.isEmpty()) return null;
            Plot first=listings.get(0);return new OwnerProfile(email,first.owner(),first.phone(),listings.size(),listings);
        }
        String[] a=account.get(0);return new OwnerProfile(a[0],a[1],a[2],listings.size(),listings);
    }
    public Optional<String> ownerEmail(String plotId) { return jdbc.query("SELECT owner_email FROM plots WHERE id=? AND active=TRUE",(rs,n)->rs.getString(1),plotId).stream().filter(Objects::nonNull).findFirst(); }
    public boolean updateVisitStatus(long id,String ownerEmail,String status,String preferredDate) {
        String allowed=switch(status) {
            case "CONFIRMED" -> "e.status IN ('NEW','CHANGE_REQUESTED')";
            case "RESCHEDULED" -> "e.status IN ('NEW','CONFIRMED','RESCHEDULED','CHANGE_REQUESTED')";
            case "COMPLETED" -> "e.status='CONFIRMED'";
            case "CANCELLED" -> "e.status IN ('NEW','RESCHEDULED','CHANGE_REQUESTED','CONFIRMED')";
            default -> "1=0";
        };
        return jdbc.update("UPDATE enquiries e SET status=?,preferred_date=COALESCE(?,e.preferred_date) FROM plots p WHERE e.plot_id=p.id AND e.id=? AND e.request_type='SITE_VISIT' AND lower(trim(p.owner_email))=lower(trim(?)) AND "+allowed,status,preferredDate,id,ownerEmail)>0;
    }
    public boolean updateBuyerVisitStatus(long id,String buyerEmail,String status,String preferredDate) {
        String allowed=switch(status) {
            case "CONFIRMED","CHANGE_REQUESTED" -> "e.status='RESCHEDULED'";
            case "CANCELLED" -> "e.status IN ('NEW','RESCHEDULED','CHANGE_REQUESTED','CONFIRMED')";
            default -> "1=0";
        };
        return jdbc.update("UPDATE enquiries e SET status=?,preferred_date=COALESCE(?,e.preferred_date) WHERE e.id=? AND e.request_type='SITE_VISIT' AND (lower(e.buyer_email)=lower(?) OR (e.buyer_email IS NULL AND lower(e.email)=lower(?))) AND "+allowed,status,preferredDate,id,buyerEmail,buyerEmail)>0;
    }

    public EnquiryView save(Enquiry enquiry,String buyerEmail) {
        KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(connection->{
            // Ask PostgreSQL for only the identity column. RETURN_GENERATED_KEYS can
            // otherwise expose the full inserted row, which makes KeyHolder.getKey()
            // fail because it expects exactly one generated value.
            var ps=connection.prepareStatement("INSERT INTO enquiries(plot_id,buyer_email,name,phone,email,request_type,preferred_date,message) VALUES(?,?,?,?,?,?,?,?)",new String[]{"id"});
            ps.setString(1,enquiry.plotId()); ps.setString(2,buyerEmail); ps.setString(3,enquiry.name()); ps.setString(4,enquiry.phone());
            ps.setString(5,enquiry.email()); ps.setString(6,enquiry.requestType()); ps.setString(7,enquiry.preferredDate()); ps.setString(8,enquiry.message());
            return ps;
        },keys);
        Map<String,Object> generatedKeys=keys.getKeyList().stream().findFirst()
                .orElseThrow(()->new IllegalStateException("PostgreSQL did not return the enquiry ID"));
        Object generatedId=generatedKeys.get("id");
        if(!(generatedId instanceof Number)) generatedId=generatedKeys.values().stream().findFirst()
                .orElseThrow(()->new IllegalStateException("PostgreSQL returned no generated enquiry key"));
        if(!(generatedId instanceof Number)) throw new IllegalStateException("PostgreSQL returned a non-numeric enquiry ID");
        return findEnquiry(((Number)generatedId).longValue()).orElseThrow();
    }
    public Optional<EnquiryView> findEnquiry(long id) {
        return jdbc.query("SELECT e.id,e.plot_id,p.payload,p.city,p.locality,p.owner_email,e.buyer_email,e.name,e.phone,e.email,e.request_type,e.preferred_date,e.message,e.status,e.created_at FROM enquiries e JOIN plots p ON p.id=e.plot_id WHERE e.id=?",
                (rs,n)->new EnquiryView(rs.getLong("id"),rs.getString("plot_id"),readPlot(rs.getString("payload")).title(),rs.getString("city"),rs.getString("locality"),rs.getString("owner_email"),rs.getString("buyer_email"),rs.getString("name"),rs.getString("phone"),rs.getString("email"),rs.getString("request_type"),rs.getString("preferred_date"),rs.getString("message"),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime()),id).stream().findFirst();
    }
    public List<EnquiryView> enquiriesFor(String email,boolean owner) {
        String sql="SELECT e.id,e.plot_id,p.payload,p.city,p.locality,p.owner_email,e.buyer_email,e.name,e.phone,e.email,e.request_type,e.preferred_date,e.message,e.status,e.created_at FROM enquiries e JOIN plots p ON p.id=e.plot_id WHERE "+(owner?"p.owner_email=?":"(lower(e.buyer_email)=lower(?) OR (e.buyer_email IS NULL AND lower(e.email)=lower(?)))")+" ORDER BY e.created_at DESC";
        var mapper=(org.springframework.jdbc.core.RowMapper<EnquiryView>)(rs,n)->new EnquiryView(rs.getLong("id"),rs.getString("plot_id"),readPlot(rs.getString("payload")).title(),rs.getString("city"),rs.getString("locality"),rs.getString("owner_email"),rs.getString("buyer_email"),rs.getString("name"),rs.getString("phone"),rs.getString("email"),rs.getString("request_type"),rs.getString("preferred_date"),rs.getString("message"),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime());
        return owner?jdbc.query(sql,mapper,email):jdbc.query(sql,mapper,email,email);
    }
    public List<EnquiryView> allEnquiries() {
        String sql="SELECT e.id,e.plot_id,p.payload,p.city,p.locality,p.owner_email,e.buyer_email,e.name,e.phone,e.email,e.request_type,e.preferred_date,e.message,e.status,e.created_at FROM enquiries e JOIN plots p ON p.id=e.plot_id ORDER BY e.created_at DESC";
        return jdbc.query(sql,(rs,n)->new EnquiryView(rs.getLong("id"),rs.getString("plot_id"),readPlot(rs.getString("payload")).title(),rs.getString("city"),rs.getString("locality"),rs.getString("owner_email"),rs.getString("buyer_email"),rs.getString("name"),rs.getString("phone"),rs.getString("email"),rs.getString("request_type"),rs.getString("preferred_date"),rs.getString("message"),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime()));
    }
    private Plot readPlot(String json) {
        try{return mapper.readValue(json,Plot.class);}catch(Exception e){throw new IllegalStateException("Could not read plot details",e);}
    }
}
