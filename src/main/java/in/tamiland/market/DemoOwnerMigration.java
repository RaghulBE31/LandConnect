package in.tamiland.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
public class DemoOwnerMigration implements CommandLineRunner {
    private static final String KEY="2026-10-demo-owner-kumar-v2";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final String email,name,phone;

    public DemoOwnerMigration(JdbcTemplate jdbc,ObjectMapper mapper,TransactionTemplate transactions,
            @Value("${app.demo-owner.email:}") String email,
            @Value("${app.demo-owner.name:Demo Owner}") String name,
            @Value("${app.demo-owner.phone:}") String phone) {
        this.jdbc=jdbc;this.mapper=mapper;this.transactions=transactions;this.email=email;this.name=name;this.phone=phone;
    }

    @Override public void run(String... args) {
        // Demo-owner reassignment is opt-in: without a configured demo owner email nothing is rewritten.
        if(email==null||email.isBlank()) return;
        Boolean applied=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app_migrations WHERE migration_key=?)",Boolean.class,KEY);
        if(Boolean.TRUE.equals(applied)) return;
        transactions.executeWithoutResult(status->{
            List<String[]> plots=jdbc.query("SELECT id,payload FROM plots",(rs,n)->new String[]{rs.getString("id"),rs.getString("payload")});
            for(String[] row:plots) {
                try {
                    ObjectNode payload=(ObjectNode)mapper.readTree(row[1]);
                    payload.put("owner",name);payload.put("ownerRole","Owner");payload.put("phone",phone);
                    jdbc.update("UPDATE plots SET owner_email=?,payload=? WHERE id=?",email,payload.toString(),row[0]);
                } catch(Exception e) { throw new IllegalStateException("Could not assign demo owner details for plot "+row[0],e); }
            }
            jdbc.update("INSERT INTO app_migrations(migration_key) VALUES(?)",KEY);
        });
    }
}
