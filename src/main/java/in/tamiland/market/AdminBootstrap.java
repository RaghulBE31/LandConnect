package in.tamiland.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Promotes an already-registered account to ADMIN on startup.
 * Set the PROMOTE_ADMIN_EMAIL environment variable (or app.admin.promote-email),
 * register that account in the app once, then restart. Remove the variable afterwards.
 * Does nothing when the variable is empty.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final String email;

    public AdminBootstrap(JdbcTemplate jdbc, @Value("${app.admin.promote-email:}") String email) {
        this.jdbc = jdbc;
        this.email = email == null ? "" : email.trim();
    }

    @Override
    public void run(String... args) {
        if (email.isEmpty()) return;
        int rows = jdbc.update("UPDATE users SET role='ADMIN' WHERE LOWER(email)=LOWER(?)", email);
        System.out.println(rows > 0
            ? "[AdminBootstrap] " + email + " is now ADMIN. Log out and back in."
            : "[AdminBootstrap] No account found for " + email + ". Register it first, then restart.");
    }
}