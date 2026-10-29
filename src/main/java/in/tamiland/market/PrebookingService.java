package in.tamiland.market;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Service
public class PrebookingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PrebookingService(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }

    public record Availability(String status) {}

    public Availability availability(String plotId) {
        Boolean exists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM plots WHERE id=? AND active=TRUE AND review_status='APPROVED')",Boolean.class,plotId);
        if(!Boolean.TRUE.equals(exists)) throw new IllegalArgumentException("Land listing not found");
        expirePlot(plotId);
        Boolean reserved=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prebookings WHERE plot_id=? AND status='APPROVED' AND expires_at>CURRENT_TIMESTAMP)",Boolean.class,plotId);
        return new Availability(Boolean.TRUE.equals(reserved)?"RESERVED":"AVAILABLE");
    }

    @Transactional
    public PrebookingView request(String plotId,String buyerEmail,String message) {
        List<String> owner=jdbc.query("SELECT owner_email FROM plots WHERE id=? AND active=TRUE AND review_status='APPROVED' FOR UPDATE",
                (rs,n)->rs.getString(1),plotId);
        if(owner.isEmpty()) throw new IllegalArgumentException("Land listing is not available");
        expirePlot(plotId);
        Boolean reserved=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prebookings WHERE plot_id=? AND status='APPROVED' AND expires_at>CURRENT_TIMESTAMP)",Boolean.class,plotId);
        if(Boolean.TRUE.equals(reserved)) throw new IllegalStateException("This land has already been pre-booked");
        try {
            jdbc.update("INSERT INTO prebookings(plot_id,buyer_email,buyer_name,phone,message) SELECT ?,email,full_name,phone,? FROM users WHERE email=? AND role='BUYER'",
                    plotId,message,buyerEmail);
        } catch(DuplicateKeyException e) {
            throw new IllegalStateException("You already have a pre-booking request for this land");
        }
        Long id=jdbc.queryForObject("SELECT currval(pg_get_serial_sequence('prebookings','id'))",Long.class);
        return find(id).orElseThrow(()->new IllegalStateException("Could not load the pre-booking request"));
    }

    @Transactional
    public PrebookingView ownerDecision(long id,String ownerEmail,String status) {
        List<String[]> target=jdbc.query("SELECT p.id,p.owner_email FROM prebookings b JOIN plots p ON p.id=b.plot_id WHERE b.id=? AND p.active=TRUE FOR UPDATE OF p",
                (rs,n)->new String[]{rs.getString(1),rs.getString(2)},id);
        if(target.isEmpty()||target.get(0)[1]==null||!target.get(0)[1].trim().equalsIgnoreCase(ownerEmail.trim()))
            throw new IllegalArgumentException("Pre-booking was not found for your listings");
        String plotId=target.get(0)[0];
        if("APPROVED".equals(status)) {
            expirePlot(plotId);
            Boolean reserved=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prebookings WHERE plot_id=? AND status='APPROVED' AND expires_at>CURRENT_TIMESTAMP AND id<>?)",Boolean.class,plotId,id);
            if(Boolean.TRUE.equals(reserved)) throw new IllegalStateException("Another buyer already has this land on hold");
            if(jdbc.update("UPDATE prebookings SET status='APPROVED',expires_at=CURRENT_TIMESTAMP + INTERVAL '48 hours',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",id)==0)
                throw new IllegalStateException("This request is no longer pending");
            jdbc.update("UPDATE prebookings SET status='DECLINED',updated_at=CURRENT_TIMESTAMP WHERE plot_id=? AND id<>? AND status='PENDING'",plotId,id);
        } else if("DECLINED".equals(status)) {
            if(jdbc.update("UPDATE prebookings SET status='DECLINED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",id)==0)
                throw new IllegalStateException("This request is no longer pending");
        } else if("CANCELLED".equals(status)) {
            if(jdbc.update("UPDATE prebookings SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('PENDING','APPROVED')",id)==0)
                throw new IllegalStateException("This reservation is no longer active");
        }
        return find(id).orElseThrow(()->new IllegalStateException("Could not load the pre-booking"));
    }

    @Transactional
    public PrebookingView buyerCancel(long id,String buyerEmail) {
        List<String> plot=jdbc.query("SELECT plot_id FROM prebookings WHERE id=? AND buyer_email=? FOR UPDATE",(rs,n)->rs.getString(1),id,buyerEmail);
        if(plot.isEmpty()) throw new IllegalArgumentException("Pre-booking was not found in your account");
        if(jdbc.update("UPDATE prebookings SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND buyer_email=? AND status IN ('PENDING','APPROVED')",id,buyerEmail)==0)
            throw new IllegalStateException("This pre-booking is no longer active");
        return find(id).orElseThrow(()->new IllegalStateException("Could not load the pre-booking"));
    }

    public List<PrebookingView> forBuyer(String email) {
        expireHolds();
        return jdbc.query(select()+" WHERE b.buyer_email=? ORDER BY b.created_at DESC",this::map,email);
    }

    public List<PrebookingView> forOwner(String email) {
        expireHolds();
        return jdbc.query(select()+" WHERE p.owner_email=? ORDER BY b.created_at DESC",this::map,email);
    }

    public Optional<PrebookingView> find(long id) {
        return jdbc.query(select()+" WHERE b.id=?",this::map,id).stream().findFirst();
    }

    private String select() {
        return "SELECT b.id,b.plot_id,p.payload,p.city,p.locality,p.owner_email,b.buyer_email,b.buyer_name,b.phone,b.message,b.status,b.created_at,b.expires_at " +
                "FROM prebookings b JOIN plots p ON p.id=b.plot_id ";
    }

    private PrebookingView map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        Plot plot;
        try { plot=mapper.readValue(rs.getString("payload"),Plot.class); }
        catch(Exception e) { throw new java.sql.SQLException("Could not read land listing",e); }
        java.sql.Timestamp expiry=rs.getTimestamp("expires_at");
        return new PrebookingView(rs.getLong("id"),rs.getString("plot_id"),plot.title(),rs.getString("city"),rs.getString("locality"),
                rs.getString("owner_email"),rs.getString("buyer_email"),rs.getString("buyer_name"),rs.getString("phone"),rs.getString("message"),
                rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime(),expiry==null?null:expiry.toLocalDateTime());
    }

    @Scheduled(fixedDelay=60_000)
    public void expireHolds() {
        jdbc.update("UPDATE prebookings SET status='EXPIRED',updated_at=CURRENT_TIMESTAMP WHERE status='APPROVED' AND expires_at<=CURRENT_TIMESTAMP");
    }

    private void expirePlot(String plotId) {
        jdbc.update("UPDATE prebookings SET status='EXPIRED',updated_at=CURRENT_TIMESTAMP WHERE plot_id=? AND status='APPROVED' AND expires_at<=CURRENT_TIMESTAMP",plotId);
    }
}
