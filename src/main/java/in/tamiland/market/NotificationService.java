package in.tamiland.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationService {
    private static final Logger log=LoggerFactory.getLogger(NotificationService.class);
    private final JavaMailSender mailSender;
    private final JdbcTemplate jdbc;
    @Value("${app.mail.admin:}") private String adminEmail;
    @Value("${spring.mail.username:}") private String smtpUsername;
    @Value("${spring.mail.password:}") private String smtpPassword;

    public NotificationService(JavaMailSender mailSender,JdbcTemplate jdbc){this.mailSender=mailSender;this.jdbc=jdbc;}

    public record Result(String adminEmailStatus,String ownerEmailStatus) {}

    public Result notifyEnquiry(EnquiryView enquiry) {
        String adminStatus=send(enquiry,adminEmail,"ADMIN");
        String ownerStatus=StringUtils.hasText(enquiry.ownerEmail())?send(enquiry,enquiry.ownerEmail(),"OWNER"):"NO_OWNER_EMAIL";
        return new Result(adminStatus,ownerStatus);
    }

    public String notifyVisitStatus(EnquiryView enquiry) {
        String recipient=StringUtils.hasText(enquiry.buyerEmail())?enquiry.buyerEmail():enquiry.email();
        if(!StringUtils.hasText(recipient)) return "NO_BUYER_EMAIL";
        String state=switch(enquiry.status()) {
            case "CONFIRMED" -> "confirmed";case "RESCHEDULED" -> "proposed a new date for";
            case "CHANGE_REQUESTED" -> "asked to change the date for";case "COMPLETED" -> "marked complete";
            case "CANCELLED" -> "cancelled";default -> "updated";
        };
        return sendVisitUpdate(enquiry,recipient,"BUYER","Your site visit has been "+state);
    }

    public void notifyPrebookingOwner(PrebookingView booking) {
        boolean cancelled="CANCELLED".equals(booking.status());
        String text=cancelled?"The buyer cancelled their pre-booking request for "+booking.plotTitle()+" in "+booking.locality()+", "+booking.city()+". The land is available again."
                :"A buyer requested a pre-booking for "+booking.plotTitle()+" in "+booking.locality()+", "+booking.city()+".\n\nBuyer: "+booking.buyerName()+"\nPhone: "+booking.phone()+"\nEmail: "+booking.buyerEmail()+"\nMessage: "+(StringUtils.hasText(booking.message())?booking.message():"None")+"\n\nReview the request in your MannVeedu owner dashboard.";
        sendPrebookingMail(booking.ownerEmail(),(cancelled?"Pre-booking cancelled: ":"New pre-booking request: ")+booking.plotTitle(),text);
    }

    public void notifyPrebookingBuyer(PrebookingView booking) {
        String status=switch(booking.status()) { case "APPROVED" -> "approved; the land is now held for you";case "DECLINED" -> "not approved by the owner";case "CANCELLED" -> "cancelled and the land is available again";default -> booking.status().toLowerCase(); };
        String text="Hello "+booking.buyerName()+",\n\nYour pre-booking request for "+booking.plotTitle()+" in "+booking.locality()+", "+booking.city()+" was "+status+".\n\nView the latest details in your MannVeedu account.";
        sendPrebookingMail(booking.buyerEmail(),"Pre-booking update: "+booking.plotTitle(),text);
    }

    private void sendPrebookingMail(String recipient,String subject,String body) {
        if(!StringUtils.hasText(recipient)||!StringUtils.hasText(smtpUsername)||!StringUtils.hasText(smtpPassword)) return;
        try { SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(smtpUsername);mail.setTo(recipient);mail.setSubject(subject);mail.setText(body);mailSender.send(mail); }
        catch(MailException|IllegalArgumentException exception) { log.warn("Could not send pre-booking notification",exception); }
    }

    public String notifyOwnerVisitResponse(EnquiryView enquiry) {
        if(!StringUtils.hasText(enquiry.ownerEmail())) return "NO_OWNER_EMAIL";
        String state=switch(enquiry.status()) {
            case "CONFIRMED" -> "accepted the proposed date";case "CHANGE_REQUESTED" -> "suggested a different date";
            case "CANCELLED" -> "cancelled the visit";default -> "responded to the visit request";
        };
        return sendVisitUpdate(enquiry,enquiry.ownerEmail(),"OWNER","The client has "+state);
    }

    private String sendVisitUpdate(EnquiryView enquiry,String recipient,String kind,String summary) {
        if(!StringUtils.hasText(smtpUsername)||!StringUtils.hasText(smtpPassword)) {
            record(enquiry.id(),recipient,kind,"SMTP_NOT_CONFIGURED",null);return "SMTP_NOT_CONFIGURED";
        }
        try {
            SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(smtpUsername);mail.setTo(recipient);
            if("BUYER".equals(kind)&&StringUtils.hasText(enquiry.ownerEmail())) mail.setReplyTo(enquiry.ownerEmail());
            mail.setSubject("Site visit update: "+enquiry.plotTitle());
            mail.setText("Hello "+("BUYER".equals(kind)?enquiry.name():"Land owner")+",\n\n"+summary+" for "+enquiry.plotTitle()+" in "+enquiry.locality()+", "+enquiry.city()+".\n"+
                    "Visit date: "+(StringUtils.hasText(enquiry.preferredDate())?enquiry.preferredDate():"To be arranged")+"\n\n"+
                    "You can view the latest details in your MannVeedu account.\nRequest reference: #"+enquiry.id());
            mailSender.send(mail);record(enquiry.id(),recipient,kind,"SENT",null);return "SENT";
        } catch(MailException|IllegalArgumentException exception) {
            record(enquiry.id(),recipient,kind,"FAILED",exception.getMessage());
            log.error("Could not send site visit update for enquiry #{}",enquiry.id(),exception);return "FAILED";
        }
    }

    private String send(EnquiryView e,String recipient,String kind) {
        if(!StringUtils.hasText(recipient)) { log.warn("{} enquiry notification skipped: recipient is not configured",kind); return "NO_RECIPIENT"; }
        if(!StringUtils.hasText(smtpUsername)||!StringUtils.hasText(smtpPassword)) {
            record(e.id(),recipient,kind,"SMTP_NOT_CONFIGURED",null);
            log.warn("{} enquiry notification saved but not mailed: SMTP credentials are not configured",kind);
            return "SMTP_NOT_CONFIGURED";
        }
        String subject=("SITE_VISIT".equals(e.requestType())?"Site visit request: ":"Property enquiry: ")+e.plotTitle();
        String body="A new "+("SITE_VISIT".equals(e.requestType())?"site visit request":"property enquiry")+" was submitted.\n\n"+
                "Plot: "+e.plotTitle()+"\nLocation: "+e.locality()+", "+e.city()+"\n"+
                "Price/listing ID: "+e.plotId()+"\n"+
                "Name: "+e.name()+"\nPhone: "+e.phone()+"\nEmail: "+(e.email()==null?"Not provided":e.email())+"\n"+
                "Preferred date: "+(e.preferredDate()==null?"Not specified":e.preferredDate())+"\n"+
                "Message: "+(e.message()==null?"No message":e.message())+"\n\n"+
                "Request reference: #"+e.id();
        try {
            SimpleMailMessage mail=new SimpleMailMessage();
            mail.setFrom(smtpUsername); mail.setTo(recipient); mail.setSubject(subject); mail.setText(body);
            mailSender.send(mail);
            record(e.id(),recipient,kind,"SENT",null);
            return "SENT";
        } catch(MailException|IllegalArgumentException ex) {
            record(e.id(),recipient,kind,"FAILED",ex.getMessage());
            log.error("{} email delivery failed for enquiry #{}",kind,e.id(),ex);
            return "FAILED";
        }
    }

    private void record(long enquiryId,String recipient,String kind,String status,String error) {
        String safeError=error==null?null:error.substring(0,Math.min(500,error.length()));
        jdbc.update("INSERT INTO notification_log(enquiry_id,recipient,recipient_kind,status,error_message) VALUES(?,?,?,?,?)",enquiryId,recipient,kind,status,safeError);
    }
}
