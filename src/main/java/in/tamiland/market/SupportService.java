package in.tamiland.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SupportService {
    private static final Logger log=LoggerFactory.getLogger(SupportService.class);
    private final JdbcTemplate jdbc;
    private final JavaMailSender mailSender;
    @Value("${app.mail.admin:}") private String adminEmail;
    @Value("${spring.mail.username:}") private String smtpUsername;
    @Value("${spring.mail.password:}") private String smtpPassword;

    public SupportService(JdbcTemplate jdbc,JavaMailSender mailSender) { this.jdbc=jdbc;this.mailSender=mailSender; }
    public record Query(long id,String name,String email,String topic,String message,String status,LocalDateTime createdAt) {}

    public SupportController.QueryResponse submit(SupportController.QueryRequest request) {
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var statement=connection.prepareStatement("INSERT INTO support_queries(name,email,topic,message,email_status) VALUES(?,?,?,?,?)",new String[]{"id"});
            statement.setString(1,request.name().trim());statement.setString(2,request.email().trim().toLowerCase());
            statement.setString(3,request.topic().trim());statement.setString(4,request.message().trim());statement.setString(5,"PENDING");return statement;
        },key);
        Object generated=key.getKeyList().stream().findFirst().map(Map::values).flatMap(values->values.stream().findFirst())
                .orElseThrow(()->new IllegalStateException("Database did not return the support query ID"));
        long id=((Number)generated).longValue();
        String status=send(request,id);
        jdbc.update("UPDATE support_queries SET email_status=? WHERE id=?",status,id);
        return new SupportController.QueryResponse(id,status);
    }

    private String send(SupportController.QueryRequest request,long id) {
        if(!StringUtils.hasText(adminEmail)) return "NO_RECIPIENT";
        if(!StringUtils.hasText(smtpUsername)||!StringUtils.hasText(smtpPassword)) return "SMTP_NOT_CONFIGURED";
        try {
            SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(smtpUsername);mail.setTo(adminEmail);
            mail.setReplyTo(request.email().trim());mail.setSubject("MannVeedu support query: "+request.topic().trim());
            mail.setText("A user sent a query from MannVeedu.\n\nReference: #"+id+"\nName: "+request.name().trim()+
                    "\nEmail: "+request.email().trim()+"\nTopic: "+request.topic().trim()+"\n\nMessage:\n"+request.message().trim());
            mailSender.send(mail);return "SENT";
        } catch(MailException|IllegalArgumentException exception) {
            log.error("Could not send support query #{} to the admin mailbox",id,exception);return "FAILED";
        }
    }

    public List<Query> all() {
        return jdbc.query("SELECT id,name,email,topic,message,status,created_at FROM support_queries ORDER BY created_at DESC",
                (rs,n)->new Query(rs.getLong("id"),rs.getString("name"),rs.getString("email"),rs.getString("topic"),rs.getString("message"),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime()));
    }
    public boolean updateStatus(long id,String status) { return jdbc.update("UPDATE support_queries SET status=? WHERE id=?",status,id)>0; }
}
